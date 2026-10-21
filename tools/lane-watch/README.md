# Lane watch

Reads what another lane posts on GitHub (comments, review comments, issues and PRs, commits on main) and has a headless Claude run sort it for your lane's session: tasks, files to avoid, decisions, and draft replies. A poll with nothing new costs nothing. A triage run is capped at $1; the first one, on 34 posts, cost $0.29. It never pushes, posts or merges.

## Setup (Windows, about 5 minutes)
1. Needs `gh` (signed in), `git`, Claude Code (`claude` on PATH) and PowerShell 5.1 or later.
2. Copy this folder anywhere outside the repo, for example `C:\lane-watch` (inside the clone its notes would be gitignored but mixed with repo work). In this repo it lives at `tools/lane-watch/`; coordination between lanes happens on issue #35.
3. Copy `config.example.json` to `config.json`. Set `clone` to your local clone (use forward slashes, for example `C:/code/Beyond-and-Tabs`), `lane` to your lane's name, and `authors` to the GitHub logins to watch.
4. Test it: `powershell -NoProfile -ExecutionPolicy Bypass -File watch.ps1 -DryRun`. This lists the new posts without running Claude.
5. Run it every 10 minutes:
   `schtasks /create /tn "Lane watch" /sc minute /mo 10 /tr "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File C:\lane-watch\watch.ps1"`
6. Tell your lane's session to read `notes\LANE-STATE.md`, the newest part of `notes\INBOX.md`, `notes\NEEDS-OWNER.md` and `notes\drafts\` at every start, and to post a draft only after checking it.

## Files
- `notes/INBOX.md`: every batch, one line per post, with its class and the next step.
- `notes/LANE-STATE.md`: what to avoid now, what you're waiting for, the decisions so far (rewritten each run).
- `notes/NEEDS-OWNER.md`: things the person has to decide.
- `notes/drafts/`: draft replies.
- `notes/ALERTS.txt`: failed runs. A failed run is retried on the next poll.

To pause it, create a file named `PAUSED` in the folder. To read further back on the first run, raise `lookback_hours`.
