#!/usr/bin/env bash
# Copy a PR's head commit to the CI mirror (ILovishThe/beyond-and-tabs-ci, the org's own 20 runners) as branch
# pr-<n>, start build.yml there, and mark the PR's commit status `ci-mirror` pending. Used by build.yml (job mirror,
# when the router sends a PR there) and ci-mirror.yml (the label). Needs: a checkout of the PR head with full history,
# TOKEN (secret CI_MIRROR_TOKEN), GITHUB_TOKEN_HERE (this repo's token for the status), SHA, PR, MIRROR.
set -euo pipefail
if [ -z "${TOKEN:-}" ]; then
  echo "::error::secret CI_MIRROR_TOKEN is not set (see ci-mirror.yml); the mirror was not used"
  exit 1
fi
url="https://x-access-token:${TOKEN}@github.com/${MIRROR}.git"
export GH_TOKEN="$TOKEN"
# the mirror's default branch must hold build.yml for workflow_dispatch to find it: seed it once
if ! gh api "repos/$MIRROR/contents/.github/workflows/build.yml?ref=main" >/dev/null 2>&1; then
  git fetch -q origin main
  git push -q "$url" origin/main:refs/heads/main
  sleep 5
  # the mirror only tests PR branches: every other workflow (eyes, packs, voice, installer ...) is switched off there,
  # and the build the seed push started on its main is cancelled
  for id in $(gh api "repos/$MIRROR/actions/workflows" -q '.workflows[] | select(.path != ".github/workflows/build.yml") | .id'); do
    gh api -X PUT "repos/$MIRROR/actions/workflows/$id/disable" >/dev/null || true
  done
  for run in $(gh api "repos/$MIRROR/actions/runs?branch=main&per_page=20" -q '.workflow_runs[] | select(.status != "completed") | .id'); do
    gh api -X POST "repos/$MIRROR/actions/runs/$run/cancel" >/dev/null || true
  done
fi
# the mirror is scratch space: pr-<n> is overwritten by each push to the PR (never this repo's branches)
git push -q --force "$url" "$SHA:refs/heads/pr-$PR"
gh api -X POST "repos/$MIRROR/actions/workflows/build.yml/dispatches" -f ref="pr-$PR" >/dev/null
echo "::notice::[Mirror] PR #$PR ($SHA) is building in https://github.com/$MIRROR/actions (branch pr-$PR)"
GH_TOKEN="$GITHUB_TOKEN_HERE" gh api -X POST "repos/$GITHUB_REPOSITORY/statuses/$SHA" -f state=pending -f context=ci-mirror \
  -f description="GameTests running in the mirror" -f target_url="https://github.com/$MIRROR/actions" >/dev/null
