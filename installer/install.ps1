# Beyond and Tabs - Windows setup in one paste (lovish, Oct 23: "improve the launcher and the way I can copy n paste
# it"). Paste into PowerShell (Start -> type PowerShell -> Enter):
#
#   irm https://raw.githubusercontent.com/IamTheLovish/Beyond-and-Tabs/main/installer/install.ps1 | iex
#
# No .exe to download, so nothing for SmartScreen / antivirus to block. Adds the game to the Prism Launcher you
# already have (ours from BeyondAndTabs-Setup.exe, or your own install); without one it fetches portable Prism.
# The instance updates itself to the newest build before every launch (packwiz), shows lovish's cat, and gets a
# "lovish" shortcut on the Desktop and in the Start Menu. Safe to run again: your own instance settings are kept.
# CI switches: $env:BAT_ROOT (install folder instead of %LOCALAPPDATA%/%APPDATA%), $env:BAT_NO_LAUNCH = '1'.

& {
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Instance = 'BeyondAndTabs-test'
$Raw = 'https://raw.githubusercontent.com/IamTheLovish/Beyond-and-Tabs/main/installer/brand'
$PrismZipUrl = 'https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Windows-MSVC-Portable-11.1.1.zip'
$BootstrapUrl = 'https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar'
$IconKey = 'lovish_cat'

Write-Host '=== Beyond and Tabs setup (Windows) ==='
$local = if ($env:BAT_ROOT) { Join-Path $env:BAT_ROOT 'local' } else { $env:LOCALAPPDATA }
$roaming = if ($env:BAT_ROOT) { Join-Path $env:BAT_ROOT 'roaming' } else { $env:APPDATA }

# 1. Which Prism: ours (BeyondAndTabs-Setup.exe), then a normal Prism install, else portable Prism
$ours = Join-Path $local 'BeyondAndTabs\PrismLauncher'
$installedData = Join-Path $roaming 'PrismLauncher'
$installedExe = @((Join-Path $local 'Programs\PrismLauncher\prismlauncher.exe'),
                  (Join-Path ${env:ProgramFiles} 'PrismLauncher\prismlauncher.exe')) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (Test-Path (Join-Path $ours 'prismlauncher.exe')) {
    $data = $ours; $exe = Join-Path $ours 'prismlauncher.exe'
    Write-Host 'Found the Beyond and Tabs Prism Launcher.'
} elseif ($installedExe -and (Test-Path $installedData)) {
    $data = $installedData; $exe = $installedExe
    Write-Host 'Found your Prism Launcher - adding the game to it.'
} else {
    $data = $ours; $exe = Join-Path $ours 'prismlauncher.exe'
    New-Item -ItemType Directory -Force -Path $ours | Out-Null
    Write-Host 'Downloading Prism Launcher (20 MB) ...'
    $zip = Join-Path $ours 'prism.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $PrismZipUrl -OutFile $zip
    Expand-Archive -Force -Path $zip -DestinationPath $ours
    Remove-Item $zip
    Set-Content -Path (Join-Path $ours 'portable.txt') -Value 'Portable mode: all launcher data stays in this folder.'
    $cfgPath = Join-Path $ours 'prismlauncher.cfg'
    if (-not (Test-Path $cfgPath)) {
        Set-Content -Path $cfgPath -Value "[General]`nConfigVersion=1.3`nAutomaticJavaDownload=true`nAutomaticJavaSwitch=true`nLanguage=en_US`nApplicationTheme=system`nIconTheme=pe_colored`nSelectedInstance=$Instance"
    }
}

# 2. The instance (kept if it exists: only the cat and the pack updater are made sure of)
$inst = Join-Path $data "instances\$Instance"
$game = Join-Path $inst 'minecraft'
$icons = Join-Path $data 'icons'
New-Item -ItemType Directory -Force -Path $game, $icons | Out-Null
$cfg = Join-Path $inst 'instance.cfg'
$preLaunch = 'PreLaunchCommand=\"$INST_JAVA\" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/IamTheLovish/beyond-and-tabs-pack/main/windows/pack.toml'
if (-not (Test-Path $cfg)) {
    $uuid = [guid]::NewGuid().ToString('N')
    $lines = @('[General]', 'ConfigVersion=1.3', 'InstanceType=OneSix', "name=$Instance", 'iconKey=lovish_cat',
               "uuid=$uuid", 'AutomaticJava=true', 'OverrideCommands=true', $preLaunch)
} else {
    $lines = @()
    foreach ($l in (Get-Content $cfg)) {
        if ($l -match '^(iconKey|PreLaunchCommand|OverrideCommands)=') { continue }
        $lines += $l
        if ($l -eq '[General]') { $lines += 'iconKey=lovish_cat' }
    }
    $lines += 'OverrideCommands=true'
    $lines += $preLaunch
}
[IO.File]::WriteAllText($cfg, (($lines -join "`n") + "`n"))
$pack = Join-Path $inst 'mmc-pack.json'
if (-not (Test-Path $pack)) {
    Set-Content -Path $pack -Value '{"components":[{"uid":"org.lwjgl3","version":"3.3.1","dependencyOnly":true},{"uid":"net.minecraft","version":"1.20.1","important":true},{"uid":"net.minecraftforge","version":"47.4.0"}],"formatVersion":1}'
}
Write-Host 'Downloading the game updater ...'
Invoke-WebRequest -UseBasicParsing -Uri $BootstrapUrl -OutFile (Join-Path $game 'packwiz-installer-bootstrap.jar')
$ico = Join-Path $data 'lovish.ico'
try {
    Invoke-WebRequest -UseBasicParsing -Uri "$Raw/cat.png" -OutFile (Join-Path $icons "$IconKey.png")
    Invoke-WebRequest -UseBasicParsing -Uri "$Raw/cat.ico" -OutFile $ico
} catch { Write-Host '(could not fetch the cat icon - the game works without it)'; $ico = $exe }

# 3. "lovish" shortcut with the cat (an existing "lovish" shortcut is replaced only if it opens this Prism; the old
#    "Beyond and Tabs" one goes only if it opens this Prism)
if (-not $env:BAT_ROOT) {
    $ws = New-Object -ComObject WScript.Shell
    foreach ($dir in @([Environment]::GetFolderPath('Desktop'), (Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs'))) {
        $lnk = Join-Path $dir 'lovish.lnk'
        if ((-not (Test-Path -LiteralPath $lnk)) -or ($ws.CreateShortcut($lnk).TargetPath -eq $exe)) {
            $s = $ws.CreateShortcut($lnk)
            $s.TargetPath = $exe
            $s.Arguments = "-l `"$Instance`""
            $s.WorkingDirectory = Split-Path $exe
            $s.IconLocation = "$ico,0"
            $s.Description = 'Play Beyond and Tabs'
            $s.Save()
        }
        $oldPath = Join-Path $dir 'Beyond and Tabs.lnk'
        if (Test-Path -LiteralPath $oldPath) {
            $old = $ws.CreateShortcut($oldPath)
            if ($old.TargetPath -eq $exe) { Remove-Item -LiteralPath $oldPath -ErrorAction SilentlyContinue }
        }
    }
}

Write-Host ''
Write-Host 'All done! Use the "lovish" shortcut on your Desktop from now on.'
Write-Host "Add your Microsoft (Minecraft) account when Prism asks, then select `"$Instance`" and click Launch."
Write-Host 'The first start takes a few minutes; every later launch updates the game automatically.'
if ($env:BAT_NO_LAUNCH -ne '1') {
    Start-Process -FilePath $exe -ArgumentList "-l `"$Instance`"" -WorkingDirectory (Split-Path $exe)
}
}
