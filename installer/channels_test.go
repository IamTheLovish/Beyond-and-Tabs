package main

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestChannelURLSelection(t *testing.T) {
	tested, _ := channelByID(channelTested)
	if tested.PackTomlURL() != packTomlURL {
		t.Fatalf("the tested channel must be the URL the installer writes: %q vs %q", tested.PackTomlURL(), packTomlURL)
	}
	fast, ok := channelByID(channelFast)
	if !ok || fast.PackTomlURL() != "https://raw.githubusercontent.com/"+packRepoSlug+"/fast/windows/pack.toml" {
		t.Fatalf("fast channel URL: %q ok=%v", fast.PackTomlURL(), ok)
	}
	for _, id := range []string{"", "nightly-from-a-newer-launcher"} {
		if c := selectedChannel(id); c.ID != channelTested {
			t.Fatalf("%q must fall back to the tested channel, got %q", id, c.ID)
		}
	}
	if channels[0].ID != channelTested || channels[0].Label != "Latest - tested" || channels[1].Label != "Newest - untested" {
		t.Fatalf("menu order/labels: %+v", channels)
	}
	p, err := parsePackCommit("Fast pack 2026.10.26-2310-abc1234\n\nuntested")
	if err != nil || p.Sha != "abc1234" {
		t.Fatalf("fast pack commit: %+v %v", p, err)
	}
}

func TestStateSaveLoadKeepsChannel(t *testing.T) {
	path := filepath.Join(t.TempDir(), stateFileName)
	if s := readState(path); s.Channel != "" {
		t.Fatalf("no file: empty state, got %+v", s)
	}
	at := time.Date(2026, 10, 26, 20, 0, 0, 0, time.UTC)
	if err := writeState(path, launcherState{LastPlayedSha: "abc1234", LastPlayedAt: at, Channel: channelFast}); err != nil {
		t.Fatal(err)
	}
	s := readState(path)
	if s.Channel != channelFast || s.LastPlayedSha != "abc1234" || !s.LastPlayedAt.Equal(at) {
		t.Fatalf("round trip: %+v", s)
	}
	// a state file from the launcher before channels has no "channel": tested
	if err := os.WriteFile(path, []byte(`{"lastPlayedSha":"abc1234","lastPlayedAt":"2026-10-26T20:00:00Z"}`), 0o644); err != nil {
		t.Fatal(err)
	}
	if c := selectedChannel(readState(path).Channel); c.ID != channelTested {
		t.Fatalf("old state file: %q", c.ID)
	}
}

