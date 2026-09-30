#!/bin/bash
# Shared helper: runs a command that emits tab-separated OK/ERR/DRY result lines
# from reprocess-banners.jsh, and prints a friendly summary. The caller must have
# already exported MEDIA_DIR (and optionally DRY_RUN) for the child process.

run_and_report() {
    local out ok=0 err=0 dry=0 total=0
    out="$(mktemp)"

    # Capture stdout+stderr together so nothing (errors, stack traces) is hidden.
    "$@" >"$out" 2>&1 || true

    while IFS=$'\t' read -r status path in_size out_size; do
        total=$((total+1))
        case "$status" in
            OK)
                local saved=$(( in_size > 0 ? (in_size - out_size) * 100 / in_size : 0 ))
                printf "OK    %s  %sKB -> %sKB (-%s%%)\n" \
                    "$path" "$((in_size/1024))" "$((out_size/1024))" "$saved"
                ok=$((ok+1)) ;;
            DRY)
                printf "WOULD %s  (%sKB)\n" "$path" "$((in_size/1024))"
                dry=$((dry+1)) ;;
            ERR)
                printf "ERROR %s  %s\n" "$path" "$in_size"
                err=$((err+1)) ;;
        esac
    done < <(grep -E '^(OK|ERR|DRY)' "$out")

    # If nothing matched, the command likely failed - show its full output.
    if [ "$total" -eq 0 ]; then
        echo "No banner results - full command output was:"
        sed 's/^/  | /' "$out"
    fi
    rm -f "$out"

    if [ -n "${DRY_RUN:-}" ]; then
        echo "Dry run: $dry banner(s) would be reprocessed, $err unreadable."
    else
        echo "Done: $ok converted, $err failed."
    fi
    [ "$err" -eq 0 ] && [ "$total" -gt 0 ]
}
