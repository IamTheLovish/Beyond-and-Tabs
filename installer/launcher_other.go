//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
)

// The installer only ships for Windows; these stand-ins let `go test` run on the Linux CI runner.

func hideConsole() {}

func hideWindow(*exec.Cmd) {}

func alert(title, msg string) { fmt.Fprintln(os.Stderr, title+": "+msg) }

func displayDPI() int { return 96 }

// ensureHighGPU is a no-op off Windows: the GPU preference is a Windows setting.
func ensureHighGPU(gamePaths) string { return "" }
