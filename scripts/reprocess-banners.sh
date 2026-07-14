#!/bin/bash
# Re-encodes every existing post banner in a media directory to a resized WebP,
# matching what new uploads produce (uses the app's BannerImageProcessor).
#
# Usage:   ./reprocess-banners.sh /path/to/media/directory
# Dry run: ./reprocess-banners.sh /path/to/media/directory --dry-run
#
# Requires a JDK (jshell) on PATH. Builds the fat JAR automatically if missing.
set -euo pipefail

MEDIA_DIR="${1:-}"
DRY_RUN="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JSH_SCRIPT="$SCRIPT_DIR/reprocess-banners.jsh"

if [ -z "$MEDIA_DIR" ]; then
    echo "Usage: $0 <media-directory> [--dry-run]"
    exit 1
fi

if [ ! -d "$MEDIA_DIR" ]; then
    echo "Error: $MEDIA_DIR is not a directory"
    exit 1
fi

# Collect the banner file for each post directory. Mirrors the app's lookup:
# the first file whose name starts with "banner".
declare -a BANNERS=()
for post_dir in "$MEDIA_DIR"/*/; do
    [ -d "$post_dir" ] || continue
    banner_file="$(find "$post_dir" -maxdepth 1 -type f -name 'banner*' | sort | head -1)"
    [ -n "$banner_file" ] || continue
    BANNERS+=("$banner_file")
done

if [ "${#BANNERS[@]}" -eq 0 ]; then
    echo "No banner files found under $MEDIA_DIR"
    exit 0
fi

if [ "$DRY_RUN" = "--dry-run" ]; then
    echo "Would reprocess ${#BANNERS[@]} banner(s) to banner.webp:"
    for b in "${BANNERS[@]}"; do
        size="$(du -h "$b" | cut -f1)"
        printf "  %-8s %s\n" "$size" "$b"
    done
    exit 0
fi

# Ensure a fat JAR exists (contains BannerImageProcessor + bundled webp binaries).
FAT_JAR="$(ls -t "$PROJECT_ROOT"/build/libs/*-all.jar 2>/dev/null | head -1 || true)"
if [ -z "$FAT_JAR" ]; then
    echo "No fat JAR found, building it..."
    (cd "$PROJECT_ROOT" && ./gradlew buildFatJar --console=plain)
    FAT_JAR="$(ls -t "$PROJECT_ROOT"/build/libs/*-all.jar 2>/dev/null | head -1)"
fi
echo "Using fat JAR: $FAT_JAR"

# Hand the list of banner files to jshell (single JVM start).
LIST_FILE="$(mktemp)"
trap 'rm -f "$LIST_FILE"' EXIT
printf '%s\n' "${BANNERS[@]}" > "$LIST_FILE"

echo "Reprocessing ${#BANNERS[@]} banner(s)..."
ok=0
err=0
while IFS=$'\t' read -r status path in_size out_size; do
    case "$status" in
        OK)
            saved_pct=$(( (in_size - out_size) * 100 / in_size ))
            printf "OK    %s  %sKB -> %sKB (-%s%%)\n" \
                "$path" "$((in_size/1024))" "$((out_size/1024))" "$saved_pct"
            ok=$((ok+1))
            ;;
        ERR)
            printf "ERROR %s  %s\n" "$path" "$in_size"
            err=$((err+1))
            ;;
    esac
done < <(LIST_FILE="$LIST_FILE" jshell -q --class-path "$FAT_JAR" "$JSH_SCRIPT" 2>/dev/null | grep -E '^(OK|ERR)')

echo "Done: $ok converted, $err failed."
[ "$err" -eq 0 ]
