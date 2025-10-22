#!/usr/bin/env bash
set -euo pipefail

REMOTE_URL=${1:-${REMOTE_URL:-}}
BRANCH=${2:-${BRANCH:-main}}

if [[ -z "${REMOTE_URL}" ]]; then
  echo "Usage: $0 <remote-url> [branch] or set REMOTE_URL"
  exit 2
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a git repository"
  exit 2
fi

# Ensure clean commit
if [[ -n "$(git status --porcelain)" ]]; then
  git add -A
  git commit -m "Initial commit: MySQL connection health check"
fi

# Rename to desired branch
git branch -M "$BRANCH"

# Configure origin
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

# Push with upstream
git push -u origin "$BRANCH"
