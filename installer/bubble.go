package main

import (
	_ "embed"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// The desktop bubble (lovish, Oct 23): the cat in a round, always-on-top bubble that starts the game when clicked.

const (
	bubbleShortcut = shortcutName // lovish, Oct 28: the one Desktop icon is "lovish", and it brings the bubble back
	bubbleImage    = "cat.png"
)

//go:embed bubble/bubble.ps1
var bubblePS []byte

// installBubble writes the bubble script and the cat into <root>\Bubble.
func installBubble(root string) (string, error) {
	dir := filepath.Join(root, "Bubble")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	if err := os.WriteFile(filepath.Join(dir, "bubble.ps1"), bubblePS, 0o644); err != nil {
		return "", err
	}
	return dir, os.WriteFile(filepath.Join(dir, bubbleImage), catPNG, 0o644)
}

// bubbleShortcutScript makes the Desktop "lovish" shortcut, which brings the bubble back (the only Desktop icon:
// clicking the cat opens the launcher), plus a copy in the bubble folder (what "Start with Windows" copies into
// Startup), then opens the bubble. A Desktop "lovish" is only rewritten when this installer made it: missing, the
// launcher's (launcherExe), the earlier installers' (this Prism with -l the instance) or already the bubble's - never
// lovish's own, which starts his server. The "Beyond and Tabs bubble" shortcut earlier installers made is removed.
func bubbleShortcutScript(dir, icoPath, launcherExe, prismExe string) string {
	args := `-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "` + dir + `\bubble.ps1"`
	return `
$ps = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$bargs = '` + psq(args) + `'
$exe = '` + psq(launcherExe) + `'
$prism = '` + psq(prismExe) + `'
$ws = New-Object -ComObject WScript.Shell
$desk = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desk '` + bubbleShortcut + `.lnk'
$ours = (-not (Test-Path -LiteralPath $lnk))
if (-not $ours) {
  $t = $ws.CreateShortcut($lnk)
  $ours = ($t.TargetPath -eq $exe) -or (($t.TargetPath -eq $prism) -and ($t.Arguments -eq '-l "` + instanceName + `"')) -or ($t.Arguments -like '*bubble.ps1*')
}
$targets = @(Join-Path '` + psq(dir) + `' 'bubble.lnk')
if ($ours) { $targets += $lnk }
foreach ($l in $targets) {
  $s = $ws.CreateShortcut($l)
  $s.TargetPath = $ps
  $s.Arguments = $bargs
  $s.WorkingDirectory = '` + psq(dir) + `'
  $s.IconLocation = '` + psq(icoPath) + `,0'
  $s.WindowStyle = 7
  $s.Description = '` + appName + `: click the cat to play'
  $s.Save()
}
$oldBubble = Join-Path $desk 'Beyond and Tabs bubble.lnk'
if ((Test-Path -LiteralPath $oldBubble) -and ($ws.CreateShortcut($oldBubble).Arguments -like '*bubble.ps1*')) {
  Remove-Item -LiteralPath $oldBubble -ErrorAction SilentlyContinue
}
Start-Process -FilePath $ps -ArgumentList $bargs -WindowStyle Hidden`
}

func bubble(root, icoPath, launcherExe, prismExe string) error {
	dir, err := installBubble(root)
	if err != nil {
		return err
	}
	cmd := exec.Command("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", bubbleShortcutScript(dir, icoPath, launcherExe, prismExe))
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}
