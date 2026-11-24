#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN=$(gh auth token)

echo "[*] Synchronizing local wiki files to https://github.com/h9zdev/GHOSTH/wiki..."

TMP_DIR="/tmp/ghosth_wiki_sync"
rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"

cd "$TMP_DIR"
git init
git config user.name "h9zdev"
git config user.email "h9zdev@users.noreply.github.com"

cp "$DIR"/*.md .

git add .
git commit -m "docs: update GitHub wiki pages"
git remote add origin "https://${TOKEN}@github.com/h9zdev/GHOSTH.wiki.git"
git push -u origin master --force

echo "[✓] SUCCESS: GitHub Wiki at https://github.com/h9zdev/GHOSTH/wiki has been fully updated!"
