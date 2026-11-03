# Beyond and Tabs desktop bubble: lovish's cat in a round, always-on-top bubble.
# Click = play, drag = move (remembered), right-click = Play / Start with Windows / Close bubble.
# Written by the installer into %LOCALAPPDATA%\BeyondAndTabs\Bubble next to cat.png and bubble.lnk.
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$created = $false
$mutex = New-Object System.Threading.Mutex($true, 'BeyondAndTabsBubble', [ref]$created)
if (-not $created) { exit }
[System.Windows.Forms.Application]::EnableVisualStyles()

$size = 88
$red = [System.Drawing.Color]::FromArgb(168, 36, 42)
$gold = [System.Drawing.Color]::FromArgb(224, 178, 90)
$img = [System.Drawing.Image]::FromFile((Join-Path $dir 'cat.png'))
$posFile = Join-Path $dir 'position.txt'

$form = New-Object System.Windows.Forms.Form
$form.FormBorderStyle = 'None'; $form.ShowInTaskbar = $false; $form.TopMost = $true
$form.StartPosition = 'Manual'; $form.Size = New-Object System.Drawing.Size($size, $size)
$form.BackColor = $red; $form.Text = 'Beyond and Tabs'
$shape = New-Object System.Drawing.Drawing2D.GraphicsPath; $shape.AddEllipse(0, 0, $size, $size)
$form.Region = New-Object System.Drawing.Region($shape)

# Where the bubble sits: the last saved spot if it is still on a screen, else bottom-right
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$x = $wa.Right - $size - 24; $y = $wa.Bottom - $size - 24
if (Test-Path $posFile) {
  $p = (Get-Content $posFile -Raw).Trim() -split ','
  if ($p.Count -eq 2) {
    $pt = New-Object System.Drawing.Point([int]$p[0], [int]$p[1])
    foreach ($s in [System.Windows.Forms.Screen]::AllScreens) { if ($s.WorkingArea.Contains($pt)) { $x = $pt.X; $y = $pt.Y } }
  }
}
$form.Location = New-Object System.Drawing.Point($x, $y)

$form.Add_Paint({ param($s, $e)
  $gr = $e.Graphics; $gr.SmoothingMode = 'AntiAlias'; $gr.InterpolationMode = 'HighQualityBicubic'
  $c = New-Object System.Drawing.Drawing2D.GraphicsPath; $c.AddEllipse(5, 5, ($size - 10), ($size - 10))
  $gr.SetClip($c); $gr.DrawImage($img, 5, 5, ($size - 10), ($size - 10)); $gr.ResetClip()
})

$script:lastLaunch = [datetime]::MinValue
function Start-Game {
  if (([datetime]::Now - $script:lastLaunch).TotalSeconds -lt 20) { return }
  # 1. the Desktop play shortcut (on lovish's PC it also starts his server) - unless it is this bubble's own
  #    "lovish" shortcut (the installer's one Desktop icon), which would only bring the bubble back
  $desks = @([Environment]::GetFolderPath('Desktop'), (Join-Path $env:USERPROFILE 'Desktop'),
             (Join-Path $env:USERPROFILE 'OneDrive\Desktop')) | Select-Object -Unique
  $wsh = New-Object -ComObject WScript.Shell
  foreach ($d in $desks) {
    $f = Join-Path $d 'lovish.lnk'
    if ((Test-Path -LiteralPath $f) -and -not ($wsh.CreateShortcut($f).Arguments -like '*bubble.ps1*')) {
      $script:lastLaunch = [datetime]::Now
      Start-Process -FilePath $f
      $tip.Show('Starting Beyond and Tabs...', $form, 0, -28, 4000)
      return
    }
  }
  # 2. the launcher next to this folder (what's new, your build, PLAY)
  $launcher = Join-Path (Split-Path -Parent $dir) 'launcher.exe'
  if (Test-Path -LiteralPath $launcher) {
    $script:lastLaunch = [datetime]::Now
    Start-Process -FilePath $launcher -ArgumentList '--launch' -WorkingDirectory (Split-Path -Parent $launcher) -WindowStyle Minimized
    $tip.Show('Starting Beyond and Tabs...', $form, 0, -28, 4000)
    return
  }
  # 3. the installed Prism next to this folder
  $prism = Join-Path (Split-Path -Parent $dir) 'PrismLauncher\prismlauncher.exe'
  if (Test-Path -LiteralPath $prism) {
    $script:lastLaunch = [datetime]::Now
    Start-Process -FilePath $prism -ArgumentList '-l "BeyondAndTabs-test"' -WorkingDirectory (Split-Path -Parent $prism)
    $tip.Show('Starting Beyond and Tabs...', $form, 0, -28, 4000)
    return
  }
  [System.Windows.Forms.MessageBox]::Show("Couldn't find the game. Run BeyondAndTabs-Setup.exe again, then click me.", 'Beyond and Tabs') | Out-Null
}

# Click = play, drag = move
$script:down = $null; $script:dragged = $false
$form.Add_MouseDown({ param($s, $e)
  if ($e.Button -eq 'Left') { $script:down = [System.Windows.Forms.Cursor]::Position; $script:start = $form.Location; $script:dragged = $false }
})
$form.Add_MouseMove({ param($s, $e)
  if ($null -ne $script:down -and $e.Button -eq 'Left') {
    $cur = [System.Windows.Forms.Cursor]::Position
    $dx = $cur.X - $script:down.X; $dy = $cur.Y - $script:down.Y
    if ([Math]::Abs($dx) + [Math]::Abs($dy) -gt 4) { $script:dragged = $true }
    if ($script:dragged) { $form.Location = New-Object System.Drawing.Point(($script:start.X + $dx), ($script:start.Y + $dy)) }
  }
})
$form.Add_MouseUp({ param($s, $e)
  if ($e.Button -eq 'Left' -and $null -ne $script:down) {
    if ($script:dragged) { Set-Content -Path $posFile -Value "$($form.Left),$($form.Top)" } else { Start-Game }
    $script:down = $null
  }
})
$form.Add_MouseEnter({ $form.BackColor = $gold })
$form.Add_MouseLeave({ $form.BackColor = $red })

$tip = New-Object System.Windows.Forms.ToolTip
$tip.SetToolTip($form, 'Click to play Beyond and Tabs - drag to move - right-click for more')

# Right-click menu
$startup = Join-Path ([Environment]::GetFolderPath('Startup')) 'Beyond and Tabs bubble.lnk'
$menu = New-Object System.Windows.Forms.ContextMenuStrip
[void]$menu.Items.Add('Play', $null, { Start-Game })
$auto = New-Object System.Windows.Forms.ToolStripMenuItem('Start with Windows')
$auto.Checked = Test-Path $startup
$auto.Add_Click({
  if (Test-Path $startup) { Remove-Item $startup } else { Copy-Item (Join-Path $dir 'bubble.lnk') $startup }
  $auto.Checked = Test-Path $startup
})
[void]$menu.Items.Add($auto)
[void]$menu.Items.Add('Close bubble', $null, { $form.Close() })
$form.ContextMenuStrip = $menu

[System.Windows.Forms.Application]::Run($form)
$mutex.ReleaseMutex()
