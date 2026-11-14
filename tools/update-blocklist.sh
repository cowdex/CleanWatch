#!/bin/sh
# Downloads Peter Lowe's ad and tracking server list (https://pgl.yoyo.org/adservers/,
# about 3,500 hosts) into the app's assets as blocklist-pgl.txt. Rebuild afterwards.
set -eu
cd "$(dirname "$0")/.."
out=app/src/main/assets/blocklist-pgl.txt
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

curl -fsSL --max-time 60 \
  'https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext' \
  -o "$tmp"

# Keep only lines that look like hostnames, so an error page can never become the list.
grep -E '^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$' "$tmp" > "$tmp.hosts" || true
count=$(wc -l < "$tmp.hosts" | tr -d ' ')
if [ "$count" -lt 1000 ]; then
  rm -f "$tmp.hosts"
  echo "Only $count hosts downloaded; keeping the existing list." >&2
  exit 1
fi

{
  echo "# Peter Lowe's ad and tracking server list, https://pgl.yoyo.org/adservers/"
  echo "# Downloaded $(date -u +%Y-%m-%d) by tools/update-blocklist.sh. Do not edit; use blocklist.txt."
  cat "$tmp.hosts"
} > "$out"
rm -f "$tmp.hosts"
echo "$count hosts written to $out"
