// Launcher mode: `BeyondAndTabs-Setup.exe --launch`, or the same exe copied to %LOCALAPPDATA%\BeyondAndTabs\launcher.exe.
//
// It serves one page from 127.0.0.1 (random free port, random token in the path) and opens it as an Edge app window
// (1200x640). The page shows your build (from the game jar's name), what is new on main since you last played and the
// open pull requests ("Coming soon"). PLAY runs the packwiz update itself (the instance's pre-launch command), checks
// that the game jar is the newest tester pack, remembers that pack and starts Prism Launcher on the instance.
//
// GitHub: public API, no login, at most ghCallBudget calls per launch. The server stops when the page stops sending its
// heartbeat, when PLAY is pressed, or after maxLifetime.
package main

import (
	"crypto/rand"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	repoSlug        = "IamTheLovish/Beyond-and-Tabs"
	packRepoSlug    = "IamTheLovish/beyond-and-tabs-pack" // the tester pack: CI publishes "Tester pack <date>-<sha>" on its main
	ghAPI           = "https://api.github.com"
	ghCallBudget    = 5 // main, the two channels' packs, the news, the open PRs
	launcherExe     = "launcher.exe"
	stateFileName   = "launcher-state.json"
	troubleshootURL = "https://github.com/" + repoSlug + "/blob/main/pack/INSTALL-for-testers.txt"
	reportBugURL    = "https://github.com/" + repoSlug + "/issues/new"
	maxLifetime     = 30 * time.Minute
	firstPingWait   = 3 * time.Minute  // the window may take a while to open the first time
	pingTimeout     = 20 * time.Second // the page pings every 5 s
	userAgent       = "BeyondAndTabs-Launcher/1.0 (github.com/" + repoSlug + ")"
)

//go:embed launcher.html
var launcherHTML string

// the launcher's full-bleed background: a clean in-game shot with no HUD
//
//go:embed brand/launcher-bg.jpg
var launcherBg []byte

// ---------- mode ----------

// isLaunchMode: --launch on the command line, or the exe is the installed launcher.exe.
func isLaunchMode(args []string, exePath string) bool {
	for _, a := range args {
		if a == "--launch" {
			return true
		}
	}
	base := exePath
	if i := strings.LastIndexAny(base, `\/`); i >= 0 {
		base = base[i+1:]
	}
	return strings.EqualFold(base, launcherExe)
}

func hasFlag(args []string, flag string) bool {
	for _, a := range args {
		if a == flag {
			return true
		}
	}
	return false
}

// ---------- paths ----------

type gamePaths struct {
	root, prismDir, prismExe, mcDir, launcher, state string
}

func newGamePaths(localAppData string) gamePaths {
	root := filepath.Join(localAppData, "BeyondAndTabs")
	prismDir := filepath.Join(root, "PrismLauncher")
	return gamePaths{
		root:     root,
		prismDir: prismDir,
		prismExe: filepath.Join(prismDir, "prismlauncher.exe"),
		mcDir:    filepath.Join(prismDir, "instances", instanceName, "minecraft"),
		launcher: filepath.Join(root, launcherExe),
		state:    filepath.Join(root, stateFileName),
	}
}

// installLauncher copies the running exe to launcher.exe (via a temp file, so a failed copy never leaves half an exe).
func installLauncher(dest string) error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	if strings.EqualFold(filepath.Clean(self), filepath.Clean(dest)) {
		return nil
	}
	tmp := dest + ".new"
	if err := copyFile(self, tmp); err != nil {
		os.Remove(tmp)
		return err
	}
	os.Remove(dest)
	if err := os.Rename(tmp, dest); err != nil {
		os.Remove(tmp)
		return fmt.Errorf("%w (is the launcher still open?)", err)
	}
	return nil
}

// ---------- your build: the game jar's name ----------

// beyondandtabs-2026.10.25-2235-f5775f8.jar: build date-time (UTC) and the commit sha it was built from.
var jarRe = regexp.MustCompile(`(?i)^beyondandtabs-(\d{4}\.\d{2}\.\d{2}-\d{4})(?:-([0-9a-f]{7,40}))?\.jar$`)

type buildInfo struct {
	Sha  string    `json:"sha"`
	Time time.Time `json:"date"`
	File string    `json:"file"`
	Jars int       `json:"jars"` // game jars found; more than one stops Forge from starting
}

func parseJarName(name string) (buildInfo, bool) {
	m := jarRe.FindStringSubmatch(name)
	if m == nil {
		return buildInfo{}, false
	}
	t, err := time.ParseInLocation("2006.01.02-1504", m[1], time.UTC)
	if err != nil {
		return buildInfo{}, false
	}
	return buildInfo{Sha: strings.ToLower(m[2]), Time: t, File: name, Jars: 1}, true
}

// pickBuild returns the newest game jar among file names (other mods are ignored) and how many game jars there are.
func pickBuild(names []string) (buildInfo, bool) {
	var best buildInfo
	n := 0
	for _, name := range names {
		b, ok := parseJarName(name)
		if !ok {
			continue
		}
		n++
		if n == 1 || b.Time.After(best.Time) {
			best = b
		}
	}
	best.Jars = n
	return best, n > 0
}

func readBuild(modsDir string) (buildInfo, bool) {
	entries, err := os.ReadDir(modsDir)
	if err != nil {
		return buildInfo{}, false
	}
	var names []string
	for _, e := range entries {
		if !e.IsDir() {
			names = append(names, e.Name())
		}
	}
	return pickBuild(names)
}

// ---------- status line ----------

func short(sha string) string {
	sha = strings.ToLower(strings.TrimSpace(sha))
	if len(sha) > 7 {
		return sha[:7]
	}
	return sha
}

// packHead is the newest tester pack: the pack repo's main commit "Tester pack <yyyy.mm.dd-hhmm>-<sha>" (CI publishes it
// from main's builds, so main can be ahead of it for ~25 min).
type packHead struct {
	Sha  string
	Date time.Time // UTC
}

