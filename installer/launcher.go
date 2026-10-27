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
	"context"
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
	ghCallBudget    = 4
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

var packRe = regexp.MustCompile(`^Tester pack (\d{4}\.\d{2}\.\d{2}-\d{4})-([0-9a-f]{7,40})$`)

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
	switch {
	case err != nil:
		return "Couldn't check GitHub (" + shortErr(err) + ")", "warn"
	case pack.Sha == "":
		return "Couldn't read the newest tester pack from GitHub", "warn"
	}
	ahead := ""
	if short(mainSha) != short(pack.Sha) {
		ahead = " · main has newer merges still being packed (usually ~25 min)"
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
}

func (g *ghClient) get(path string, v any) error {
	if g.calls >= ghCallBudget {
		return fmt.Errorf("GitHub call budget (%d) used up", ghCallBudget)
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
	PackErr   error // the newest pack could not be read: the status line says why
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

// fetchGitHub makes at most 4 calls: main's head, the newest tester pack, then either compare(last...main) or the recent
// closed PRs, then the open PRs.
func fetchGitHub(g *ghClient, lastSha string) (r ghResult) {
	defer func() { r.Calls = g.calls }()
	repo := "/repos/" + repoSlug

	var head ghCommit
	if err := g.get(repo+"/commits/main", &head); err != nil {
		r.Err = err
		return r
	}
	r.MainSha, r.MainDate = head.Sha, head.Commit.Committer.Date

	var packTip ghCommit
	if err := g.get("/repos/"+packRepoSlug+"/commits/main", &packTip); err != nil {
		r.PackErr = err
	} else {
		r.Pack, r.PackErr = parsePackCommit(packTip.Commit.Message)
	}

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
}

func buildPageState(p gamePaths, g *ghClient) pageState {
	st := readState(p.state)
	b, haveBuild := readBuild(filepath.Join(p.mcDir, "mods"))
	r := fetchGitHub(g, st.LastPlayedSha)

	ps := pageState{Version: "Latest", MainSha: r.MainSha, PackSha: r.Pack.Sha, News: r.News, NewsNote: r.NewsNote, Soon: r.Soon,
		SoonNote: r.SoonNote, GHCalls: r.Calls, NewCount: -1, NewsTitle: "Recently merged"}
	if haveBuild {
		ps.Build = &b
	}
	if !r.MainDate.IsZero() {
		ps.MainDate = &r.MainDate
	}
	checkErr := r.Err
	if checkErr == nil {
		checkErr = r.PackErr
	}
	ps.Status, ps.Level = statusLine(b.Sha, r.MainSha, r.Pack, checkErr)
	if haveBuild && b.Sha == "" && checkErr == nil {
		ps.Status, ps.Level = "Your game jar has no commit in its name ("+b.File+"): can't compare with the newest pack ("+short(r.Pack.Sha)+")", "update"
	}
	if haveBuild && b.Jars > 1 {
		ps.Status += fmt.Sprintf(" - warning: %d copies of the game jar in mods, Forge needs one", b.Jars)
		ps.Level = "warn"
	}
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
	gameRunning  func() bool   // the game (Prism's Java for this instance) is open: its files are locked
	playMu       sync.Mutex    // one PLAY at a time
	updateFailed bool          // the last PLAY's check failed: the next press launches anyway (guarded by playMu)
	// GitHub's raw files are cached up to 5 min (Cache-Control max-age=300): just after a pack is published, packwiz can
	// read the old pack.toml and report "up to date" (Oct 26). A clean run that left an older jar is retried this often.
	updateTries int
	updateWait  time.Duration
}

func newLauncher(p gamePaths, gh *ghClient) *launcher {
	return &launcher{paths: p, gh: gh, ready: make(chan struct{}), quit: make(chan struct{}),
		packwiz: execPackwiz{}, launchPrism: func() error { return startPrism(p) },
		gameRunning: func() bool { return gameIsRunning(p) },
		updateTries: 8, updateWait: 45 * time.Second}
}

func (l *launcher) load() {
	l.once.Do(func() {
		go func() {
			l.state = buildPageState(l.paths, l.gh)
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
		s := l.state
		l.mu.Lock()
		s.WindowNote = l.windowNote
		l.mu.Unlock()
		writeJSON(w, 200, s)
	})
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

// packwizTimeout bounds one update run: a hung update must never leave PLAY waiting forever.
const packwizTimeout = 10 * time.Minute

// headlessArgs adds packwiz-installer's -g (no GUI) before the pack URL. hideWindow hides the window it starts, so a GUI
// dialog ("Failed to download ... " when the running game holds a file, Oct 26) was invisible and waited forever.
// With -g packwiz prints the error and exits, and PLAY shows it.
func headlessArgs(args []string) []string {
	for _, a := range args {
		if a == "-g" || a == "--no-gui" {
			return args
		}
	}
	if len(args) == 0 {
		return []string{"-g"}
	}
	out := append([]string{}, args[:len(args)-1]...)
	return append(out, "-g", args[len(args)-1])
}

func (execPackwiz) Run(dir, exe string, args []string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), packwizTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, exe, headlessArgs(args)...)
	cmd.Dir = dir
	hideWindow(cmd) // no console window: the output goes to packwiz-update.log
	out, err := cmd.CombinedOutput()
	if ctx.Err() == context.DeadlineExceeded {
		return string(out), fmt.Errorf("the pack update did not finish in %s and was stopped", packwizTimeout)
	}
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
	// an open game holds its mods and resource packs: the update cannot replace them (Oct 26: PLAY hung on this)
	if l.gameRunning != nil && l.gameRunning() {
		return playResult{Level: "warn", Text: "The game is still open: close it, then press PLAY again (the update can't replace files the game is using)."}, nil
	}
	forced := l.updateFailed
	lastLine := ""
	if !forced {
		exe, args, err := packwizCommand(l.paths)
		if err != nil {
			return playResult{}, err
		}
		for try := 1; ; try++ {
			out, runErr := l.packwiz.Run(l.paths.mcDir, exe, args)
			if err := writeUpdateLog(updateLogPath(l.paths), exe, args, out, runErr); err != nil {
				return playResult{}, fmt.Errorf("writing packwiz-update.log: %w", err)
			}
			lastLine = lastOutputLine(out, runErr)
			jar, _ := readBuild(filepath.Join(l.paths.mcDir, "mods"))
			staleCache := runErr == nil && strings.Contains(out, "Finished successfully") &&
				l.state.PackSha != "" && short(jar.Sha) != short(l.state.PackSha)
			if !staleCache || try >= l.updateTries {
				break
			}
			fmt.Printf("packwiz read an older pack than %s (GitHub's cache): retry %d of %d in %s\n",
				short(l.state.PackSha), try, l.updateTries-1, l.updateWait)
			time.Sleep(l.updateWait)
		}
	}
	jar, _ := readBuild(filepath.Join(l.paths.mcDir, "mods"))
	plan := planAfterUpdate(jar.Sha, l.state.PackSha, lastLine, forced)
	if !plan.Launch {
		l.updateFailed = true
		return playResult{Level: plan.Level, Text: plan.Text}, nil
	}
	// remember the pack that is installed (a main commit, so the news compares from there); the jar's commit if unknown
	s := launcherState{LastPlayedAt: time.Now().UTC(), LastPlayedSha: l.state.PackSha}
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
	l.load() // fetch while the window opens

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
