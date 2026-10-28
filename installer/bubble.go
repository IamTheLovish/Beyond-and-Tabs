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
	bubbleShortcut = "Beyond and Tabs bubble"
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

// bubbleShortcutScript makes the Desktop "Beyond and Tabs bubble" shortcut plus a copy in the bubble folder (what
// "Start with Windows" copies into Startup), then opens the bubble.
func bubbleShortcutScript(dir, icoPath string) string {
	args := `-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "` + dir + `\bubble.ps1"`
	return `
$ps = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$bargs = '` + args + `'
$ws = New-Object -ComObject WScript.Shell
foreach ($lnk in @((Join-Path ([Environment]::GetFolderPath('Desktop')) '` + bubbleShortcut + `.lnk'), (Join-Path '` + dir + `' 'bubble.lnk'))) {
  $s = $ws.CreateShortcut($lnk)
  $s.TargetPath = $ps
  $s.Arguments = $bargs
  $s.WorkingDirectory = '` + dir + `'
  $s.IconLocation = '` + icoPath + `,0'
  $s.WindowStyle = 7
  $s.Description = 'Beyond and Tabs: click the cat to play'
  $s.Save()
}
Start-Process -FilePath $ps -ArgumentList $bargs -WindowStyle Hidden`
}

func bubble(root, icoPath string) error {
	dir, err := installBubble(root)
	if err != nil {
		return err
	}
	cmd := exec.Command("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", bubbleShortcutScript(dir, icoPath))
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}