// "Tester pack" on branch main (tested), "Fast pack" on branch fast (untested, channels.go)
var packRe = regexp.MustCompile(`^(?:Tester|Fast) pack (\d{4}\.\d{2}\.\d{2}-\d{4})-([0-9a-f]{7,40})$`)

func parsePackCommit(msg string) (packHead, error) {
	first := strings.TrimSpace(strings.SplitN(strings.ReplaceAll(msg, "\r\n", "\n"), "\n", 2)[0])
	m := packRe.FindStringSubmatch(first)
	if m == nil {
		return packHead{}, fmt.Errorf("the newest pack commit is not a tester pack: %q", first)
	}
	t, err := time.ParseInLocation("2006.01.02-1504", m[1], time.UTC)
	if err != nil {
		return packHead{}, fmt.Errorf("the newest pack commit has a bad date: %q", first)
	}
	return packHead{Sha: strings.ToLower(m[2]), Date: t}, nil
}

// statusLine: level is "ok" (green), "update" (gold) or "warn" (amber). The game jar is compared with the newest pack,
// not with main.
func statusLine(jarSha, mainSha string, pack packHead, err error) (text, level string) {
	return statusLineAhead(jarSha, mainSha, pack, err, " · main has newer merges still being packed (usually ~25 min)")
}

// statusLineAhead: statusLine with the tail used when main is ahead of the pack (channels.go passes the channel's own).
func statusLineAhead(jarSha, mainSha string, pack packHead, err error, aheadText string) (text, level string) {
	switch {
	case err != nil:
		return "Couldn't check GitHub (" + shortErr(err) + ")", "warn"
	case pack.Sha == "":
		return "Couldn't read the newest tester pack from GitHub", "warn"
	}
	ahead := ""
	if short(mainSha) != short(pack.Sha) {
		ahead = aheadText
	}
	switch {
	case jarSha == "":
		return "Game files not found yet: PLAY installs the newest pack (" + short(pack.Sha) + ")" + ahead, "update"
	case short(jarSha) == short(pack.Sha):
		return "Up to date: pack " + short(pack.Sha) + " (" + pack.Date.Format("2006-01-02 15:04 UTC") + ")" + ahead, "ok"
	default:
		return "Update ready: pack " + short(pack.Sha) + " - press PLAY to install it" + ahead, "update"
	}
}

// shortErr drops Go's "Get \"https://...\":" prefix so the status line stays readable.
func shortErr(err error) string {
	var ue *url.Error
	if errors.As(err, &ue) {
		err = ue.Err
	}
	s := err.Error()
	if len(s) > 90 {
		s = s[:90] + "..."
	}
	return s
}

// ---------- merge commits ----------

var mergeRe = regexp.MustCompile(`^Merge pull request #(\d+) from (\S+)`)

// parseMergeCommit: "Merge pull request #N from owner/branch\n\nTitle" -> (N, "Title"). The title is the first line of
// the body; a merge with no body falls back to the branch name.
func parseMergeCommit(msg string) (int, string, bool) {
	lines := strings.Split(strings.ReplaceAll(msg, "\r\n", "\n"), "\n")
	m := mergeRe.FindStringSubmatch(strings.TrimSpace(lines[0]))
	if m == nil {
		return 0, "", false
	}
	n, err := strconv.Atoi(m[1])
	if err != nil {
		return 0, "", false
	}
	title := ""
	for _, l := range lines[1:] {
		if t := strings.TrimSpace(l); t != "" {
			title = t
			break
		}
	}
	if title == "" {
		title = m[2]
		if i := strings.Index(title, "/"); i >= 0 {
			title = title[i+1:]
		}
	}
	return n, title, true
}

// ---------- GitHub ----------

type ghClient struct {
	base   string
	client *http.Client
	calls  int
	budget int // calls allowed; 0 = ghCallBudget (the page load). The update watcher has its own client and budget
}

func (g *ghClient) get(path string, v any) error {
	budget := g.budget
	if budget == 0 {
		budget = ghCallBudget
	}
	if g.calls >= budget {
		return fmt.Errorf("GitHub call budget (%d) used up", budget)
	}
	g.calls++
	req, err := http.NewRequest("GET", g.base+path, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", userAgent)
	req.Header.Set("Accept", "application/vnd.github+json")
	resp, err := g.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		if resp.StatusCode == 403 && resp.Header.Get("X-RateLimit-Remaining") == "0" {
			return errors.New("GitHub rate limit reached, try again in an hour")
		}
		return fmt.Errorf("HTTP %s", resp.Status)
	}
	return json.NewDecoder(resp.Body).Decode(v)
}

type prItem struct {
	N     int    `json:"n"`
	Title string `json:"title"`
	Tag   string `json:"tag,omitempty"` // Coming soon only: "draft" or "ready"
}

type ghResult struct {
	MainSha   string
	MainDate  time.Time
	News      []prItem
	NewsSince bool // true: News is "since you last played"; false: the last merged PRs (no state yet)
	NewsNote  string
	Soon      []prItem
	SoonNote  string
	Err       error // main's head could not be read: the status line says why
	Pack      packHead
	PackErr   error               // the newest pack could not be read: the status line says why
	Packs     map[string]packHead // every channel's newest pack (Pack = the tested one)
	PackErrs  map[string]error
	Calls     int
}

type ghCommit struct {
	Sha    string `json:"sha"`
	Commit struct {
		Message   string `json:"message"`
		Committer struct {
			Date time.Time `json:"date"`
		} `json:"committer"`
	} `json:"commit"`
}

type ghPull struct {
	Number   int        `json:"number"`
	Title    string     `json:"title"`
	Draft    bool       `json:"draft"`
	MergedAt *time.Time `json:"merged_at"`
}

