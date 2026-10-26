package main

import (
	"bytes"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

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

func TestStatusLine(t *testing.T) {
	text, level := statusLine("f5775f8", "f5775f89abcdef0123", nil)
	if level != "ok" || !strings.HasPrefix(text, "Up to date") {
		t.Fatalf("same sha: %q %q", text, level)
	}
	text, level = statusLine("f5775f8", "0123456789abcdef", nil)
	if level != "update" || text != "Update available: main is at 0123456" {
		t.Fatalf("different sha: %q %q", text, level)
	}
	text, level = statusLine("f5775f8", "", errors.New("dial tcp: no such host"))
	if level != "warn" || text != "Couldn't check GitHub (dial tcp: no such host)" {
		t.Fatalf("error: %q %q", text, level)
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
	if text, level := statusLine("f5775f8", r.MainSha, r.Err); level != "warn" || !strings.Contains(text, "503") {
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