func TestSetPackURLRewritesOnlyTheURL(t *testing.T) {
	cfg := filepath.Join(t.TempDir(), "instance.cfg")
	fast, _ := channelByID(channelFast)
	orig := "InstanceType=OneSix\r\nJavaPath=C:/Java/bin/javaw.exe\r\nPreLaunchCommand=\\\"$INST_JAVA\\\" -jar packwiz-installer-bootstrap.jar " + packTomlURL + "\r\nname=BeyondAndTabs-test\r\n"
	if err := os.WriteFile(cfg, []byte(orig), 0o644); err != nil {
		t.Fatal(err)
	}
	changed, err := setPackURL(cfg, fast.PackTomlURL())
	got, _ := os.ReadFile(cfg)
	if err != nil || !changed || string(got) != strings.Replace(orig, packTomlURL, fast.PackTomlURL(), 1) {
		t.Fatalf("changed=%v err=%v\n%q", changed, err, got)
	}
	if changed, err := setPackURL(cfg, fast.PackTomlURL()); err != nil || changed {
		t.Fatalf("same URL must write nothing: changed=%v err=%v", changed, err)
	}
	if _, err := os.Stat(cfg + ".new"); !os.IsNotExist(err) {
		t.Fatal("the temp file must not stay behind")
	}
	if err := os.WriteFile(cfg, []byte("PreLaunchCommand=echo hi\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := setPackURL(cfg, fast.PackTomlURL()); err == nil {
		t.Fatal("a pre-launch command with no pack URL must fail loud")
	}
	if _, err := setPackURL(filepath.Join(t.TempDir(), "missing.cfg"), fast.PackTomlURL()); err == nil {
		t.Fatal("a missing instance.cfg must fail loud")
	}
}

var t0 = time.Date(2026, 10, 26, 20, 0, 0, 0, time.UTC)

func mkRun(event, sha, status, concl string, created time.Time, dur time.Duration) wfRun {
	return wfRun{Event: event, HeadSha: sha, Status: status, Conclusion: concl, CreatedAt: created, UpdatedAt: created.Add(dur)}
}

func TestETAFromRecentRuns(t *testing.T) {
	// median of the successful runs only: 8, 10, 12 min (a failure and an in-progress run are not durations)
	hist := []wfRun{
		mkRun("push", "a1", "completed", "success", t0.Add(-5*time.Hour), 8*time.Minute),
		mkRun("push", "a2", "completed", "success", t0.Add(-4*time.Hour), 12*time.Minute),
		mkRun("push", "a3", "completed", "failure", t0.Add(-3*time.Hour), 2*time.Minute),
		mkRun("push", "a4", "completed", "success", t0.Add(-2*time.Hour), 10*time.Minute),
	}
	if d := typicalDuration(hist, time.Hour); d != 10*time.Minute {
		t.Fatalf("median: %v", d)
	}
	if d := typicalDuration(hist[2:3], 7*time.Minute); d != 7*time.Minute {
		t.Fatalf("no success: fallback, got %v", d)
	}
	fast, _ := channelByID(channelFast)
	// a merge 3 min ago is building: ~7 min left of a typical 10
	now := t0
	runs := append([]wfRun{mkRun("push", "bbbbbbb1", "in_progress", "", now.Add(-3*time.Minute), 3*time.Minute)}, hist...)
	p, ok := pendingFor(fast, "a4", [][]wfRun{runs}, []time.Duration{10 * time.Minute}, now)
	if !ok || p.Sha != "bbbbbbb" || p.Done || p.NotComing != "" || etaMinutes(p.ETA, now) != 7 {
		t.Fatalf("fast in progress: %+v ok=%v mins=%d", p, ok, etaMinutes(p.ETA, now))
	}
	// slower than usual: any minute now, never a time in the past
	p, _ = pendingFor(fast, "a4", [][]wfRun{runs}, []time.Duration{2 * time.Minute}, now)
	if etaMinutes(p.ETA, now) != 0 {
		t.Fatalf("overdue run: %+v", p)
	}
	// the newest run is the channel's pack already: nothing to wait for (docs-only merges start no fast run)
	if _, ok := pendingFor(fast, "a4", [][]wfRun{hist}, []time.Duration{10 * time.Minute}, now); ok {
		t.Fatal("nothing newer than the pack: no wait")
	}
	// tested: build green 1 min ago, publish-pack not seen yet -> its typical 6 min
	tested, _ := channelByID(channelTested)
	build := []wfRun{mkRun("push", "ccccccc", "completed", "success", now.Add(-15*time.Minute), 14*time.Minute)}
	p, ok = pendingFor(tested, "a4", [][]wfRun{build, {}}, []time.Duration{14 * time.Minute, 6 * time.Minute}, now)
	if !ok || p.Stage != "packing" || etaMinutes(p.ETA, now) != 6 {
		t.Fatalf("tested, packing next: %+v mins=%d", p, etaMinutes(p.ETA, now))
	}
	// tested: build running for 4 min of 14, then 6 of packing -> 16
	building := []wfRun{mkRun("push", "ccccccc", "in_progress", "", now.Add(-4*time.Minute), 4*time.Minute)}
	p, _ = pendingFor(tested, "a4", [][]wfRun{building, nil}, []time.Duration{14 * time.Minute, 6 * time.Minute}, now)
	if etaMinutes(p.ETA, now) != 16 || p.Stage != "build and in-game tests" {
		t.Fatalf("tested, building: %+v mins=%d", p, etaMinutes(p.ETA, now))
	}
	// tested: publish ran green after the build: done
	pub := []wfRun{mkRun("workflow_run", "ccccccc", "completed", "success", now.Add(-50*time.Second), 40*time.Second)}
	if p, _ = pendingFor(tested, "a4", [][]wfRun{build, pub}, []time.Duration{14 * time.Minute, 6 * time.Minute}, now); !p.Done {
		t.Fatalf("tested, published: %+v", p)
	}
	// a red build never arrives
	red := []wfRun{mkRun("push", "ccccccc", "completed", "failure", now.Add(-15*time.Minute), 14*time.Minute)}
	if p, _ = pendingFor(tested, "a4", [][]wfRun{red, nil}, []time.Duration{14 * time.Minute, 6 * time.Minute}, now); p.NotComing != "build and in-game tests failure" {
		t.Fatalf("red build: %+v", p)
	}
}

func TestUpdateComingStatusText(t *testing.T) {
	tested, _ := channelByID(channelTested)
	fast, _ := channelByID(channelFast)
	pack := packHead{Sha: "f5775f8", Date: t0}
	w := &pendingUpdate{Sha: "1234567", ETA: t0.Add(7 * time.Minute), Stage: "build and pack"}
	text, level := channelStatus(fast, "f5775f8", "1234567abc", pack, nil, w, t0)
	if level != "ok" || text != "Newest - untested · Up to date: pack f5775f8 (2026-10-26 20:00 UTC) · Update coming in ~7 min (build and pack of 1234567)" {
		t.Fatalf("coming: %q %q", text, level)
	}
	text, _ = channelStatus(fast, "f5775f8", "1234567abc", pack, nil, &pendingUpdate{Sha: "1234567", ETA: t0, Stage: "build and pack"}, t0)
	if !strings.HasSuffix(text, "Update coming any minute now (build and pack of 1234567)") {
		t.Fatalf("overdue: %q", text)
	}
	text, _ = channelStatus(tested, "f5775f8", "1234567abc", pack, nil, &pendingUpdate{Sha: "1234567", NotComing: "build and in-game tests failure"}, t0)
	if text != "Latest - tested · Up to date: pack f5775f8 (2026-10-26 20:00 UTC) · main 1234567 won't reach this channel (build and in-game tests failure)" {
		t.Fatalf("not coming: %q", text)
	}
	// no run tracked: the channel's usual delay
	text, _ = channelStatus(fast, "aaaaaaa", "1234567abc", pack, nil, nil, t0)
	if text != "Newest - untested · Update ready: pack f5775f8 - press PLAY to install it · main has newer merges still being packed (usually ~5 min)" {
		t.Fatalf("no wait: %q", text)
	}
	text, _ = channelStatus(fast, "aaaaaaa", "1234567abc", packHead{}, nil, nil, t0)
	if !strings.HasPrefix(text, "Newest - untested · No untested pack published yet") {
		t.Fatalf("no fast pack yet: %q", text)
	}
}

func TestAutoInstallDecision(t *testing.T) {
	for _, c := range []struct {
		old, new, jar string
		open, busy    bool
		install       bool
		whyHas        string
	}{
		{"aaaaaaa", "bbbbbbb", "aaaaaaa", false, false, true, ""},
		{"aaaaaaa", "aaaaaaa", "1111111", false, false, false, ""},              // the pack did not move
		{"aaaaaaa", "", "aaaaaaa", false, false, false, ""},                     // unknown
		{"aaaaaaa", "bbbbbbb", "bbbbbbb", false, false, false, ""},              // already installed
		{"aaaaaaa", "bbbbbbb", "aaaaaaa", true, false, false, "close the game"}, // never under a running game
		{"aaaaaaa", "bbbbbbb", "aaaaaaa", false, true, false, ""},               // PLAY is installing it anyway
		{"aaaaaaa", "bbbbbbbcccc", "", false, false, true, ""},                  // no game yet: install
	} {
		install, why := autoInstallDecision(c.old, c.new, c.jar, c.open, c.busy)
		if install != c.install || (c.whyHas != "" && !strings.Contains(why, c.whyHas)) {
			t.Fatalf("%+v: install=%v why=%q", c, install, why)
		}
	}
}

// fakeActions: one green fast run for main's newest merge and the fast pack that it published.
func fakeActions(t *testing.T, newPack string) *httptest.Server {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch p := r.URL.Path; {
		case strings.HasSuffix(p, "/actions/workflows/publish-fast-pack.yml/runs"):
			w.Write([]byte(`{"workflow_runs":[{"id":2,"status":"completed","conclusion":"success","head_sha":"` + newPack + `00","event":"push","created_at":"2026-10-26T19:50:00Z","updated_at":"2026-10-26T19:56:00Z"}]}`))
		case strings.HasSuffix(p, "/"+packRepoSlug+"/commits/fast"):
			w.Write([]byte(`{"sha":"x","commit":{"message":"Fast pack 2026.10.26-1956-` + newPack + `"}}`))
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}

func newChannelTestLauncher(t *testing.T, fake *fakePackwiz) (*launcher, gamePaths, *int) {
	p := newGamePaths(t.TempDir())
	inst := filepath.Join(p.prismDir, "instances", instanceName)
	for _, d := range []string{inst, filepath.Join(p.mcDir, "mods"), p.root} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
	}
	os.WriteFile(p.prismExe, nil, 0o644)
	cfg := "JavaPath=C:\\Java\\bin\\javaw.exe\nPreLaunchCommand=\\\"$INST_JAVA\\\" -jar packwiz-installer-bootstrap.jar " + packTomlURL + "\n"
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte(cfg), 0o644); err != nil {
		t.Fatal(err)
	}
	os.WriteFile(filepath.Join(p.mcDir, "mods", "beyondandtabs-2026.10.25-2235-aaaaaaa.jar"), nil, 0o644)
	launched := 0
	l := newLauncher(p, &ghClient{base: "http://127.0.0.1:1", client: &http.Client{}})
	l.once.Do(func() {})
	l.state = pageState{PackSha: "aaaaaaa", MainSha: "ccccccc00", Channel: channelTested}
	l.live = liveState{packs: map[string]packHead{channelTested: {Sha: "aaaaaaa"}, channelFast: {Sha: "aaaaaaa"}}}
	close(l.ready)
	l.packwiz = fake
	l.launchPrism = func() error { launched++; return nil }
	l.gameOpen = gameOpenFunc(func() bool { return false })
	l.updateWait = time.Millisecond
	return l, p, &launched
}