// fetchGitHub makes at most 5 calls: main's head, the newest pack of each channel (tested, fast), then either
// compare(last...main) or the recent closed PRs, then the open PRs. Both packs are read up front, so switching the
// Version menu costs no call.
func fetchGitHub(g *ghClient, lastSha string) (r ghResult) {
	defer func() { r.Calls = g.calls }()
	repo := "/repos/" + repoSlug

	var head ghCommit
	if err := g.get(repo+"/commits/main", &head); err != nil {
		r.Err = err
		return r
	}
	r.MainSha, r.MainDate = head.Sha, head.Commit.Committer.Date

	r.Packs, r.PackErrs = map[string]packHead{}, map[string]error{}
	for _, c := range channels {
		p, err := fetchPackHead(g, c)
		r.Packs[c.ID], r.PackErrs[c.ID] = p, err
	}
	r.Pack, r.PackErr = r.Packs[channelTested], r.PackErrs[channelTested]

	if lastSha != "" {
		r.NewsSince = true
		if short(lastSha) == short(r.MainSha) {
			r.NewsNote = "Nothing new since you last played."
		} else {
			var cmp struct {
				Commits []ghCommit `json:"commits"`
			}
			if err := g.get(repo+"/compare/"+url.PathEscape(lastSha)+"..."+url.PathEscape(r.MainSha), &cmp); err != nil {
				r.NewsNote = "Couldn't compare with the build you last played (" + shortErr(err) + ")"
			} else {
				for i := len(cmp.Commits) - 1; i >= 0; i-- { // the API lists oldest first
					if n, title, ok := parseMergeCommit(cmp.Commits[i].Commit.Message); ok {
						r.News = append(r.News, prItem{N: n, Title: title})
					}
				}
				if len(r.News) == 0 {
					r.NewsNote = "No merged pull requests since you last played."
				}
			}
		}
	} else {
		var closed []ghPull
		if err := g.get(repo+"/pulls?state=closed&sort=updated&direction=desc&per_page=30", &closed); err != nil {
			r.NewsNote = "Couldn't read the recent changes (" + shortErr(err) + ")"
		} else {
			var merged []ghPull
			for _, p := range closed {
				if p.MergedAt != nil {
					merged = append(merged, p)
				}
			}
			sort.SliceStable(merged, func(i, j int) bool { return merged[i].MergedAt.After(*merged[j].MergedAt) })
			for i, p := range merged {
				if i == 8 {
					break
				}
				r.News = append(r.News, prItem{N: p.Number, Title: p.Title})
			}
		}
	}

	var open []ghPull
	if err := g.get(repo+"/pulls?state=open&per_page=100", &open); err != nil {
		r.SoonNote = "Couldn't read the open pull requests (" + shortErr(err) + ")"
	} else {
		for _, p := range open {
			tag := "ready"
			if p.Draft {
				tag = "draft"
			}
			r.Soon = append(r.Soon, prItem{N: p.Number, Title: p.Title, Tag: tag})
		}
		if len(r.Soon) == 0 {
			r.SoonNote = "No open pull requests right now."
		}
	}
	return r
}

// ---------- state file ----------

type launcherState struct {
	LastPlayedSha string    `json:"lastPlayedSha"`
	LastPlayedAt  time.Time `json:"lastPlayedAt"`
	Channel       string    `json:"channel,omitempty"` // the Version menu's choice (channels.go); "" = tested
}

func readState(path string) launcherState {
	var s launcherState
	if data, err := os.ReadFile(path); err == nil {
		json.Unmarshal(data, &s)
	}
	return s
}

func writeState(path string, s launcherState) error {
	data, _ := json.MarshalIndent(s, "", "  ")
	return os.WriteFile(path, data, 0o644)
}

// ---------- the page's data ----------

type pageState struct {
	Build      *buildInfo `json:"build"`
	MainSha    string     `json:"mainSha"`
	MainDate   *time.Time `json:"mainDate,omitempty"`
	Status     string     `json:"status"`
	Level      string     `json:"level"`
	PackSha    string     `json:"packSha"` // the newest tester pack ("" when GitHub could not say)
	Version    string     `json:"version"`
	NewsTitle  string     `json:"newsTitle"`
	NewsSince  *time.Time `json:"newsSince,omitempty"`
	News       []prItem   `json:"news"`
	NewsNote   string     `json:"newsNote,omitempty"`
	NewCount   int        `json:"newCount"` // -1: no state yet
	Soon       []prItem   `json:"soon"`
	SoonNote   string     `json:"soonNote,omitempty"`
	WindowNote string     `json:"windowNote,omitempty"`
	GHCalls    int        `json:"githubCalls"`
	// release channels (channels.go): the chosen one, the menu, and what is on its way
	Channel  string          `json:"channel"`
	Channels []channelOption `json:"channels"`
	WaitMins *int            `json:"waitMins,omitempty"` // set while an update is coming: minutes left (0 = any minute)
	Note     string          `json:"note,omitempty"`     // what the watcher did (installed a pack, waits for the game to close)
}

// liveState is what the page's status is computed from on every request (the ETA counts down, the watcher updates it).
// Guarded by launcher.mu.
type liveState struct {
	mainErr  error
	packs    map[string]packHead
	packErrs map[string]error
	wait     *pendingUpdate
}

func buildPageState(p gamePaths, g *ghClient) (pageState, liveState) {
	st := readState(p.state)
	ch := selectedChannel(st.Channel)
	r := fetchGitHub(g, st.LastPlayedSha)

	ps := pageState{Version: ch.Label, MainSha: r.MainSha, PackSha: r.Packs[ch.ID].Sha, News: r.News, NewsNote: r.NewsNote, Soon: r.Soon,
		SoonNote: r.SoonNote, GHCalls: r.Calls, NewCount: -1, NewsTitle: "Recently merged", Channel: ch.ID, Channels: channelOptions()}
	if !r.MainDate.IsZero() {
		ps.MainDate = &r.MainDate
	}
	live := liveState{mainErr: r.Err, packs: r.Packs, packErrs: r.PackErrs}
	ps = withStatus(ps, p, live, time.Now())
	if r.NewsSince {
		ps.NewsTitle = "New since you last played"
		ps.NewCount = len(r.News)
		if !st.LastPlayedAt.IsZero() {
			ps.NewsSince = &st.LastPlayedAt
		}
	}
	if r.Err != nil {
		ps.NewsNote = "GitHub is unreachable, so the news can't be shown. PLAY still works."
	}
	return ps, live
}

