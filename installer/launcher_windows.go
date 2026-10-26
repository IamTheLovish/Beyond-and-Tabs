//go:build windows

package main

import (
	"os/exec"
	"syscall"
	"unsafe"
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