func TestSwitchChannelThenPlayUsesItsPack(t *testing.T) {
	fake := &fakePackwiz{install: "beyondandtabs-2026.10.26-1956-bbbbbbb.jar"}
	l, p, launched := newChannelTestLauncher(t, fake)
	l.live.packs[channelFast] = packHead{Sha: "bbbbbbb"}
	l.updateFailed = true
	if err := l.setChannel(channelFast); err != nil {
		t.Fatal(err)
	}
	if st := readState(p.state); st.Channel != channelFast {
		t.Fatalf("the choice must be saved: %+v", st)
	}
	if v := l.view(t0); v.Channel != channelFast || v.PackSha != "bbbbbbb" || !strings.HasPrefix(v.Status, "Newest - untested · Update ready: pack bbbbbbb") {
		t.Fatalf("view after switch: %+v", v)
	}
	if l.updateFailed {
		t.Fatal("a switch must clear the other channel's failed check")
	}
	if err := l.setChannel("bogus"); err == nil {
		t.Fatal("unknown channel must be refused")
	}
	res, err := l.play()
	fast, _ := channelByID(channelFast)
	if err != nil || !res.Launched || *launched != 1 || fake.args[len(fake.args)-1] != fast.PackTomlURL() {
		t.Fatalf("play on fast: res=%+v err=%v args=%q", res, err, fake.args)
	}
	cfg, _ := os.ReadFile(instanceCfgPath(p))
	if !strings.Contains(string(cfg), fast.PackTomlURL()) || strings.Contains(string(cfg), packTomlURL) {
		t.Fatalf("Prism's own pre-launch update must use the chosen channel too:\n%s", cfg)
	}
	if st := readState(p.state); st.Channel != channelFast || st.LastPlayedSha != "bbbbbbb" {
		t.Fatalf("PLAY must keep the channel in the state: %+v", st)
	}
}