// withStatus fills the build, the status line and the wait for the chosen channel. The game jar is read from disk each
// time (the watcher may have installed a pack since), and the ETA counts down with now.
func withStatus(ps pageState, p gamePaths, live liveState, now time.Time) pageState {
	ch := selectedChannel(ps.Channel)
	b, haveBuild := readBuild(filepath.Join(p.mcDir, "mods"))
	ps.Build = nil
	if haveBuild {
		ps.Build = &b
	}
	ps.Version = ch.Label
	pack := live.packs[ch.ID]
	checkErr := live.mainErr
	if checkErr == nil {
		checkErr = live.packErrs[ch.ID]
		if ch.ID == channelFast && checkErr != nil && strings.Contains(checkErr.Error(), "404") {
			checkErr = nil // no branch fast yet: channelStatus says no untested pack is published
		}
	}
	ps.Status, ps.Level = channelStatus(ch, b.Sha, ps.MainSha, pack, checkErr, live.wait, now)
	if haveBuild && b.Sha == "" && checkErr == nil {
		ps.Status, ps.Level = ch.Label+" · Your game jar has no commit in its name ("+b.File+"): can't compare with the newest pack ("+short(pack.Sha)+")", "update"
	}
	if haveBuild && b.Jars > 1 {
		ps.Status += fmt.Sprintf(" - warning: %d copies of the game jar in mods, Forge needs one", b.Jars)
		ps.Level = "warn"
	}
	ps.WaitMins = nil
	if w := live.wait; w != nil && w.NotComing == "" && (pack.Sha == "" || short(w.Sha) != short(pack.Sha)) {
		m := etaMinutes(w.ETA, now)
		ps.WaitMins = &m
	}
	return ps
}

// ---------- high-performance GPU ----------

// gpuPrefValue is Windows' "High performance" choice for one app (DirectX\UserGpuPreferences). 0 = let Windows decide,
// 1 = power saving.
const gpuPrefValue = "GpuPreference=2;"

// gpuPrefStore is the registry as decideGPUPref sees it: one value per exe path.
type gpuPrefStore interface {
	Get(exe string) (value string, found bool, err error)
	Set(exe, value string) error
}

// decideGPUPref writes gpuPrefValue for exe only when no choice exists yet. An existing value (the user's choice,
// including "Let Windows decide") is left alone. Read and write errors are returned, never swallowed.
func decideGPUPref(store gpuPrefStore, exe string) (wrote bool, err error) {
	_, found, err := store.Get(exe)
	if err != nil {
		return false, fmt.Errorf("reading the GPU setting: %w", err)
	}
	if found {
		return false, nil
	}
	if err := store.Set(exe, gpuPrefValue); err != nil {
		return false, fmt.Errorf("writing the GPU setting: %w", err)
	}
	return true, nil
}

