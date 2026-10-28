// Release channels and the "update coming" watcher (testers, Oct 26: "Faster pack", "Pre-release channel", "Launcher
// waits for it").
//
// Two packs live in the pack repo: "Latest - tested" (branch main, publish-pack.yml after every GameTest passed, ~25 min
// after a merge) and "Newest - untested" (branch fast, publish-fast-pack.yml after compile + unit tests, ~5 min). The
// Version menu picks one; the choice is saved in launcher-state.json.
//
// Which pack PLAY installs: Prism runs the instance's PreLaunchCommand (the packwiz bootstrap + a pack.toml URL) before
// every launch, so passing a URL only to the launcher's own packwiz run is not enough - Prism would put the other
// channel's pack back on its own pre-launch run. The launcher therefore rewrites the URL inside PreLaunchCommand of
// instance.cfg (only that token, only when it differs, temp file + rename) before it runs the update.
//
// While main has merges newer than the channel's pack, a background watcher reads the GitHub Actions runs that lead to
// that channel's pack, shows "update coming in ~N min" (ETA = the median of recent successful runs) and, when the
// channel's pack commit changes, installs it at once - only when the game is not open and PLAY is not running. It
// never starts the game.
package main

import (
	"fmt"
	"log"
	"math"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	channelTested = "tested"
	channelFast   = "fast"
	// the watcher's own GitHub budget: unauthenticated callers get 60 calls an hour, the page load takes ghCallBudget
	pollCallBudget = 40
	pollInterval   = 2 * time.Minute
)

type channel struct {
	ID     string
	Label  string // the Version menu and the status line
	Hint   string // the menu's second line
	Branch string // the pack repo branch holding windows/pack.toml
	// the workflows (in this repo) that lead from a merge on main to this channel's pack, in order, and their duration
	// when GitHub has no finished run to measure
	Workflows []string
	Fallback  []time.Duration
	Usually   string // the status line's hint when no run can be tracked
}

var channels = []channel{
	{ID: channelTested, Label: "Latest - tested", Hint: "every in-game test passed", Branch: "main",
		Workflows: []string{"build.yml", "publish-pack.yml"}, Fallback: []time.Duration{14 * time.Minute, 6 * time.Minute},
		Usually: "usually ~25 min"},
	{ID: channelFast, Label: "Newest - untested", Hint: "minutes after each merge", Branch: "fast",
		Workflows: []string{"publish-fast-pack.yml"}, Fallback: []time.Duration{7 * time.Minute},
		Usually: "usually ~5 min"},
}

func channelByID(id string) (channel, bool) {
	for _, c := range channels {
		if c.ID == id {
			return c, true
		}
	}
	return channel{}, false
}

// selectedChannel: the saved choice; nothing saved (or an unknown id from a newer launcher) is the tested pack.
func selectedChannel(id string) channel {
	if c, ok := channelByID(id); ok {
		return c
	}
	return channels[0]
}

// PackTomlURL is what packwiz reads for this channel. The tested one is main.go's packTomlURL (a test checks).
func (c channel) PackTomlURL() string {
	return "https://raw.githubusercontent.com/" + packRepoSlug + "/" + c.Branch + "/windows/pack.toml"
}

type channelOption struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Hint  string `json:"hint"`
}

func channelOptions() []channelOption {
	var o []channelOption
	for _, c := range channels {
		o = append(o, channelOption{c.ID, c.Label, c.Hint})
	}
	return o
}

// fetchPackHead reads the newest pack of a channel: the branch's head commit "Tester pack ..." / "Fast pack ...".
func fetchPackHead(g *ghClient, c channel) (packHead, error) {
	var tip ghCommit
	if err := g.get("/repos/"+packRepoSlug+"/commits/"+c.Branch, &tip); err != nil {
		return packHead{}, err
	}
	return parsePackCommit(tip.Commit.Message)
}

// ---------- instance.cfg: the channel's URL in Prism's pre-launch command ----------

