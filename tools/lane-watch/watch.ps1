# Lane watcher: reads what the watched authors post on GitHub and triages it for one lane's Claude session.
# Every run polls GitHub (cheap, no Claude). Only when there is something new does it start one headless Claude run
# (watch-prompt.md) that sorts the posts into notes/INBOX.md, notes/LANE-STATE.md, notes/NEEDS-OWNER.md and draft
# replies. That run cannot push, post or merge: GitHub text is data, so outward actions wait for the interactive
# session or its owner.
# Needs: gh (signed in), git, Claude Code (claude on PATH), PowerShell 5.1+.
# Setup: copy config.example.json to config.json and edit it, then try: watch.ps1 -DryRun
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File watch.ps1 [-DryRun]
param([switch]$DryRun)
$ErrorActionPreference = 'Stop'
$Here = $PSScriptRoot
if (Test-Path (Join-Path $Here 'PAUSED')) { exit 0 }
$cfgPath = Join-Path $Here 'config.json'
if (-not (Test-Path $cfgPath)) { throw "Copy config.example.json to config.json and edit it first." }
$cfg = Get-Content -Raw $cfgPath | ConvertFrom-Json
$Repo = $cfg.repo; $Authors = @($cfg.authors); $Clone = $cfg.clone
$Model = if ($cfg.model) { $cfg.model } else { 'sonnet' }
$Budget = if ($cfg.budget_usd) { $cfg.budget_usd } else { 1 }
$Lookback = if ($cfg.lookback_hours) { $cfg.lookback_hours } else { 24 }
if (-not (Test-Path $Clone)) { throw "config.json 'clone' does not exist: $Clone" }
$notes = Join-Path $Here 'notes'; $inbox = Join-Path $Here 'inbox'; $logs = Join-Path $Here 'logs'
foreach ($d in $notes, (Join-Path $notes 'drafts'), $inbox, $logs) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
$statePath = Join-Path $Here 'state.json'
$state = if (Test-Path $statePath) { Get-Content -Raw $statePath | ConvertFrom-Json } else { $null }
$since = if ($state) { $state.since } else { (Get-Date).ToUniversalTime().AddHours(-$Lookback).ToString('yyyy-MM-ddTHH:mm:ssZ') }
$seen = @{}; if ($state -and $state.seen) { foreach ($k in $state.seen) { $seen[$k] = $true } }
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

function Get-Api([string]$path) {
    $json = & gh api --paginate $path 2>&1
    if ($LASTEXITCODE -ne 0) { throw "gh api $path failed: $json" }
    # --paginate prints each page as its own array; join them.
    $text = ($json | Out-String) -replace '\]\s*\[', ','
    if (-not $text.Trim()) { return @() }
    return @($text | ConvertFrom-Json)
}

$items = New-Object System.Collections.ArrayList
function Add-Item($kind, $id, $login, $url, $when, $title, $body) {
    $key = "$kind-$id"
    if ($seen.ContainsKey($key) -or ($Authors -notcontains $login)) { return }
    [void]$items.Add([ordered]@{ key = $key; kind = $kind; author = $login; url = $url; time = $when; title = $title; body = $body })
}
foreach ($c in (Get-Api "repos/$Repo/issues/comments?since=$since&per_page=100")) {
    Add-Item 'comment' "$($c.id)@$($c.updated_at)" $c.user.login $c.html_url $c.updated_at ($c.issue_url -replace '.*/', '#') $c.body }
foreach ($c in (Get-Api "repos/$Repo/pulls/comments?since=$since&per_page=100")) {
    Add-Item 'review-comment' "$($c.id)@$($c.updated_at)" $c.user.login $c.html_url $c.updated_at "$($c.path):$($c.line)" $c.body }
foreach ($i in (Get-Api "repos/$Repo/issues?since=$since&state=all&per_page=100")) {
    # New issues and PRs, plus state changes (merged, closed) and body edits, keyed by updated_at.
    $kind = if ($i.pull_request) { 'pr' } else { 'issue' }
    Add-Item $kind "$($i.number)@$($i.updated_at)" $i.user.login $i.html_url $i.updated_at "#$($i.number) [$($i.state)] $($i.title)" $i.body }
foreach ($m in (Get-Api "repos/$Repo/commits?sha=main&since=$since&per_page=100")) {
    $login = if ($m.author) { $m.author.login } else { $m.commit.author.name }
    Add-Item 'commit-main' $m.sha $login $m.html_url $m.commit.author.date $m.sha.Substring(0, 8) $m.commit.message }

$stamp = '{0:yyyyMMdd-HHmm}' -f (Get-Date)
"$((Get-Date).ToString('s')) since=$since new=$($items.Count)" | Add-Content (Join-Path $logs 'polls.log')
if ($items.Count -gt 0) {
    $batch = Join-Path $inbox "$stamp.json"
    ($items | ConvertTo-Json -Depth 4) | Set-Content -Path $batch -Encoding utf8
    if ($DryRun) { Write-Output "DryRun: $($items.Count) new item(s) in $batch"; exit 0 }
    $prompt = (Get-Content -Raw (Join-Path $Here 'watch-prompt.md')).Replace('{BATCH}', $batch).Replace('{LANE}', $cfg.lane).Replace('{REPO}', $Repo).Replace('{AUTHORS}', ($Authors -join ', ')).Replace('{NOTES}', $notes).Replace('"', '\"')
    # Permission rules take absolute paths as //c/Users/... (forward slashes, lower-case drive).
    $rule = '//' + ($Here.Substring(0, 1).ToLower()) + ($Here.Substring(2) -replace '\\', '/')
    # Deny by default: read anything, write only under notes/, git read-only, gh view-only. No gh api (it can POST).
    $allowed = "Read,Glob,Grep,Edit($rule/notes/**),Write($rule/notes/**),Bash(git fetch*),Bash(git log*),Bash(git diff*),Bash(git show*),Bash(gh pr view*),Bash(gh pr diff*),Bash(gh issue view*)"
    $denied = "Edit($rule/*.ps1),Edit($rule/*.md),Edit($rule/*.json),Bash(git push*),Bash(gh api*),Bash(gh pr comment*),Bash(gh pr merge*),Bash(gh issue comment*)"
    $claude = (Get-Command claude -ErrorAction Stop).Source
    Push-Location $Clone
    $out = & $claude -p $prompt --model $Model --permission-mode dontAsk --allowedTools $allowed --disallowedTools $denied --add-dir $Here --max-budget-usd $Budget --no-session-persistence --output-format json 2>&1
    $code = $LASTEXITCODE; Pop-Location
    $out | Set-Content -Path (Join-Path $logs "triage-$stamp.log") -Encoding utf8
    if ($code -ne 0) { "$((Get-Date).ToString('s')) triage exit $code for $batch (state not advanced)" | Add-Content (Join-Path $notes 'ALERTS.txt'); exit $code }
}
# Advance only after a successful triage, so a failed run is retried on the next poll.
foreach ($it in $items) { $seen[$it.key] = $true }
$keep = @($seen.Keys | Select-Object -Last 2000)
([ordered]@{ since = $now; seen = $keep } | ConvertTo-Json -Depth 3) | Set-Content -Path $statePath -Encoding utf8
