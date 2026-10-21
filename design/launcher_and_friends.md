# Launcher and friends hub (proposal, Lane C, Oct 20)

**Status: a proposal for lovish. Nothing here gets built before his yes.** It came from Lane C's tester ("should we
add our own client for the mod?" ... "a launcher and a friends hub sound like a good project"). Each part says what it
needs from lovish.

## Why

Two kinds of friction from the Oct 20 session:
- **Getting and updating the game:** Forge, about 19 required mods, our jar, matching versions, Java. One wrong version
  and our network channel refuses the connection.
- **Playing together:** every e4mc session has a new address that has to be sent by hand (#38, item 35).

## Not our own Minecraft client

A modified Minecraft can't be redistributed under Mojang's EULA, and players still need their Microsoft login. We
stay a Forge mod, and the launcher stays a thin layer on a real launcher.

## Part 1: the launcher (builds on PR #1)

PR #1 (installer/, Go, about 6 MB) already downloads Prism Launcher portable, builds the instance from the `test-pack`
`.mrpack` (MC 1.20.1, Forge 47.4.0, our jar, every mod sha1-checked), adds shortcuts and repairs or updates when re-run.
Proposed additions, each small:

1. **One "Play" shortcut that updates first:** check the `test-pack` release; if it's newer, update the instance
   (the existing repair path), then start the game directly (`prismlauncher --launch <instance>`). Players never see
   an out-of-date version.
2. **Our name and icon** on the shortcut and the Prism instance; Prism stays the engine underneath (GPL-3.0, credited).
3. **First-launch settings** (with #51): full screen, no accessibility screen, our resource pack on.
4. **Channels:** "stable" (`bar-dev` after a green main) and "test" (a branch build), so testers can try a PR.

Needs from lovish: merge PR #1 first (Lane A owns installs), and say yes to these four.

## Part 2: the friends hub

**Goal:** add a friend once; see when they are hosting; press **Join**. No addresses.

**How:**
- Each install makes a key pair once. The public key's short form is your **friend code** (for example `BAT-7K3Q-M2XD`).
- In game (a **Friends** screen in the mod, opened from the title screen and the lobby): add codes, see who is
  online or hosting, and Join or Invite.
- When you host (Host Online, #46), the mod sends `{code, current address, expiry}` signed with your private key to a
  **lookup service**. A friend's mod asks the service for the codes it knows and gets the signed address back,
  checks the signature, and connects. A forged entry fails the signature check, so nobody can send your friends to a
  fake server.
- The service stores only friend codes, signed addresses and an expiry (minutes). No accounts, names, chat or
  friend lists: those stay on each player's PC.

**The service** is the only part that runs online:
- A Cloudflare Worker plus KV (free tier: 100k requests a day, far beyond a test group), about 150 lines.
- **Someone has to own the Cloudflare account. That's lovish's call** (it's his project and his name on it).
- Alternatives: a tiny server on a host lovish already has, or Discord or Steam later.

**Effort at Claude speed:** about 1-2 hours for the service with its tests, half a day for the Friends screen, the
signing and GameTests, then one CI cycle per PR. The real waits are lovish's decisions and a two-player test.

**Privacy:** an online status shows only to people who added your code. A setting can hide it ("appear offline").

## Questions for lovish

1. Merge PR #1 and build Part 1 on it? (Lane A or Lane C?)
2. Friends hub: yes or no, and who owns the service account (yours, or a project account)?
3. Friends screen look: a mock-up picture first?

Lane C (Isagi)
