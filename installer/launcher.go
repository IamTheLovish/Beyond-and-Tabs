// Launcher mode: `BeyondAndTabs-Setup.exe --launch`, or the same exe copied to %LOCALAPPDATA%\BeyondAndTabs\launcher.exe.
//
// It serves one page from 127.0.0.1 (random free port, random token in the path) and opens it as an Edge app window
// (1200x640). The page shows your build (from the game jar's name), what is new on main since you last played and the
// open pull requests ("Coming soon"). PLAY remembers the build you played and starts Prism Launcher on the instance;
// the instance's pre-launch command (packwiz) updates the game, so the launcher never downloads anything itself.
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
	ghAPI           = "https://api.github.com"
	ghCallBudget    = 3
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

// statusLine: level is "ok" (green), "update" (gold) or "warn" (amber).
func statusLine(jarSha, mainSha string, ghErr error) (text, level string) {
	switch {
	case ghErr != nil:
		return "Couldn't check GitHub (" + shortErr(ghErr) + ")", "warn"
	case jarSha == "":
		return "Game files not found yet: PLAY installs the newest build (main is at " + short(mainSha) + ")", "update"
	case short(jarSha) == short(mainSha):
		return "Up to date: your build is the newest on main", "ok"
	default:
		return "Update available: main is at " + short(mainSha), "update"
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

// fetchGitHub makes at most 3 calls: main's head, then either compare(last...main) or the recent closed PRs, then the
// open PRs.
func fetchGitHub(g *ghClient, lastSha string) (r ghResult) {
	defer func() { r.Calls = g.calls }()
	repo := "/repos/" + repoSlug

	var head ghCommit
	if err := g.get(repo+"/commits/main", &head); err != nil {
		r.Err = err
		return r
	}
	r.MainSha, r.MainDate = head.Sha, head.Commit.Committer.Date

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

	ps := pageState{Version: "Latest", MainSha: r.MainSha, News: r.News, NewsNote: r.NewsNote, Soon: r.Soon,
		SoonNote: r.SoonNote, GHCalls: r.Calls, NewCount: -1, NewsTitle: "Recently merged"}
	if haveBuild {
		ps.Build = &b
	}
	if !r.MainDate.IsZero() {
		ps.MainDate = &r.MainDate
	}
	ps.Status, ps.Level = statusLine(b.Sha, r.MainSha, r.Err)
	if haveBuild && b.Sha == "" && r.Err == nil {
		ps.Status, ps.Level = "Your game jar has no commit in its name ("+b.File+"): can't compare with main ("+short(r.MainSha)+")", "update"
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

// openWindow opens the page as an Edge app window, else in the default browser; the note says which.
func openWindow(u string) (note string, err error) {
	if edge := findEdge(); edge != "" {
		cmd := exec.Command(edge, "--app="+u, "--window-size=1200,640")
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
}

func newLauncher(p gamePaths, gh *ghClient) *launcher {
	return &launcher{paths: p, gh: gh, ready: make(chan struct{}), quit: make(chan struct{})}
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
		if err := l.play(); err != nil {
			writeJSON(w, 500, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, 200, map[string]bool{"ok": true})
		go func() { time.Sleep(2 * time.Second); l.stop() }()
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

// play remembers what you played (main's head when known, else your jar's commit) and starts Prism on the instance.
func (l *launcher) play() error {
	if !exists(l.paths.prismExe) {
		return fmt.Errorf("Prism Launcher not found at %s - run BeyondAndTabs-Setup.exe again", l.paths.prismExe)
	}
	s := launcherState{LastPlayedAt: time.Now().UTC()}
	if l.isReady() && l.state.MainSha != "" {
		s.LastPlayedSha = l.state.MainSha
	} else if l.isReady() && l.state.Build != nil {
		s.LastPlayedSha = l.state.Build.Sha
	} else if b, ok := readBuild(filepath.Join(l.paths.mcDir, "mods")); ok {
		s.LastPlayedSha = b.Sha
	}
	if err := writeState(l.paths.state, s); err != nil {
		return fmt.Errorf("saving %s: %w", stateFileName, err)
	}
	cmd := exec.Command(l.paths.prismExe, "-l", instanceName)
	cmd.Dir = l.paths.prismDir
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
		l.windowNote = note
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