// javaExe is the Java Prism runs the game with: JavaPath from the instance's instance.cfg or from prismlauncher.cfg when
// set, else Prism's bundled java-runtime-gamma. Prism writes JavaPath with forward slashes (C:/Users/...); it is returned
// with backslashes, because the GPU preference key must be the exact Windows path: the slash form never matched and
// the game stayed on the integrated GPU (Oct 26).
func javaExe(p gamePaths) string {
	inst := filepath.Join(p.prismDir, "instances", instanceName)
	for _, cfg := range []string{filepath.Join(inst, "instance.cfg"), filepath.Join(p.prismDir, "prismlauncher.cfg")} {
		if v := readCfgValue(cfg, "JavaPath"); v != "" {
			return strings.ReplaceAll(v, "/", `\`)
		}
	}
	return filepath.Join(p.prismDir, "java", "java-runtime-gamma", "bin", "javaw.exe")
}

// readCfgValue returns key's value in a Prism .cfg file (key=value lines); "" when the file or the key is missing.
func readCfgValue(path, key string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(data), "\n") {
		if k, v, ok := strings.Cut(strings.TrimSpace(line), "="); ok && strings.TrimSpace(k) == key {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

// ---------- window ----------

// findEdge: the standard install folders, then the App Paths registry key.
func findEdge() string {
	rel := filepath.Join("Microsoft", "Edge", "Application", "msedge.exe")
	for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"} {
		if d := os.Getenv(env); d != "" && exists(filepath.Join(d, rel)) {
			return filepath.Join(d, rel)
		}
	}
	for _, hive := range []string{"HKLM", "HKCU"} {
		cmd := exec.Command("reg", "query", hive+`\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\msedge.exe`, "/ve")
		hideWindow(cmd)
		out, err := cmd.Output()
		if err != nil {
			continue
		}
		if p := parseRegDefault(string(out)); p != "" && exists(p) {
			return p
		}
	}
	return ""
}

// parseRegDefault reads the value from `reg query ... /ve` output: "    (Default)    REG_SZ    C:\...\msedge.exe".
func parseRegDefault(out string) string {
	for _, line := range strings.Split(out, "\n") {
		if _, v, ok := strings.Cut(line, "REG_SZ"); ok {
			return strings.Trim(strings.TrimSpace(v), `"`)
		}
	}
	return ""
}

func openURL(u string) error {
	cmd := exec.Command("rundll32", "url.dll,FileProtocolHandler", u)
	hideWindow(cmd)
	return cmd.Start()
}

// Default window: the Beyond All Reason launcher size, 1190x610 LOGICAL px. Edge's --window-size is already in logical
// px (seen on a 150% display: 1190x610 gave a 1785x915-logical window when multiplied); the page scales its stage to fit.
const (
	winLogicalW = 1190
	winLogicalH = 610
)

func windowSize(dpi int) (w, h int) {
	return winLogicalW, winLogicalH // Edge applies the display scale itself; dpi is kept for the caller's log only
}

// edgeArgs: a private Edge profile, so --window-size applies even when the user's own Edge is already running
// (an --app window joining a running profile ignores it). Device scale is not forced.
func edgeArgs(u, localAppData string, dpi int) []string {
	w, h := windowSize(dpi)
	return []string{
		"--app=" + u,
		"--user-data-dir=" + filepath.Join(localAppData, "BeyondAndTabs", "launcher-app"),
		"--no-first-run",
		"--no-default-browser-check",
		// the private profile must never sign in to the user's Microsoft account or sync (a fresh profile did, 2026-10-26;
		// checked on screen). Do not add msEdgeOnRamp* here: with them Edge exits at once and no window opens.
		"--disable-sync",
		"--disable-features=msImplicitSignin",
		fmt.Sprintf("--window-size=%d,%d", w, h),
	}
}

// openWindow opens the page as an Edge app window, else in the default browser; the note says which.
func openWindow(u string) (note string, err error) {
	if edge := findEdge(); edge != "" {
		cmd := exec.Command(edge, edgeArgs(u, os.Getenv("LOCALAPPDATA"), displayDPI())...)
		if err := cmd.Start(); err == nil {
			return "", nil
		} else {
			note = "Edge failed to start (" + shortErr(err) + "): opened in your default browser instead."
		}
	} else {
		note = "Microsoft Edge not found: opened in your default browser instead."
	}
	if err := openURL(u); err != nil {
		return note, fmt.Errorf("could not open a browser: %w", err)
	}
	return note, nil
}

// ---------- server ----------

type launcher struct {
	paths      gamePaths
	gh         *ghClient
	once       sync.Once
	state      pageState
	ready      chan struct{}
	mu         sync.Mutex
	lastPing   time.Time
	quit       chan struct{}
	quitOnce   sync.Once
	windowNote string

	// PLAY: the packwiz update and the Prism start (fakes in the tests)
	packwiz      packwizRunner // the update PLAY runs
	launchPrism  func() error  // starts Prism on the instance
	playMu       sync.Mutex    // one PLAY at a time
	updateFailed bool          // the last PLAY's check failed: the next press launches anyway (guarded by playMu)
	// GitHub's raw files are cached up to 5 min (Cache-Control max-age=300): just after a pack is published, packwiz can
	// read the old pack.toml and report "up to date" (Oct 26). A clean run that left an older jar is retried this often.
	updateTries int
	updateWait  time.Duration

	// release channels and the update watcher (channels.go). All optional: nil/zero means "not running" or the default.
	live     liveState       // guarded by mu
	note     string          // guarded by mu: what the watcher did, shown on the page
	gameOpen gameOpenChecker // nil: logLockProbe on the instance
	watcher  *updateWatcher  // nil: no background checks (tests, GitHub unreachable)
	kick     chan struct{}   // a channel switch asks the watcher for an early check
}

func newLauncher(p gamePaths, gh *ghClient) *launcher {
	return &launcher{paths: p, gh: gh, ready: make(chan struct{}), quit: make(chan struct{}),
		packwiz: execPackwiz{}, launchPrism: func() error { return startPrism(p) },
		updateTries: 8, updateWait: 45 * time.Second}
}

func (l *launcher) load() {
	l.once.Do(func() {
		go func() {
			ps, live := buildPageState(l.paths, l.gh)
			l.mu.Lock()
			l.state, l.live = ps, live
			l.mu.Unlock()
			close(l.ready)
		}()
	})
}

func (l *launcher) stop() { l.quitOnce.Do(func() { close(l.quit) }) }

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func (l *launcher) handler(token string) http.Handler {
	mux := http.NewServeMux()
	pre := "/" + token
	post := func(h http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodPost {
				writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
				return
			}
			h(w, r)
		}
	}
	mux.HandleFunc(pre+"/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != pre+"/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		fmt.Fprint(w, launcherHTML)
	})
	mux.HandleFunc(pre+"/bg.jpg", func(w http.ResponseWriter, r *http.Request) { serveImage(w, "image/jpeg", launcherBg) })
	mux.HandleFunc(pre+"/cat.png", func(w http.ResponseWriter, r *http.Request) { serveImage(w, "image/png", catPNG) })
	mux.HandleFunc(pre+"/api/state", func(w http.ResponseWriter, r *http.Request) {
		l.load()
		<-l.ready
		writeJSON(w, 200, l.view(time.Now()))
	})
	mux.HandleFunc(pre+"/api/channel", post(func(w http.ResponseWriter, r *http.Request) {
		if err := l.setChannel(r.URL.Query().Get("c")); err != nil {
			writeJSON(w, 409, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, 200, l.view(time.Now()))
	}))
	mux.HandleFunc(pre+"/api/ping", post(func(w http.ResponseWriter, r *http.Request) {
		l.mu.Lock()
		l.lastPing = time.Now()
		l.mu.Unlock()
		writeJSON(w, 200, map[string]bool{"ok": true})
	}))
	mux.HandleFunc(pre+"/api/quit", post(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]bool{"ok": true})
		l.stop()
	}))
	mux.HandleFunc(pre+"/api/play", post(func(w http.ResponseWriter, r *http.Request) {
		res, err := l.play()
		if err != nil {
			writeJSON(w, 500, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, 200, res)
		if res.Launched {
			go func() { time.Sleep(2 * time.Second); l.stop() }()
		}
	}))
	mux.HandleFunc(pre+"/api/open", post(func(w http.ResponseWriter, r *http.Request) {
		if err := l.open(r.URL.Query().Get("what")); err != nil {
			writeJSON(w, 500, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, 200, map[string]bool{"ok": true})
	}))
	return mux
}

func serveImage(w http.ResponseWriter, contentType string, data []byte) {
	w.Header().Set("Content-Type", contentType)
	w.Write(data)
}

// ---------- PLAY: update, check, start ----------

// packwizRunner runs the game's packwiz update. The real one starts Java; the tests use a fake.
type packwizRunner interface {
	Run(dir, exe string, args []string) (out string, err error)
}

type execPackwiz struct{}

func (execPackwiz) Run(dir, exe string, args []string) (string, error) {
	cmd := exec.Command(exe, args...)
	cmd.Dir = dir
	hideWindow(cmd) // no console window: the output goes to packwiz-update.log
	out, err := cmd.CombinedOutput()
	return string(out), err
}

// splitCommand splits a command line as Prism stores it in instance.cfg: spaces separate, and "..." or \"...\" group.
// Prism runs the command without a shell, so there is no other quoting.
func splitCommand(s string) []string {
	var toks []string
	var cur strings.Builder
	inQuote, have := false, false
	for i := 0; i < len(s); i++ {
		c := s[i]
		switch {
		case c == '\\' && i+1 < len(s) && s[i+1] == '"':
			inQuote, have = !inQuote, true
			i++
		case c == '"':
			inQuote, have = !inQuote, true
		case c == ' ' && !inQuote:
			if have {
				toks = append(toks, cur.String())
				cur.Reset()
				have = false
			}
		default:
			cur.WriteByte(c)
			have = true
		}
	}
	if have {
		toks = append(toks, cur.String())
	}
	return toks
}

// packwizCommand is the instance's PreLaunchCommand with $INST_JAVA replaced by the game's Java: the program and its
// arguments. It is read from instance.cfg, so the launcher runs what Prism would run before a launch.
func packwizCommand(p gamePaths) (exe string, args []string, err error) {
	cfg := filepath.Join(p.prismDir, "instances", instanceName, "instance.cfg")
	raw := readCfgValue(cfg, "PreLaunchCommand")
	if raw == "" {
		return "", nil, fmt.Errorf("no PreLaunchCommand in %s: run BeyondAndTabs-Setup.exe again", cfg)
	}
	toks := splitCommand(raw)
	isPackwiz := false
	for _, t := range toks {
		if strings.HasSuffix(t, "packwiz-installer-bootstrap.jar") {
			isPackwiz = true
		}
	}
	if !isPackwiz {
		return "", nil, fmt.Errorf("PreLaunchCommand in %s is not the packwiz update: %s", cfg, raw)
	}
	if toks[0] == "$INST_JAVA" {
		toks[0] = javaExe(p)
	}
	return toks[0], toks[1:], nil
}

// lastOutputLine is the last non-empty line the update printed (the warning shows it), else the run error.
func lastOutputLine(out string, runErr error) string {
	lines := strings.Split(strings.ReplaceAll(out, "\r\n", "\n"), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		if t := strings.TrimSpace(lines[i]); t != "" {
			if len(t) > 200 {
				t = t[:200] + "..."
			}
			return t
		}
	}
	if runErr != nil {
		return shortErr(runErr)
	}
	return "no output"
}

func writeUpdateLog(path, exe string, args []string, out string, runErr error) error {
	var b strings.Builder
	fmt.Fprintf(&b, "%s $ %s %s\n", time.Now().Format("2006-01-02 15:04:05"), exe, strings.Join(args, " "))
	b.WriteString(out)
	if runErr != nil {
		fmt.Fprintf(&b, "\nexit: %v\n", runErr)
	}
	return os.WriteFile(path, []byte(b.String()), 0o644)
}

// playPlan is what PLAY does once the update has run: start Prism, or stop with a warning (the next press launches).
type playPlan struct {
	Launch bool
	Level  string
	Text   string
}

// planAfterUpdate decides from the game jar's commit after packwiz ran. forced is the second press after a failed
// check: launch whatever is installed. An unknown pack (GitHub unreachable) cannot be checked, so PLAY launches and says so.
func planAfterUpdate(jarSha, packSha, lastLine string, forced bool) playPlan {
	have := short(jarSha)
	if have == "" {
		have = "no game jar"
	}
	switch {
	case packSha == "":
		return playPlan{true, "warn", "Couldn't read the newest pack from GitHub, so the install is not checked."}
	case short(jarSha) == short(packSha):
		return playPlan{true, "ok", "Installed pack " + short(packSha) + "."}
	case forced:
		return playPlan{true, "warn", "Launching anyway: you have " + have + ", not the newest pack " + short(packSha) + "."}
	default:
		return playPlan{false, "warn", "Update did not install pack " + short(packSha) + " (you have " + have + "): " + lastLine}
	}
}

// playResult is PLAY's answer to the page. Launched false: the page shows Text and PLAY can be pressed again.
type playResult struct {
	Launched bool   `json:"launched"`
	Level    string `json:"level,omitempty"`
	Text     string `json:"text"`
}

// play runs the packwiz update, checks that the game jar is the newest tester pack and starts Prism on the instance.
// A failed check starts nothing: the warning says why, and a second press launches anyway.
func (l *launcher) play() (playResult, error) {
	if !exists(l.paths.prismExe) {
		return playResult{}, fmt.Errorf("Prism Launcher not found at %s - run BeyondAndTabs-Setup.exe again", l.paths.prismExe)
	}
	l.playMu.Lock()
	defer l.playMu.Unlock()
	l.load()
	<-l.ready
	forced := l.updateFailed
	packSha, ch := l.selected()
	lastLine := ""
	if !forced {
		var err error
		if lastLine, err = l.runUpdate(ch, packSha); err != nil {
			return playResult{}, err
		}
	}
	jar, _ := readBuild(filepath.Join(l.paths.mcDir, "mods"))
	plan := planAfterUpdate(jar.Sha, packSha, lastLine, forced)
	if !plan.Launch {
		l.updateFailed = true
		return playResult{Level: plan.Level, Text: plan.Text}, nil
	}
	// remember the pack that is installed (a main commit, so the news compares from there); the jar's commit if unknown.
	// The rest of the state (the channel) is kept.
	s := readState(l.paths.state)
	s.LastPlayedAt, s.LastPlayedSha = time.Now().UTC(), packSha
	if s.LastPlayedSha == "" {
		s.LastPlayedSha = jar.Sha
	}
	if err := writeState(l.paths.state, s); err != nil {
		return playResult{}, fmt.Errorf("saving %s: %w", stateFileName, err)
	}
	if err := l.launchPrism(); err != nil {
		return playResult{}, err
	}
	l.updateFailed = false
	return playResult{Launched: true, Level: plan.Level, Text: plan.Text + " Starting Prism Launcher ..."}, nil
}

// selected: the chosen channel and its newest pack's commit ("" when GitHub could not say).
func (l *launcher) selected() (packSha string, ch channel) {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.state.PackSha, selectedChannel(l.state.Channel)
}

// runUpdate points the instance at the channel's pack (instance.cfg, channels.go) and runs the packwiz update, retrying
// a clean run that left an older jar than packSha (GitHub's raw cache). It returns the update's last output line; an
// error only when the update could not be started. The caller holds playMu.
func (l *launcher) runUpdate(ch channel, packSha string) (lastLine string, err error) {
	if _, err := setPackURL(instanceCfgPath(l.paths), ch.PackTomlURL()); err != nil {
		return "", err
	}
	exe, args, err := packwizCommand(l.paths)
	if err != nil {
		return "", err
	}
	for try := 1; ; try++ {
		out, runErr := l.packwiz.Run(l.paths.mcDir, exe, args)
		if err := writeUpdateLog(updateLogPath(l.paths), exe, args, out, runErr); err != nil {
			return "", fmt.Errorf("writing packwiz-update.log: %w", err)
		}
		lastLine = lastOutputLine(out, runErr)
		jar, _ := readBuild(filepath.Join(l.paths.mcDir, "mods"))
		staleCache := runErr == nil && strings.Contains(out, "Finished successfully") &&
			packSha != "" && short(jar.Sha) != short(packSha)
		if !staleCache || try >= l.updateTries {
			return lastLine, nil
		}
		fmt.Printf("packwiz read an older pack than %s (GitHub's cache): retry %d of %d in %s\n",
			short(packSha), try, l.updateTries-1, l.updateWait)
		time.Sleep(l.updateWait)
	}
}

// view is the page's data now: the loaded state with the status line, the wait and the window note filled in.
func (l *launcher) view(now time.Time) pageState {
	l.mu.Lock()
	s, live := l.state, l.live
	s.WindowNote, s.Note = l.windowNote, l.note
	l.mu.Unlock()
	if s.Channels == nil {
		s.Channels = channelOptions()
	}
	if s.Channel == "" {
		s.Channel = channelTested
	}
	return withStatus(s, l.paths, live, now)
}

// setChannel saves the Version menu's choice and shows that channel's pack. Nothing is installed here: PLAY (or the
// watcher, for a pack that lands later) installs it. Refused while PLAY or an automatic update is running.
func (l *launcher) setChannel(id string) error {
	ch, ok := channelByID(id)
	if !ok {
		return fmt.Errorf("unknown version %q", id)
	}
	if !l.playMu.TryLock() {
		return errors.New("the game is being updated: try again when it is done")
	}
	defer l.playMu.Unlock()
	l.load()
	<-l.ready
	st := readState(l.paths.state)
	st.Channel = ch.ID
	if err := writeState(l.paths.state, st); err != nil {
		return fmt.Errorf("saving %s: %w", stateFileName, err)
	}
	l.mu.Lock()
	l.state.Channel, l.state.PackSha = ch.ID, l.live.packs[ch.ID].Sha
	l.live.wait, l.note = nil, ""
	l.mu.Unlock()
	l.updateFailed = false // a failed check of the other channel says nothing about this one
	if l.kick != nil {
		select {
		case l.kick <- struct{}{}:
		default:
		}
	}
	return nil
}

func (l *launcher) gameIsOpen() bool {
	if l.gameOpen != nil {
		return l.gameOpen.GameOpen()
	}
	return logLockProbe{mcDir: l.paths.mcDir}.GameOpen()
}

// pollUpdates is one check of the watcher: what is coming to the chosen channel, and an automatic install when its
// pack moved. It never starts the game. Returns false when the watcher should stop (GitHub budget used up).
func (l *launcher) pollUpdates(now time.Time) bool {
	packSha, ch := l.selected()
	l.mu.Lock()
	mainKnown := l.state.MainSha != ""
	l.mu.Unlock()
	if !mainKnown {
		return false // GitHub was unreachable at start: the status already says so
	}
	if l.watcher.newHour(now) {
		l.mu.Lock()
		if strings.HasPrefix(l.note, "Stopped checking") {
			l.note = "" // the budget is new: watching again
		}
		l.mu.Unlock()
	}
	if l.watcher.gh.calls >= pollCallBudget-3 {
		l.mu.Lock()
		l.live.wait, l.note = nil, "Stopped checking GitHub for updates (hourly limit): press PLAY to update."
		l.mu.Unlock()
		return false
	}
	p, tracked, head, headErr, err := l.watcher.check(ch, packSha, now)
	if err != nil {
		l.mu.Lock()
		l.live.wait = nil // a stale "update coming" would keep PLAY disabled until a poll works
		l.note = "Couldn't check for a coming update (" + shortErr(err) + ")"
		l.mu.Unlock()
		return true
	}
	if _, cur := l.selected(); cur.ID != ch.ID {
		return true // the channel changed while checking: the next poll is for the new one
	}
	l.mu.Lock()
	l.live.wait = nil
	if tracked {
		l.live.wait = &p
	}
	moved := tracked && p.Done && headErr == nil && head.Sha != "" && short(head.Sha) != short(packSha)
	if moved {
		if l.live.packs == nil {
			l.live.packs = map[string]packHead{}
		}
		l.live.packs[ch.ID], l.state.PackSha = head, head.Sha
		l.live.wait = nil
	}
	l.mu.Unlock()
	if moved {
		l.autoInstall(ch, packSha, head.Sha)
	}
	return true
}

// autoInstall installs a channel's new pack by itself (autoInstallDecision says when). Never launches the game.
func (l *launcher) autoInstall(ch channel, oldPack, newPack string) {
	busy := !l.playMu.TryLock()
	if !busy {
		defer l.playMu.Unlock()
	}
	jar, _ := readBuild(filepath.Join(l.paths.mcDir, "mods"))
	install, why := autoInstallDecision(oldPack, newPack, jar.Sha, !busy && l.gameIsOpen(), busy)
	if !install {
		if why != "" {
			l.mu.Lock()
			l.note = why
			l.mu.Unlock()
		}
		return
	}
	l.mu.Lock()
	l.note = "Installing " + ch.Label + " pack " + short(newPack) + " ..."
	l.mu.Unlock()
	lastLine, err := l.runUpdate(ch, newPack)
	jar, _ = readBuild(filepath.Join(l.paths.mcDir, "mods"))
	note := "Installed " + ch.Label + " pack " + short(newPack) + ": press PLAY."
	switch {
	case err != nil:
		note = "Couldn't install pack " + short(newPack) + " (" + shortErr(err) + "): PLAY tries again."
	case short(jar.Sha) != short(newPack):
		note = "Pack " + short(newPack) + " did not install (" + lastLine + "): PLAY tries again."
	default:
		l.updateFailed = false
	}
	l.mu.Lock()
	l.note = note
	l.mu.Unlock()
}

// watchUpdates polls every interval (or at once after a channel switch) until the launcher stops.
func (l *launcher) watchUpdates(interval time.Duration) {
	l.load()
	<-l.ready
	t := time.NewTicker(interval)
	defer t.Stop()
	for {
		if !l.pollUpdates(time.Now()) {
			return
		}
		select {
		case <-l.quit:
			return
		case <-t.C:
		case <-l.kick:
		}
	}
}

// updateLogPath: the packwiz output of the last PLAY (overwritten each time).
func updateLogPath(p gamePaths) string { return filepath.Join(p.root, "packwiz-update.log") }

// startPrism opens Prism Launcher on the instance. Prism's own pre-launch command runs too; after the PLAY update it does nothing.
func startPrism(p gamePaths) error {
	cmd := exec.Command(p.prismExe, "-l", instanceName)
	cmd.Dir = p.prismDir
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting Prism Launcher: %w", err)
	}
	return nil
}