func TestWatcherAutoInstallsButNeverLaunches(t *testing.T) {
	fake := &fakePackwiz{install: "beyondandtabs-2026.10.26-1956-bbbbbbb.jar", out: "Finished successfully!\n"}
	l, _, launched := newChannelTestLauncher(t, fake)
	l.state.Channel = channelFast
	srv := fakeActions(t, "bbbbbbb")
	l.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})
	if !l.pollUpdates(t0) {
		t.Fatal("the watcher should keep going")
	}
	if fake.calls != 1 || *launched != 0 {
		t.Fatalf("new pack: one install, no launch; calls=%d launched=%d", fake.calls, *launched)
	}
	v := l.view(t0)
	if v.PackSha != "bbbbbbb" || v.WaitMins != nil || !strings.Contains(v.Note, "Installed Newest - untested pack bbbbbbb") {
		t.Fatalf("after install: %+v", v)
	}
	// the pack does not move again: no second install
	l.pollUpdates(t0)
	if fake.calls != 1 {
		t.Fatalf("installed once only, got %d runs", fake.calls)
	}

	// the game is open: wait and say so, install nothing
	fake2 := &fakePackwiz{install: "beyondandtabs-2026.10.26-1956-bbbbbbb.jar"}
	l2, _, launched2 := newChannelTestLauncher(t, fake2)
	l2.state.Channel = channelFast
	l2.gameOpen = gameOpenFunc(func() bool { return true })
	l2.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})
	l2.pollUpdates(t0)
	if fake2.calls != 0 || *launched2 != 0 || !strings.Contains(l2.view(t0).Note, "close the game") {
		t.Fatalf("game open: calls=%d launched=%d note=%q", fake2.calls, *launched2, l2.view(t0).Note)
	}

	// out of GitHub budget: say so, and keep the watcher alive for the hourly reset (review C-10)
	l2.watcher.gh.calls = pollCallBudget
	if !l2.pollUpdates(t0) || !strings.Contains(l2.view(t0).Note, "Stopped checking") {
		t.Fatal("an exhausted budget must pause with a note, not end the watcher")
	}
}

