#!/usr/bin/env bash
# Renames template placeholders after "Use this template".
set -euo pipefail

read -rp "Project name: " PROJECT_NAME
read -rp "GitHub username/org: " GH_USER

FILES=$(grep -rlE '\{\{(PROJECT_NAME|GH_USER)\}\}' --include='*.md' --exclude-dir='.git' . || true)

for f in $FILES; do
  sed -i.bak \
    -e "s/{{PROJECT_NAME}}/${PROJECT_NAME}/g" \
    -e "s/{{GH_USER}}/${GH_USER}/g" \
    "$f"
  rm "${f}.bak"
done

echo "Done. Next: cp deployment/.env.example deployment/.env"