// setPackURL rewrites the pack.toml URL in PreLaunchCommand of instance.cfg to url. Nothing else in the file changes
// (line endings included); an unchanged URL writes nothing. Written to a temp file and renamed, so a failure never
// leaves half a config.
func setPackURL(cfgPath, url string) (changed bool, err error) {
	data, err := os.ReadFile(cfgPath)
	if err != nil {
		return false, fmt.Errorf("reading %s: %w", cfgPath, err)
	}
	lines := strings.Split(string(data), "\n")
	found := false
	for i, line := range lines {
		if !strings.HasPrefix(strings.TrimSpace(line), "PreLaunchCommand=") {
			continue
		}
		found = true
		old := ""
		for _, tok := range splitCommand(strings.TrimRight(line, "\r")) {
			if strings.HasPrefix(tok, "https://") && strings.HasSuffix(tok, "/pack.toml") {
				old = tok
			}
		}
		if old == "" {
			return false, fmt.Errorf("PreLaunchCommand in %s has no pack.toml URL: run BeyondAndTabs-Setup.exe again", cfgPath)
		}
		if old == url {
			return false, nil
		}
		lines[i] = strings.Replace(line, old, url, 1)
	}
	if !found {
		return false, fmt.Errorf("no PreLaunchCommand in %s: run BeyondAndTabs-Setup.exe again", cfgPath)
	}
	tmp := cfgPath + ".new"
	if err := os.WriteFile(tmp, []byte(strings.Join(lines, "\n")), 0o644); err != nil {
		os.Remove(tmp)
		return false, fmt.Errorf("writing %s: %w", tmp, err)
	}
	if err := os.Rename(tmp, cfgPath); err != nil {
		os.Remove(tmp)
		return false, fmt.Errorf("replacing %s: %w", cfgPath, err)
	}
	return true, nil
}

func instanceCfgPath(p gamePaths) string {
	return filepath.Join(p.prismDir, "instances", instanceName, "instance.cfg")
}

// ---------- is the game open ----------

// gameOpenChecker says whether the game is running (its files are locked, so an update must wait). An interface so a
// fuller check (PR #264's gameIsRunning) can replace the minimal one below without touching the callers.
type gameOpenChecker interface {
	GameOpen() bool
}

type gameOpenFunc func() bool

func (f gameOpenFunc) GameOpen() bool { return f() }

// logLockProbe: a running game holds logs/latest.log open, and Windows refuses to rename an open file. No log = not
// open. On other systems the rename always works, so it says "not open" (the launcher only ships for Windows).
// If the rename back fails, latest.log stays renamed and something is wrong: log it and say "open" (fail safe: no
// automatic install).
type logLockProbe struct {
	mcDir  string
	rename func(oldpath, newpath string) error // nil: os.Rename (a test injects failures)
}

func (p logLockProbe) GameOpen() bool {
	rename := p.rename
	if rename == nil {
		rename = os.Rename
	}
	logFile := filepath.Join(p.mcDir, "logs", "latest.log")
	if !exists(logFile) {
		return false
	}
	probe := logFile + ".probe"
	if err := rename(logFile, probe); err != nil {
		return true
	}
	if err := rename(probe, logFile); err != nil {
		log.Printf("could not rename %s back to latest.log (%v): treating the game as possibly running", probe, err)
		return true
	}
	return false
}

// autoInstallDecision: install a channel's new pack by itself only when the pack really changed, the game jar is not
// already that pack, the game is closed and PLAY is not running. why is shown when it waits.
func autoInstallDecision(oldPack, newPack, jarSha string, gameOpen, busy bool) (install bool, why string) {
	switch {
	case newPack == "" || short(newPack) == short(oldPack):
		return false, ""
	case jarSha != "" && short(jarSha) == short(newPack):
		return false, ""
	case gameOpen:
		return false, "Pack " + short(newPack) + " is ready: close the game, then press PLAY to install it."
	case busy:
		return false, ""
	}
	return true, ""
}

// ---------- the Actions runs: what is coming and when ----------