func TestPageLoadReadsBothChannelsWithinBudget(t *testing.T) {
	srv, calls := fakeGitHub(t)
	r := fetchGitHub(&ghClient{base: srv.URL, client: srv.Client()}, "aaaaaaa")
	if *calls > ghCallBudget || r.Packs[channelTested].Sha != "f5775f8" || r.PackErrs[channelFast] == nil {
		t.Fatalf("calls=%d packs=%+v errs=%+v", *calls, r.Packs, r.PackErrs)
	}
	// no fast branch yet (404): the fast channel says so instead of "couldn't check GitHub"
	p := newGamePaths(t.TempDir())
	ps := withStatus(pageState{Channel: channelFast, MainSha: r.MainSha}, p, liveState{packs: r.Packs, packErrs: r.PackErrs}, t0)
	if !strings.Contains(ps.Status, "No untested pack published yet") {
		t.Fatalf("fast without a branch: %q", ps.Status)
	}
}

// failingGitHub: every call is an HTTP 500 (GitHub down, no network proxy, ...).
func failingGitHub(t *testing.T) *httptest.Server {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { http.Error(w, "down", 500) }))
	t.Cleanup(srv.Close)
	return srv
}

// PR #267 review finding 6: a network error must not stop the watcher, install anything or start the game.
func TestWatcherNetworkErrorKeepsWatching(t *testing.T) {
	fake := &fakePackwiz{install: "beyondandtabs-2026.10.26-1956-bbbbbbb.jar"}
	l, _, launched := newChannelTestLauncher(t, fake)
	l.state.Channel = channelFast
	srv := failingGitHub(t)
	l.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})
	for i := 0; i < 2; i++ {
		if !l.pollUpdates(t0) {
			t.Fatal("a network error must not stop the watcher: the next poll may work")
		}
	}
	if note := l.view(t0).Note; !strings.Contains(note, "Couldn't check for a coming update") {
		t.Fatalf("the error must be shown: %q", note)
	}
	if fake.calls != 0 || *launched != 0 {
		t.Fatalf("no install, no launch on an error: calls=%d launched=%d", fake.calls, *launched)
	}
}

