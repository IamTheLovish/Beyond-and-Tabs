package main

import (
	"bytes"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// fakeGPUStore stands in for the registry: one value per exe.
type fakeGPUStore struct {
	vals   map[string]string
	getErr error
	setErr error
	sets   int
}

func (f *fakeGPUStore) Get(exe string) (string, bool, error) {
	if f.getErr != nil {
		return "", false, f.getErr
	}
	v, ok := f.vals[exe]
	return v, ok, nil
}

func (f *fakeGPUStore) Set(exe, value string) error {
	if f.setErr != nil {
		return f.setErr
	}
	f.sets++
	f.vals[exe] = value
	return nil
}

const testJavaExe = `C:\Users\a\AppData\Local\BeyondAndTabs\PrismLauncher\java\java-runtime-gamma\bin\javaw.exe`

func TestGPUPrefNoValueWritesHighPerformance(t *testing.T) {
	s := &fakeGPUStore{vals: map[string]string{}}
	wrote, err := decideGPUPref(s, testJavaExe)
	if err != nil || !wrote || s.vals[testJavaExe] != "GpuPreference=2;" || s.sets != 1 {
		t.Fatalf("wrote=%v err=%v vals=%v sets=%d", wrote, err, s.vals, s.sets)
	}
}

func TestGPUPrefExistingChoiceUntouched(t *testing.T) {
	s := &fakeGPUStore{vals: map[string]string{testJavaExe: "GpuPreference=1;"}}
	wrote, err := decideGPUPref(s, testJavaExe)
	if err != nil || wrote || s.sets != 0 || s.vals[testJavaExe] != "GpuPreference=1;" {
		t.Fatalf("an existing choice must stay: wrote=%v err=%v vals=%v", wrote, err, s.vals)
	}
}

func TestGPUPrefValueWithOtherFieldsUntouched(t *testing.T) {
	const val = "GpuPreference=0;SomeOther=1;"
	s := &fakeGPUStore{vals: map[string]string{testJavaExe: val}}
	wrote, err := decideGPUPref(s, testJavaExe)
	if err != nil || wrote || s.sets != 0 || s.vals[testJavaExe] != val {
		t.Fatalf("a value with other fields must stay: wrote=%v err=%v vals=%v", wrote, err, s.vals)
	}
}

func TestGPUPrefWriteErrorSurfaced(t *testing.T) {
	s := &fakeGPUStore{vals: map[string]string{}, setErr: errors.New("access denied")}
	wrote, err := decideGPUPref(s, testJavaExe)
	if err == nil || wrote || !strings.Contains(err.Error(), "access denied") {
		t.Fatalf("a write error must be returned: wrote=%v err=%v", wrote, err)
	}
}

func TestGPUPrefReadErrorSurfaced(t *testing.T) {
	s := &fakeGPUStore{vals: map[string]string{}, getErr: errors.New("registry broken")}
	wrote, err := decideGPUPref(s, testJavaExe)
	if err == nil || wrote || s.sets != 0 || !strings.Contains(err.Error(), "registry broken") {
		t.Fatalf("a read error must be returned and nothing written: wrote=%v err=%v", wrote, err)
	}
}

func TestJavaExePrefersConfiguredPath(t *testing.T) {
	p := newGamePaths(t.TempDir())
	if got := javaExe(p); got != filepath.Join(p.prismDir, "java", "java-runtime-gamma", "bin", "javaw.exe") {
		t.Fatalf("no config: got %q", got)
	}
	inst := filepath.Join(p.prismDir, "instances", instanceName)
	if err := os.MkdirAll(inst, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte("InstanceType=OneSix\nJavaPath=C:\\Java\\bin\\javaw.exe\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if got := javaExe(p); got != `C:\Java\bin\javaw.exe` {
		t.Fatalf("instance.cfg JavaPath: got %q", got)
	}
	// Prism's own form: forward slashes; the GPU key needs the backslash path Windows matches
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte("JavaPath=C:/Users/J/AppData/Local/BeyondAndTabs/PrismLauncher/java/java-runtime-gamma/bin/javaw.exe\r\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if got := javaExe(p); got != `C:\Users\J\AppData\Local\BeyondAndTabs\PrismLauncher\java\java-runtime-gamma\bin\javaw.exe` {
		t.Fatalf("slash JavaPath not turned into a Windows path: got %q", got)
	}
}

func TestJarNameToday(t *testing.T) {
	b, ok := parseJarName("beyondandtabs-2026.10.25-2235-f5775f8.jar")
	if !ok {
		t.Fatal("today's jar name not recognised")
	}
	if b.Sha != "f5775f8" {
		t.Fatalf("sha = %q, want f5775f8", b.Sha)
	}
	if want := time.Date(2026, 10, 25, 22, 35, 0, 0, time.UTC); !b.Time.Equal(want) || b.Time.Location() != time.UTC {
		t.Fatalf("time = %v, want %v (UTC)", b.Time, want)
	}
}

func TestJarNameWithoutSha(t *testing.T) {
	b, ok := parseJarName("beyondandtabs-2026.10.25-2235.jar")
	if !ok || b.Sha != "" || b.Time.IsZero() {
		t.Fatalf("a jar with no sha should parse with an empty sha: %+v ok=%v", b, ok)
	}
	for _, bad := range []string{"alexsmobs-1.22.9.jar", "beyondandtabs-latest.jar", "beyondandtabs-2026.10.25-2235-f5775f8.jar.disabled"} {
		if _, ok := parseJarName(bad); ok {
			t.Fatalf("%q should not count as the game jar", bad)
		}
	}
}

func TestSeveralJarsPicksNewest(t *testing.T) {
	b, ok := pickBuild([]string{
		"citadel-2.6.3-1.20.1.jar",
		"beyondandtabs-2026.10.24-0910-aaaaaaa.jar",
		"beyondandtabs-2026.10.25-2235-f5775f8.jar",
		"beyondandtabs-2026.10.25-0100-bbbbbbb.jar",
	})
	if !ok || b.Sha != "f5775f8" || b.Jars != 3 {
		t.Fatalf("want newest f5775f8 of 3 game jars, got %+v ok=%v", b, ok)
	}
	if _, ok := pickBuild([]string{"citadel-2.6.3-1.20.1.jar"}); ok {
		t.Fatal("no game jar should report not found")
	}
}

func TestMergeCommitParsing(t *testing.T) {
	n, title, ok := parseMergeCommit("Merge pull request #107 from IamTheLovish/joisco/c1-area-mex\n\nDrag a circle to place metal extractors\n\nMore text")
	if !ok || n != 107 || title != "Drag a circle to place metal extractors" {
		t.Fatalf("got (#%d, %q, %v)", n, title, ok)
	}
	n, title, ok = parseMergeCommit("Merge pull request #5 from someone/fix-thing")
	if !ok || n != 5 || title != "fix-thing" {
		t.Fatalf("no body: got (#%d, %q, %v), want the branch as title", n, title, ok)
	}
	for _, msg := range []string{"Fix the kill feed", "Merge branch 'main' into x", ""} {
		if _, _, ok := parseMergeCommit(msg); ok {
			t.Fatalf("%q is not a PR merge", msg)
		}
	}
}

func TestParsePackCommit(t *testing.T) {
	p, err := parsePackCommit("Tester pack 2026.10.26-1949-23df346")
	if err != nil || p.Sha != "23df346" || !p.Date.Equal(time.Date(2026, 10, 26, 19, 49, 0, 0, time.UTC)) {
		t.Fatalf("got %+v err=%v", p, err)
	}
	p, err = parsePackCommit("Tester pack 2026.10.26-1949-23df346\n\nbody text")
	if err != nil || p.Sha != "23df346" {
		t.Fatalf("a body after the subject must be ignored: got %+v err=%v", p, err)
	}
	for _, msg := range []string{"Merge pull request #1 from a/b", "Tester pack 2026.10.26-1949", "Tester pack 2026.13.40-1949-23df346", ""} {
		if _, err := parsePackCommit(msg); err == nil {
			t.Fatalf("%q is not a tester pack commit", msg)
		}
	}
}

func TestStatusLine(t *testing.T) {
	pack, err := parsePackCommit("Tester pack 2026.10.25-2230-f5775f8")
	if err != nil {
		t.Fatal(err)
	}
	// equal: the jar is the newest pack and main has nothing newer
	text, level := statusLine("f5775f8", "f5775f8abcdef0123", pack, nil)
	if level != "ok" || text != "Up to date: pack f5775f8 (2026-10-25 22:30 UTC)" {
		t.Fatalf("equal: %q %q", text, level)
	}
	// behind: the jar is older than the newest pack
	text, level = statusLine("aaaaaaa", "f5775f8abcdef0123", pack, nil)
	if level != "update" || text != "Update ready: pack f5775f8 - press PLAY to install it" {
		t.Fatalf("behind: %q %q", text, level)
	}
	// main ahead of the newest pack: the jar is the newest pack, main's newer merges are still being packed
	text, level = statusLine("f5775f8", "0123456789abcdef", pack, nil)
	if level != "ok" || text != "Up to date: pack f5775f8 (2026-10-25 22:30 UTC) · main has newer merges still being packed (usually ~25 min)" {
		t.Fatalf("main ahead: %q %q", text, level)
	}
	// no jar yet
	text, level = statusLine("", "0123456789abcdef", pack, nil)
	if level != "update" || text != "Game files not found yet: PLAY installs the newest pack (f5775f8) · main has newer merges still being packed (usually ~25 min)" {
		t.Fatalf("no jar: %q %q", text, level)
	}
	text, level = statusLine("f5775f8", "", packHead{}, errors.New("dial tcp: no such host"))
	if level != "warn" || text != "Couldn't check GitHub (dial tcp: no such host)" {
		t.Fatalf("error: %q %q", text, level)
	}
	text, level = statusLine("f5775f8", "f5775f8", packHead{}, nil)
	if level != "warn" || text != "Couldn't read the newest tester pack from GitHub" {
		t.Fatalf("no pack: %q %q", text, level)
	}
}

func TestPlanAfterUpdate(t *testing.T) {
	p := planAfterUpdate("f5775f8", "f5775f8", "", false)
	if !p.Launch || p.Level != "ok" || p.Text != "Installed pack f5775f8." {
		t.Fatalf("match must launch: %+v", p)
	}
	p = planAfterUpdate("aaaaaaa", "f5775f8", "Could not resolve host", false)
	if p.Launch || p.Level != "warn" || p.Text != "Update did not install pack f5775f8 (you have aaaaaaa): Could not resolve host" {
		t.Fatalf("mismatch must warn and not launch: %+v", p)
	}
	p = planAfterUpdate("", "f5775f8", "no jar", false)
	if p.Launch || !strings.Contains(p.Text, "(you have no game jar)") {
		t.Fatalf("no jar must warn: %+v", p)
	}
	p = planAfterUpdate("aaaaaaa", "f5775f8", "Could not resolve host", true)
	if !p.Launch || p.Level != "warn" || !strings.HasPrefix(p.Text, "Launching anyway: you have aaaaaaa, not the newest pack f5775f8") {
		t.Fatalf("second press must launch, labelled: %+v", p)
	}
	p = planAfterUpdate("f5775f8", "", "", false)
	if !p.Launch || p.Level != "warn" {
		t.Fatalf("an unknown pack cannot be checked: launch with a warning: %+v", p)
	}
}

func TestSplitCommandForms(t *testing.T) {
	want := []string{"C:\\Program Files\\java\\javaw.exe", "-jar", "packwiz-installer-bootstrap.jar", "https://x/pack.toml"}
	for _, raw := range []string{
		`"C:\Program Files\java\javaw.exe" -jar packwiz-installer-bootstrap.jar https://x/pack.toml`,
		`\"C:\Program Files\java\javaw.exe\" -jar packwiz-installer-bootstrap.jar https://x/pack.toml`,
	} {
		got := splitCommand(raw)
		if strings.Join(got, "|") != strings.Join(want, "|") {
			t.Fatalf("%q: got %q", raw, got)
		}
	}
}

func TestPackwizCommandFromInstanceCfg(t *testing.T) {
	p := newGamePaths(t.TempDir())
	inst := filepath.Join(p.prismDir, "instances", instanceName)
	if err := os.MkdirAll(inst, 0o755); err != nil {
		t.Fatal(err)
	}
	cfg := "JavaPath=C:\\Java\\bin\\javaw.exe\nPreLaunchCommand=\\\"$INST_JAVA\\\" -jar packwiz-installer-bootstrap.jar " + packTomlURL + "\n"
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte(cfg), 0o644); err != nil {
		t.Fatal(err)
	}
	exe, args, err := packwizCommand(p)
	if err != nil || exe != `C:\Java\bin\javaw.exe` || strings.Join(args, "|") != "-jar|packwiz-installer-bootstrap.jar|"+packTomlURL {
		t.Fatalf("exe=%q args=%q err=%v", exe, args, err)
	}
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte("JavaPath=x\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, _, err := packwizCommand(p); err == nil {
		t.Fatal("a missing PreLaunchCommand must fail loud")
	}
}

// fakePackwiz records the update it was asked to run. install: the jar it leaves in mods (the update worked).
type fakePackwiz struct {
	calls   int
	dir     string
	exe     string
	args    []string
	install string
	out     string
	fromRun int // install only from this call on (a stale cache answered the earlier ones); 0 = every call
}

func (f *fakePackwiz) Run(dir, exe string, args []string) (string, error) {
	f.calls++
	f.dir, f.exe, f.args = dir, exe, args
	if f.install != "" && f.calls >= f.fromRun {
		if err := os.WriteFile(filepath.Join(dir, "mods", f.install), nil, 0o644); err != nil {
			return "", err
		}
	}
	return f.out, nil
}

func TestPlayUpdatesVerifiesAndLaunches(t *testing.T) {
	p := newGamePaths(t.TempDir())
	inst := filepath.Join(p.prismDir, "instances", instanceName)
	for _, d := range []string{inst, filepath.Join(p.mcDir, "mods"), p.root} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(p.prismExe, nil, 0o644); err != nil {
		t.Fatal(err)
	}
	cfg := "JavaPath=C:\\Java\\bin\\javaw.exe\nPreLaunchCommand=\\\"$INST_JAVA\\\" -jar packwiz-installer-bootstrap.jar " + packTomlURL + "\n"
	if err := os.WriteFile(filepath.Join(inst, "instance.cfg"), []byte(cfg), 0o644); err != nil {
		t.Fatal(err)
	}
	old := "beyondandtabs-2026.10.25-2235-aaaaaaa.jar"
	if err := os.WriteFile(filepath.Join(p.mcDir, "mods", old), nil, 0o644); err != nil {
		t.Fatal(err)
	}
	newJar := "beyondandtabs-2026.10.26-1949-f5775f8.jar"

	launched := 0
	newTestLauncher := func(fake *fakePackwiz) *launcher {
		l := newLauncher(p, &ghClient{base: "http://127.0.0.1:1", client: &http.Client{}})
		l.once.Do(func() {}) // no GitHub in this test: the state is set by hand
		l.state = pageState{PackSha: "f5775f8"}
		close(l.ready)
		l.packwiz = fake
		l.launchPrism = func() error { launched++; return nil }
		return l
	}

	// 1. the update leaves the old jar: nothing launches, the warning names both builds
	fake := &fakePackwiz{out: "Error: could not download pack.toml\n"}
	l := newTestLauncher(fake)
	res, err := l.play()
	if err != nil || res.Launched || res.Level != "warn" || launched != 0 {
		t.Fatalf("a failed check must not launch: res=%+v err=%v launched=%d", res, err, launched)
	}
	if res.Text != "Update did not install pack f5775f8 (you have aaaaaaa): Error: could not download pack.toml" {
		t.Fatalf("warning text: %q", res.Text)
	}
	if fake.calls != 1 || fake.dir != p.mcDir || fake.exe != `C:\Java\bin\javaw.exe` || strings.Join(fake.args, "|") != "-jar|packwiz-installer-bootstrap.jar|"+packTomlURL {
		t.Fatalf("update ran as dir=%q exe=%q args=%q calls=%d", fake.dir, fake.exe, fake.args, fake.calls)
	}
	if _, err := os.Stat(updateLogPath(p)); err != nil {
		t.Fatalf("the update output must be logged: %v", err)
	}

	// 2. a second press launches anyway and does not run the update again
	res, err = l.play()
	if err != nil || !res.Launched || launched != 1 || fake.calls != 1 || !strings.HasPrefix(res.Text, "Launching anyway") {
		t.Fatalf("second press: res=%+v err=%v launched=%d calls=%d", res, err, launched, fake.calls)
	}

	// 3. a new launcher whose update installs the newest pack: launches with the install reported
	fake2 := &fakePackwiz{install: newJar}
	l2 := newTestLauncher(fake2)
	res, err = l2.play()
	if err != nil || !res.Launched || res.Level != "ok" || launched != 2 || !strings.HasPrefix(res.Text, "Installed pack f5775f8.") {
		t.Fatalf("matching install: res=%+v err=%v launched=%d", res, err, launched)
	}
	if st := readState(p.state); st.LastPlayedSha != "f5775f8" {
		t.Fatalf("the played pack must be remembered: %+v", st)
	}

	// 4. just after a publish GitHub's cache serves the old pack.toml: packwiz says up to date twice, then installs.
	// PLAY retries the clean-but-stale run and launches; a failed run (case 1) is not retried
	if err := os.Remove(filepath.Join(p.mcDir, "mods", newJar)); err != nil {
		t.Fatal(err)
	}
	fake3 := &fakePackwiz{install: newJar, fromRun: 3, out: "(1/1) Modpack is already up to date!\nFinished successfully!\n"}
	l3 := newTestLauncher(fake3)
	l3.updateWait = time.Millisecond
	res, err = l3.play()
	if err != nil || !res.Launched || res.Level != "ok" || fake3.calls != 3 || launched != 3 {
		t.Fatalf("stale cache: res=%+v err=%v calls=%d launched=%d", res, err, fake3.calls, launched)
	}

	// 5. the cache never catches up: PLAY stops after updateTries runs and warns instead of launching the old jar
	if err := os.Remove(filepath.Join(p.mcDir, "mods", newJar)); err != nil {
		t.Fatal(err)
	}
	fake4 := &fakePackwiz{out: "Finished successfully!\n"}
	l4 := newTestLauncher(fake4)
	l4.updateWait = time.Millisecond
	res, err = l4.play()
	if err != nil || res.Launched || fake4.calls != l4.updateTries || launched != 3 {
		t.Fatalf("never updated: res=%+v err=%v calls=%d launched=%d", res, err, fake4.calls, launched)
	}
}

func TestShortcutOpensTheLauncher(t *testing.T) {
	ps := shortcutScript(`C:\B\launcher.exe`, `C:\B\PrismLauncher\prismlauncher.exe`, `C:\B\PrismLauncher`, `C:\B\PrismLauncher\lovish.ico`)
	for _, want := range []string{
		`$exe = 'C:\B\launcher.exe'`,
		`$s.TargetPath = $exe`,
		`$s.Arguments = '--launch'`,
		`$s.WorkingDirectory = 'C:\B\PrismLauncher'`,
		`$s.IconLocation = 'C:\B\PrismLauncher\lovish.ico,0'`,
		`$s.Description = 'Play ` + appName + `'`,
		// the shortcut earlier installers made (this Prism, -l "BeyondAndTabs-test") is repointed
		`(($ws.CreateShortcut($lnk).TargetPath -eq $prism) -and ($ws.CreateShortcut($lnk).Arguments -eq '-l "` + instanceName + `"'))`,
	} {
		if !strings.Contains(ps, want) {
			t.Fatalf("shortcut script lacks %q:\n%s", want, ps)
		}
	}
	if strings.Contains(ps, `$s.TargetPath = $prism`) {
		t.Fatal("shortcut still targets Prism directly")
	}
	if got := shortcutScript(`C:\Users\O'Neil\launcher.exe`, `p`, `d`, `i`); !strings.Contains(got, `'C:\Users\O''Neil\launcher.exe'`) {
		t.Fatalf("a ' in the path is not escaped:\n%s", got)
	}
}

func TestLaunchModeSelection(t *testing.T) {
	if !isLaunchMode([]string{"--launch"}, `C:\Downloads\BeyondAndTabs-Setup.exe`) {
		t.Fatal("--launch should open the launcher")
	}
	if !isLaunchMode(nil, `C:\Users\x\AppData\Local\BeyondAndTabs\Launcher.exe`) {
		t.Fatal("launcher.exe should open the launcher")
	}
	if isLaunchMode(nil, `C:\Downloads\BeyondAndTabs-Setup.exe`) {
		t.Fatal("a plain run must stay the installer")
	}
}

func TestStopReason(t *testing.T) {
	t0 := time.Date(2026, 10, 25, 12, 0, 0, 0, time.UTC)
	if r := stopReason(t0, t0.Add(10*time.Second), t0.Add(20*time.Second)); r != "" {
		t.Fatalf("a live window should keep the server: %q", r)
	}
	if r := stopReason(t0, t0.Add(10*time.Second), t0.Add(40*time.Second)); r == "" {
		t.Fatal("a lost heartbeat should stop the server")
	}
	if r := stopReason(t0, t0.Add(31*time.Minute), t0.Add(31*time.Minute)); r == "" {
		t.Fatal("the 30-minute safety timeout should stop the server")
	}
	if r := stopReason(t0, time.Time{}, t0.Add(4*time.Minute)); r == "" {
		t.Fatal("a window that never connects should stop the server")
	}
}

// fakeGitHub answers the three endpoints the launcher uses and counts calls.
func fakeGitHub(t *testing.T) (*httptest.Server, *int) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Header.Get("User-Agent") == "" {
			t.Error("GitHub call without a User-Agent")
		}
		p := r.URL.Path
		switch {
		case strings.Contains(p, "/"+packRepoSlug+"/commits/main"):
			w.Write([]byte(`{"sha":"ffff000000000000000000000000000000000000","commit":{"message":"Tester pack 2026.10.25-2230-f5775f8\n\nbuilt from main","committer":{"date":"2026-10-25T22:31:00Z"}}}`))
		case strings.HasSuffix(p, "/commits/main"):
			w.Write([]byte(`{"sha":"f5775f89abcdef","commit":{"message":"x","committer":{"date":"2026-10-25T22:30:00Z"}}}`))
		case strings.Contains(p, "/compare/"):
			w.Write([]byte(`{"commits":[
				{"sha":"1","commit":{"message":"Merge pull request #106 from a/b\n\nReturn-resources key"}},
				{"sha":"2","commit":{"message":"plain commit"}},
				{"sha":"3","commit":{"message":"Merge pull request #107 from a/c\n\nDrag a circle"}}]}`))
		case strings.HasSuffix(p, "/pulls") && r.URL.Query().Get("state") == "closed":
			w.Write([]byte(`[{"number":1,"title":"closed unmerged","merged_at":null},
				{"number":2,"title":"older","merged_at":"2026-10-20T00:00:00Z"},
				{"number":3,"title":"newer","merged_at":"2026-10-24T00:00:00Z"}]`))
		case strings.HasSuffix(p, "/pulls"):
			w.Write([]byte(`[{"number":230,"title":"Draft thing","draft":true},{"number":231,"title":"Ready thing","draft":false}]`))
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(srv.Close)
	return srv, &calls
}

func TestGitHubWithState(t *testing.T) {
	srv, calls := fakeGitHub(t)
	r := fetchGitHub(&ghClient{base: srv.URL, client: srv.Client()}, "aaaaaaa")
	if r.Err != nil || *calls > ghCallBudget || r.Calls != *calls {
		t.Fatalf("err=%v calls=%d (reported %d)", r.Err, *calls, r.Calls)
	}
	if r.PackErr != nil || r.Pack.Sha != "f5775f8" {
		t.Fatalf("the newest pack should come from the pack repo: %+v err=%v", r.Pack, r.PackErr)
	}
	if len(r.News) != 2 || r.News[0].N != 107 || r.News[0].Title != "Drag a circle" || r.News[1].N != 106 || !r.NewsSince {
		t.Fatalf("news should be the merges, newest first: %+v", r.News)
	}
	if len(r.Soon) != 2 || r.Soon[0].Tag != "draft" || r.Soon[1].Tag != "ready" {
		t.Fatalf("coming soon tags: %+v", r.Soon)
	}
}

func TestGitHubWithoutState(t *testing.T) {
	srv, calls := fakeGitHub(t)
	r := fetchGitHub(&ghClient{base: srv.URL, client: srv.Client()}, "")
	if r.Err != nil || *calls > ghCallBudget {
		t.Fatalf("err=%v calls=%d", r.Err, *calls)
	}
	if len(r.News) != 2 || r.News[0].Title != "newer" || r.NewsSince {
		t.Fatalf("no state: want merged PRs only, newest first: %+v", r.News)
	}
}

func TestGitHubDown(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "nope", http.StatusServiceUnavailable)
	}))
	defer srv.Close()
	r := fetchGitHub(&ghClient{base: srv.URL, client: srv.Client()}, "")
	if r.Err == nil || r.Calls != 1 {
		t.Fatalf("an unreachable GitHub should stop after one call with an error: %+v", r)
	}
	if text, level := statusLine("f5775f8", r.MainSha, r.Pack, r.Err); level != "warn" || !strings.Contains(text, "503") {
		t.Fatalf("status: %q %q", text, level)
	}
}

