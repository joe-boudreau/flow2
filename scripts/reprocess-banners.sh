#!/bin/bash
# Re-encodes every existing post banner under a media directory to a resized WebP,
# matching what new uploads produce (uses the app's BannerImageProcessor).
#
# Use this when the media directory is a normal path on THIS machine. If the media
# lives in a Docker volume on the server, use reprocess-banners-docker.sh instead.
#
# Usage:   ./reprocess-banners.sh /path/to/media/directory
# Dry run: ./reprocess-banners.sh /path/to/media/directory --dry-run
#
# Requires a JDK (jshell) on PATH. Builds the fat JAR automatically if missing.
set -euo pipefail

MEDIA_DIR="${1:-}"
DRY_FLAG="${2:-}"

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

DRY_RUN=""
[ "$DRY_FLAG" = "--dry-run" ] && DRY_RUN="1"
export MEDIA_DIR DRY_RUN

# Ensure a fat JAR exists (contains BannerImageProcessor + bundled webp binaries).
FAT_JAR="$(ls -t "$PROJECT_ROOT"/build/libs/*-all.jar 2>/dev/null | head -1 || true)"
if [ -z "$FAT_JAR" ]; then
    echo "No fat JAR found, building it..."
    (cd "$PROJECT_ROOT" && ./gradlew buildFatJar --console=plain)
    FAT_JAR="$(ls -t "$PROJECT_ROOT"/build/libs/*-all.jar 2>/dev/null | head -1)"
fi
echo "Using fat JAR: $FAT_JAR"

source "$SCRIPT_DIR/reprocess-banners-report.sh"
run_and_report jshell -q --class-path "$FAT_JAR" "$JSH_SCRIPT"