// PR #267 review finding 3: "Update coming" from an earlier poll must not stay (PLAY disabled) once a poll fails.
func TestWatcherNetworkErrorClearsWait(t *testing.T) {
	l, _, _ := newChannelTestLauncher(t, &fakePackwiz{})
	l.state.Channel = channelFast
	srv := failingGitHub(t)
	l.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})
	l.live.wait = &pendingUpdate{Sha: "ccccccc", ETA: t0, Stage: "build and pack"}
	l.pollUpdates(t0)
	if v := l.view(t0); v.WaitMins != nil || strings.Contains(v.Status, "any minute now") {
		t.Fatalf("a failed poll must clear the stale wait: %+v", v)
	}
}

// PR #267 review finding 4: the call budget is per hour, so the watcher must count hours, not stop for good.
func TestWatcherBudgetResetsEveryHour(t *testing.T) {
	l, _, _ := newChannelTestLauncher(t, &fakePackwiz{})
	l.state.Channel = channelFast
	srv := fakeActions(t, "aaaaaaa")
	l.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})
	l.pollUpdates(t0) // starts the hour
	l.watcher.gh.calls = pollCallBudget
	if !l.pollUpdates(t0.Add(30*time.Minute)) || !strings.Contains(l.view(t0).Note, "Stopped checking") {
		t.Fatal("budget used up inside the hour: say so, but keep the watcher alive (watchUpdates exits on false and the hour would never reset; review C-10)")
	}
	if !l.pollUpdates(t0.Add(61 * time.Minute)) {
		t.Fatal("an hour later the budget is new: keep watching")
	}
	if l.watcher.gh.calls >= pollCallBudget-3 || strings.Contains(l.view(t0).Note, "Stopped checking") {
		t.Fatalf("calls=%d note=%q", l.watcher.gh.calls, l.view(t0).Note)
	}
}

// PR #267 review finding 5: if the rename-back fails, latest.log stays renamed and the game may be running - say
// "open" (no auto-install) instead of ignoring it. (The real Windows lock itself can only be tested on a Windows
// install with the game open.)
func TestLogLockProbe(t *testing.T) {
	dir := t.TempDir()
	if (logLockProbe{mcDir: dir}).GameOpen() {
		t.Fatal("no log: not open")
	}
	logPath := filepath.Join(dir, "logs", "latest.log")
	os.MkdirAll(filepath.Dir(logPath), 0o755)
	os.WriteFile(logPath, []byte("x"), 0o644)
	if (logLockProbe{mcDir: dir}).GameOpen() || !exists(logPath) {
		t.Fatal("free log: not open, and the log is back under its name")
	}
	n := 0
	locked := logLockProbe{mcDir: dir, rename: func(a, b string) error { n++; return errors.New("in use") }}
	if !locked.GameOpen() || n != 1 {
		t.Fatalf("rename refused = the game holds the log: open (renames=%d)", n)
	}
	n = 0
	backFails := logLockProbe{mcDir: dir, rename: func(a, b string) error {
		n++
		if n == 2 {
			return errors.New("access denied")
		}
		return os.Rename(a, b)
	}}
	if !backFails.GameOpen() {
		t.Fatal("rename-back failed: treat the game as possibly running")
	}
}

