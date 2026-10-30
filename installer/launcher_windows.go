//go:build windows

package main

import (
	"errors"
	"fmt"
	"os/exec"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows/registry"
)

var (
	kernel32        = syscall.NewLazyDLL("kernel32.dll")
	user32          = syscall.NewLazyDLL("user32.dll")
	procFreeConsole = kernel32.NewProc("FreeConsole")
	procMessageBox  = user32.NewProc("MessageBoxW")
)

// hideConsole detaches from the console window the shortcut opened (minimised), so only the launcher window stays.
func hideConsole() { procFreeConsole.Call() }

// hideWindow keeps helper console programs (reg, rundll32) from flashing a window.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
}

// alert shows an error box: with the console detached this is the only way to say what went wrong.
func alert(title, msg string) {
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(msg)
	procMessageBox.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x10) // MB_ICONERROR
}

// displayDPI is the system DPI (96 = 100%). SetProcessDPIAware first: an unaware process is told 96 whatever the scaling.
// The launcher has no window of its own, so this changes nothing visible.
func displayDPI() int {
	user32.NewProc("SetProcessDPIAware").Call()
	p := user32.NewProc("GetDpiForSystem")
	if p.Find() != nil {
		return 96
	}
	if d, _, _ := p.Call(); d >= 96 {
		return int(d)
	}
	return 96
}

// gpuPrefKey is the per-user (HKCU, no admin) key where Windows keeps each app's graphics setting.
const gpuPrefKey = `Software\Microsoft\DirectX\UserGpuPreferences`

// winGPUStore is gpuPrefStore on the real registry.
type winGPUStore struct{}

func (winGPUStore) Get(exe string) (string, bool, error) {
	k, err := registry.OpenKey(registry.CURRENT_USER, gpuPrefKey, registry.QUERY_VALUE)
	if errors.Is(err, registry.ErrNotExist) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	defer k.Close()
	v, _, err := k.GetStringValue(exe)
	if errors.Is(err, registry.ErrNotExist) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return v, true, nil
}

func (winGPUStore) Set(exe, value string) error {
	k, _, err := registry.CreateKey(registry.CURRENT_USER, gpuPrefKey, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer k.Close()
	return k.SetStringValue(exe, value)
}

// ensureHighGPU makes Windows run the game's Java on the high-performance GPU. It logs a line and returns the status
// note for the page: "" when nothing was written, the error when the write failed (the launch goes on).
func ensureHighGPU(p gamePaths) string {
	exe := javaExe(p)
	wrote, err := decideGPUPref(winGPUStore{}, exe)
	if err != nil {
		msg := "Could not set high-performance GPU: " + err.Error()
		fmt.Println(msg, "("+exe+")")
		return msg
	}
	if !wrote {
		return ""
	}
	msg := "Game set to use the high-performance GPU"
	fmt.Println(msg+":", exe)
	return msg
}
