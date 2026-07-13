#!/bin/bash
# Adds correct file extensions to banner files that are missing them.
# Usage: ./fix-banner-extensions.sh /path/to/media/directory
# Dry run: ./fix-banner-extensions.sh /path/to/media/directory --dry-run

MEDIA_DIR="$1"
DRY_RUN="$2"

if [ -z "$MEDIA_DIR" ]; then
    echo "Usage: $0 <media-directory> [--dry-run]"
    exit 1
fi

if [ ! -d "$MEDIA_DIR" ]; then
    echo "Error: $MEDIA_DIR is not a directory"
    exit 1
fi

for post_dir in "$MEDIA_DIR"/*/; do
    banner_file="$post_dir/banner"

    # Skip if no extensionless "banner" file exists
    [ -f "$banner_file" ] || continue

    mime_type=$(file --mime-type -b "$banner_file")

    case "$mime_type" in
        image/jpeg)  ext="jpg" ;;
        image/png)   ext="png" ;;
        image/gif)   ext="gif" ;;
        image/webp)  ext="webp" ;;
        image/svg+xml) ext="svg" ;;
        *)
            echo "SKIP: $banner_file (unknown type: $mime_type)"
            continue
            ;;
    esac

    new_file="${banner_file}.${ext}"

    if [ "$DRY_RUN" = "--dry-run" ]; then
        echo "WOULD RENAME: $banner_file -> $new_file"
    else
        mv "$banner_file" "$new_file"
        echo "RENAMED: $banner_file -> $new_file"
    fi
done