func (l *launcher) isReady() bool {
	select {
	case <-l.ready:
		return true
	default:
		return false
	}
}

func (l *launcher) open(what string) error {
	switch what {
	case "log":
		dir := filepath.Join(l.paths.mcDir, "logs")
		if !exists(filepath.Join(dir, "latest.log")) {
			return errors.New("no log yet: play once first")
		}
		return exec.Command("explorer.exe", dir).Start() // explorer's exit code is 1 even on success: Start, not Run
	case "game":
		if !exists(l.paths.mcDir) {
			return fmt.Errorf("game folder not found (%s): run BeyondAndTabs-Setup.exe", l.paths.mcDir)
		}
		return exec.Command("explorer.exe", l.paths.mcDir).Start()
	case "help":
		return openURL(troubleshootURL)
	case "bug":
		return openURL(reportBugURL)
	}
	return fmt.Errorf("unknown target %q", what)
}

// watch stops the server on quit, on a lost heartbeat, or after maxLifetime.
func (l *launcher) watch(start time.Time, now func() time.Time) string {
	t := time.NewTicker(2 * time.Second)
	defer t.Stop()
	for {
		select {
		case <-l.quit:
			return "closed by the page"
		case <-t.C:
		}
		l.mu.Lock()
		last := l.lastPing
		l.mu.Unlock()
		if why := stopReason(start, last, now()); why != "" {
			return why
		}
	}
}

