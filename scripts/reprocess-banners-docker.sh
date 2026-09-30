#!/bin/bash
# Re-encodes existing post banners to WebP inside a one-off container that mounts
# the media volume BY ITS LITERAL NAME. Run this ON THE SERVER when the media is a
# Docker volume. Mounting the named volume directly avoids compose project-name /
# volume-prefix ambiguity (which can resolve to an empty auto-created volume).
#
# It reuses the deployed image (jar at /app/flow2.jar, bundling BannerImageProcessor
# + the webp binaries and a full JDK with jshell), so output matches live uploads.
#
# Usage:   ./reprocess-banners-docker.sh
# Dry run: DRY_RUN=1 ./reprocess-banners-docker.sh
#
# Override defaults via env vars if needed:
#   VOLUME=flow2-media-vol          media volume name (from `docker volume ls`)
#   IMAGE=thejoeflow/flow2:latest   deployed app image
#   CONTAINER_MEDIA_DIR=/data/media MEDIA_DIRECTORY_PATH inside the container
set -euo pipefail

VOLUME="${VOLUME:-flow2-media-vol}"
IMAGE="${IMAGE:-thejoeflow/flow2:latest}"
CONTAINER_MEDIA_DIR="${CONTAINER_MEDIA_DIR:-/data/media}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JSH_SCRIPT="$SCRIPT_DIR/reprocess-banners.jsh"

if [ ! -f "$JSH_SCRIPT" ]; then
    echo "Error: $JSH_SCRIPT not found (copy the scripts/ dir to the server)."
    exit 1
fi
if ! docker volume inspect "$VOLUME" >/dev/null 2>&1; then
    echo "Error: docker volume '$VOLUME' not found. Available volumes:"
    docker volume ls
    exit 1
fi

echo "Volume: $VOLUME  Image: $IMAGE  Media dir: $CONTAINER_MEDIA_DIR"
source "$SCRIPT_DIR/reprocess-banners-report.sh"

# --entrypoint jshell overrides the app's `java -jar` entrypoint. The jsh is
# bind-mounted read-only; the classpath points at the image's fat jar.
run_and_report docker run --rm --entrypoint jshell \
    -e MEDIA_DIR="$CONTAINER_MEDIA_DIR" \
    -e DRY_RUN="${DRY_RUN:-}" \
    -v "$VOLUME:$CONTAINER_MEDIA_DIR" \
    -v "$JSH_SCRIPT:/reprocess-banners.jsh:ro" \
    "$IMAGE" \
    -q --class-path /app/flow2.jar /reprocess-banners.jsh
