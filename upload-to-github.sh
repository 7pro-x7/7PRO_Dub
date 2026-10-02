#!/usr/bin/env bash
# Pushes this project to GitHub WITH the executable bit stored inside git.
# This is the permanent fix for:  /bin/sh: 1: ./gradlew: Permission denied
# Needs only git (Mac / Linux / Windows "Git Bash" / GitHub Codespaces / Termux).
#
# Usage:  bash upload-to-github.sh https://github.com/USER/REPO.git [branch]
#   Add FORCE=1 in front to overwrite the remote branch:  FORCE=1 bash upload-to-github.sh URL
set -euo pipefail
URL="${1:?Usage: bash upload-to-github.sh https://github.com/USER/REPO.git [branch]}"
BRANCH="${2:-main}"
cd "$(dirname "$0")"

[ -d .git ] || git init -q
git checkout -q -B "$BRANCH"
git add -A
# Force mode 100755 on gradlew and every shell script, regardless of the local filesystem.
for f in $(git ls-files '*gradlew' '*.sh'); do
  git update-index --chmod=+x -- "$f"
done
git -c user.name="${GIT_AUTHOR_NAME:-7PRO}" -c user.email="${GIT_AUTHOR_EMAIL:-dev@7pro.local}" \
  commit -q -m "Update project (gradlew executable)" || echo "Nothing new to commit"

git remote remove origin 2>/dev/null || true
git remote add origin "$URL"
if [ "${FORCE:-0}" = "1" ]; then git push -u --force origin "$BRANCH"; else git push -u origin "$BRANCH"; fi

echo "gradlew mode in git: $(git ls-files -s android-7pro/gradlew | cut -c1-6)  (must be 100755)"