func stopReason(start, lastPing, now time.Time) string {
	switch {
	case now.Sub(start) > maxLifetime:
		return "safety timeout (30 min)"
	case lastPing.IsZero() && now.Sub(start) > firstPingWait:
		return "the window never connected"
	case !lastPing.IsZero() && now.Sub(lastPing) > pingTimeout:
		return "the window was closed"
	}
	return ""
}

func randomToken() string {
	b := make([]byte, 12)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// runLauncher is the launcher mode. --no-window starts the server and prints the URL without opening anything.
func runLauncher(args []string) error {
	noWindow := hasFlag(args, "--no-window")
	if !noWindow {
		hideConsole()
	}
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		return errors.New("LOCALAPPDATA is not set")
	}
	l := newLauncher(newGamePaths(base), &ghClient{base: ghAPI, client: &http.Client{Timeout: 15 * time.Second}})
	gpuNote := ensureHighGPU(l.paths) // before Prism starts; a failure is shown on the page and the launch goes on
	l.load()                          // fetch while the window opens
	// what is coming to the chosen channel, and its automatic install (channels.go): its own GitHub budget
	l.watcher = newUpdateWatcher(&ghClient{base: ghAPI, client: &http.Client{Timeout: 15 * time.Second}, budget: pollCallBudget})
	l.kick = make(chan struct{}, 1)
	go l.watchUpdates(pollInterval)

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return fmt.Errorf("starting the launcher's local page: %w", err)
	}
	token := randomToken()
	pageURL := fmt.Sprintf("http://%s/%s/", ln.Addr().String(), token)
	srv := &http.Server{Handler: l.handler(token), ReadHeaderTimeout: 10 * time.Second}
	go srv.Serve(ln)
	fmt.Println("Launcher page:", pageURL)

	if !noWindow {
		note, err := openWindow(pageURL)
		l.mu.Lock()
		l.windowNote = strings.TrimSpace(gpuNote + " " + note)
		l.mu.Unlock()
		if err != nil {
			srv.Close()
			return fmt.Errorf("%v\nOpen this address in a browser instead: %s", err, pageURL)
		}
	}
	why := l.watch(time.Now(), time.Now)
	fmt.Println("Launcher stopped:", why)
	return srv.Close()
}