// blockingPackwiz: an update that stays "running" until release is closed, counting runs (safe across goroutines).
type blockingPackwiz struct {
	mu       sync.Mutex
	calls    int
	args     []string
	started  chan struct{}
	release  chan struct{}
	install  string
	startedO sync.Once
}

func (b *blockingPackwiz) Run(dir, exe string, args []string) (string, error) {
	b.mu.Lock()
	b.calls++
	b.args = args
	b.mu.Unlock()
	b.startedO.Do(func() { close(b.started) })
	<-b.release
	if err := os.WriteFile(filepath.Join(dir, "mods", b.install), nil, 0o644); err != nil {
		return "", err
	}
	return "Finished successfully!\n", nil
}

// PR #267 review finding 6: switching the channel while PLAY (install + launch) is running must be refused with a
// clear note, start no second install or launch, and leave the saved state and the instance on one channel.
func TestSetChannelWhilePlayRunningIsRefused(t *testing.T) {
	fake := &blockingPackwiz{started: make(chan struct{}), release: make(chan struct{}), install: "beyondandtabs-2026.10.25-2235-aaaaaaa.jar"}
	l, p, launched := newChannelTestLauncher(t, &fakePackwiz{})
	l.packwiz = fake
	srv := fakeActions(t, "bbbbbbb")
	l.watcher = newUpdateWatcher(&ghClient{base: srv.URL, client: srv.Client(), budget: pollCallBudget})

	type playOut struct {
		res playResult
		err error
	}
	done := make(chan playOut, 1)
	go func() {
		res, err := l.play()
		done <- playOut{res, err}
	}()
	<-fake.started // PLAY holds the install

	// the channel switch (twice, from two goroutines) and a watcher install attempt all arrive during the install
	var wg sync.WaitGroup
	errs := make([]error, 2)
	for i := range errs {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			errs[i] = l.setChannel(channelFast)
		}(i)
	}
	wg.Add(1)
	go func() {
		defer wg.Done()
		l.autoInstall(channels[1], "aaaaaaa", "bbbbbbb")
	}()
	wg.Wait()
	for i, err := range errs {
		if err == nil || !strings.Contains(err.Error(), "being updated") {
			t.Fatalf("switch %d during PLAY must be refused with a clear note, got %v", i, err)
		}
	}
	if st := readState(p.state); st.Channel != "" {
		t.Fatalf("a refused switch must not save anything: %+v", st)
	}
	if c := l.view(t0).Channel; c != channelTested {
		t.Fatalf("page still shows the old channel, got %q", c)
	}

	close(fake.release)
	out := <-done
	if out.err != nil || !out.res.Launched {
		t.Fatalf("PLAY must finish and launch: %+v %v", out.res, out.err)
	}
	fake.mu.Lock()
	calls, last := fake.calls, fake.args[len(fake.args)-1]
	fake.mu.Unlock()
	if calls != 1 || *launched != 1 {
		t.Fatalf("exactly one install and one launch: installs=%d launches=%d", calls, *launched)
	}
	if last != packTomlURL {
		t.Fatalf("PLAY installed the tested pack it started with, got %q", last)
	}
	cfg, _ := os.ReadFile(instanceCfgPath(p))
	if !strings.Contains(string(cfg), packTomlURL) || strings.Contains(string(cfg), "/fast/") {
		t.Fatalf("instance.cfg must stay on the tested pack:\n%s", cfg)
	}
	if st := readState(p.state); st.Channel != "" || st.LastPlayedSha != "aaaaaaa" {
		t.Fatalf("state must be the finished PLAY's: %+v", st)
	}
	// after PLAY the switch works
	if err := l.setChannel(channelFast); err != nil {
		t.Fatalf("switch after PLAY: %v", err)
	}
	if st := readState(p.state); st.Channel != channelFast || l.view(t0).Channel != channelFast {
		t.Fatalf("switch after PLAY must save: %+v", st)
	}
}