type wfRun struct {
	ID         int64     `json:"id"`
	Status     string    `json:"status"`     // queued, in_progress, completed, ...
	Conclusion string    `json:"conclusion"` // success, failure, cancelled, skipped ... ("" until completed)
	HeadSha    string    `json:"head_sha"`
	Event      string    `json:"event"`
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

func (r wfRun) done() bool { return r.Status == "completed" }

func fetchRuns(g *ghClient, workflow string) ([]wfRun, error) {
	var v struct {
		Runs []wfRun `json:"workflow_runs"`
	}
	err := g.get("/repos/"+repoSlug+"/actions/workflows/"+workflow+"/runs?branch=main&per_page=15", &v)
	return v.Runs, err
}

// typicalDuration: the median time from a run's creation (queue included: that is what a tester waits for) to its end,
// over the successful runs in the list; fallback when there is none.
func typicalDuration(runs []wfRun, fallback time.Duration) time.Duration {
	var d []time.Duration
	for _, r := range runs {
		if r.done() && r.Conclusion == "success" && r.UpdatedAt.After(r.CreatedAt) {
			d = append(d, r.UpdatedAt.Sub(r.CreatedAt))
		}
	}
	if len(d) == 0 {
		return fallback
	}
	sort.Slice(d, func(i, j int) bool { return d[i] < d[j] })
	if len(d)%2 == 1 {
		return d[len(d)/2]
	}
	return (d[len(d)/2-1] + d[len(d)/2]) / 2
}

// pendingUpdate is the newest merge on its way to the channel's pack.
type pendingUpdate struct {
	Sha       string    // main commit being built
	ETA       time.Time // when its pack should land
	Stage     string    // "building", "packing" ...
	Done      bool      // every run finished green: the pack commit should be there now
	NotComing string    // non-empty: this merge will not reach the channel (why)
}

// pendingFor works out the newest merge's progress towards the channel's pack. runs[i] is the run list of
// ch.Workflows[i]; nil = not fetched (its typical duration is still counted). typical[i] is that workflow's typical
// duration. ok false: no run to track (no runs, or the newest is already the channel's pack).
func pendingFor(ch channel, packSha string, runs [][]wfRun, typical []time.Duration, now time.Time) (p pendingUpdate, ok bool) {
	if len(runs) == 0 {
		return p, false
	}
	// stage 0: the newest run started by a merge (push) or by hand on main
	first := newestTrigger(runs[0])
	if first == nil || (packSha != "" && short(first.HeadSha) == short(packSha)) {
		return p, false
	}
	p.Sha = short(first.HeadSha)
	rest := func(from int) time.Duration {
		var d time.Duration
		for i := from; i < len(typical); i++ {
			d += typical[i]
		}
		return d
	}
	prev := *first
	for i := 0; i < len(ch.Workflows); i++ {
		stage := stageName(ch, i)
		var cur *wfRun
		if i == 0 {
			cur = first
		} else if i < len(runs) && runs[i] != nil {
			// the run the previous one triggered: the oldest one created after it ended (2 min of clock slack)
			for j := range runs[i] {
				r := &runs[i][j]
				if !r.CreatedAt.Before(prev.UpdatedAt.Add(-2*time.Minute)) && (cur == nil || r.CreatedAt.Before(cur.CreatedAt)) {
					cur = r
				}
			}
		}
		if cur == nil { // not started yet (or not fetched): it starts about now
			p.Stage, p.ETA = stage, clampETA(now.Add(rest(i)), now)
			return p, true
		}
		if !cur.done() {
			p.Stage, p.ETA = stage, clampETA(cur.CreatedAt.Add(rest(i)), now)
			return p, true
		}
		if cur.Conclusion != "success" {
			p.Stage = stage
			p.NotComing = stage + " " + cur.Conclusion
			return p, true
		}
		prev = *cur
	}
	p.Done, p.ETA, p.Stage = true, now, "published"
	return p, true
}

// newestTrigger: the newest run started by a merge (push) or by hand on main.
func newestTrigger(runs []wfRun) *wfRun {
	var first *wfRun
	for i := range runs {
		r := &runs[i]
		if (r.Event == "push" || r.Event == "workflow_dispatch") && (first == nil || r.CreatedAt.After(first.CreatedAt)) {
			first = r
		}
	}
	return first
}

func stageName(ch channel, i int) string {
	if ch.ID == channelTested && i == 0 {
		return "build and in-game tests"
	}
	if ch.ID == channelTested {
		return "packing"
	}
	return "build and pack"
}

// clampETA: a run slower than usual is "any minute now", never a time in the past.
func clampETA(eta, now time.Time) time.Time {
	if eta.Before(now) {
		return now
	}
	return eta
}

// etaMinutes: whole minutes left, rounded up; 0 = any minute now.
func etaMinutes(eta, now time.Time) int {
	if !eta.After(now) {
		return 0
	}
	return int(math.Ceil(eta.Sub(now).Minutes()))
}

// waitText is the status line's tail while a merge is on its way to the channel.
func waitText(ch channel, w *pendingUpdate, now time.Time) string {
	switch {
	case w == nil:
		return ""
	case w.NotComing != "":
		return " · main " + w.Sha + " won't reach this channel (" + w.NotComing + ")"
	case w.Done:
		return " · update " + w.Sha + " landing now"
	}
	if m := etaMinutes(w.ETA, now); m > 0 {
		return fmt.Sprintf(" · Update coming in ~%d min (%s of %s)", m, w.Stage, w.Sha)
	}
	return " · Update coming any minute now (" + w.Stage + " of " + w.Sha + ")"
}

// channelStatus is the status line for a channel: its name first, then the pack sha, then what is coming.
func channelStatus(ch channel, jarSha, mainSha string, pack packHead, err error, w *pendingUpdate, now time.Time) (string, string) {
	ahead := " · main has newer merges still being packed (" + ch.Usually + ")"
	if w != nil {
		ahead = waitText(ch, w, now)
	}
	text, level := statusLineAhead(jarSha, mainSha, pack, err, ahead)
	if pack.Sha == "" && err == nil && ch.ID == channelFast {
		text = "No untested pack published yet: pick \"" + channels[0].Label + "\""
	}
	return ch.Label + " · " + text, level
}

// ---------- the watcher ----------

type updateWatcher struct {
	gh      *ghClient
	mu      sync.Mutex
	typical map[string]time.Duration // per workflow, measured once
	// start of the current GitHub rate-limit hour (the limit is per hour; PR #267 review finding 4)
	hourStart time.Time
}

// newHour gives the watcher a fresh call budget once an hour has passed. True when it did.
func (w *updateWatcher) newHour(now time.Time) bool {
	if w.hourStart.IsZero() || now.Sub(w.hourStart) >= time.Hour {
		w.hourStart, w.gh.calls = now, 0
		return true
	}
	return false
}

func newUpdateWatcher(gh *ghClient) *updateWatcher {
	return &updateWatcher{gh: gh, typical: map[string]time.Duration{}}
}

// check fetches the runs it needs (stage 0 always; a later stage only once its predecessor finished, or once to
// measure its typical duration) and, when every run finished, the channel's pack head.
func (w *updateWatcher) check(ch channel, packSha string, now time.Time) (p pendingUpdate, ok bool, head packHead, headErr error, err error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	runs := make([][]wfRun, len(ch.Workflows))
	typical := make([]time.Duration, len(ch.Workflows))
	for i, wf := range ch.Workflows {
		t, measured := w.typical[wf]
		need := i == 0 || !measured
		if f := newestTrigger(runs[0]); i > 0 && f != nil && f.done() && f.Conclusion == "success" {
			need = true // the first stage finished green: the later stages' runs matter now
		}
		if need {
			r, e := fetchRuns(w.gh, wf)
			if e != nil {
				return p, false, head, nil, e
			}
			runs[i] = r
			t = typicalDuration(r, ch.Fallback[i])
			w.typical[wf] = t
		}
		typical[i] = t
	}
	p, ok = pendingFor(ch, packSha, runs, typical, now)
	if ok && p.Done {
		head, headErr = fetchPackHead(w.gh, ch)
		if headErr == nil && short(head.Sha) == short(packSha) {
			// every run is green but the pack did not move: nothing testers download changed (publish-pack's skip)
			p.Done, p.NotComing = false, "nothing new to download in it"
		}
	}
	return p, ok, head, headErr, nil
}