func TestLauncherBackgroundIsEmbedded(t *testing.T) {
	if !bytes.HasPrefix(launcherBg, []byte{0xff, 0xd8, 0xff}) {
		t.Fatalf("brand/launcher-bg.jpg is not an embedded JPEG (%d bytes)", len(launcherBg))
	}
	if !strings.Contains(launcherHTML, "url(bg.jpg)") {
		t.Fatal("the page does not use the background")
	}
}

func TestEdgeArgs(t *testing.T) {
	la := `C:\Users\a\AppData\Local`
	args := edgeArgs("http://127.0.0.1:1/x/", la, 96)
	want := filepath.Join(la, "BeyondAndTabs", "launcher-app")
	have := map[string]bool{}
	for _, a := range args {
		have[a] = true
		if strings.HasPrefix(a, "--force-device-scale-factor") {
			t.Errorf("must not force scale: %q", a)
		}
	}
	for _, a := range []string{"--app=http://127.0.0.1:1/x/", "--user-data-dir=" + want, "--window-size=1190,610", "--no-first-run", "--no-default-browser-check", "--disable-sync", "--disable-features=msImplicitSignin"} {
		if !have[a] {
			t.Errorf("missing %q in %v", a, args)
		}
	}
}

func TestWindowSizeIsLogical(t *testing.T) {
	for _, c := range []struct{ dpi, w, h int }{{96, 1190, 610}, {120, 1190, 610}, {144, 1190, 610}, {0, 1190, 610}} {
		if w, h := windowSize(c.dpi); w != c.w || h != c.h {
			t.Errorf("dpi %d: got %dx%d want %dx%d", c.dpi, w, h, c.w, c.h)
		}
	}
}
