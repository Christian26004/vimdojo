# Progress for the build scripts, so a long step doesn't look like a hang. Each step shows a
# spinner and a running timer, then a tick and how long it took. A step's own output is hidden
# unless it fails. When the output isn't a terminal (a log, CI) it prints plain lines instead.
#
# Use: set total to the number of steps, then: step "What it does" command args...

progress_log=$(mktemp)
progress_n=0
progress_pid=
progress_began=$(date +%s)
if [ -t 1 ]; then
    progress_tty=1
else
    progress_tty=
fi

# Stops a step left running and gives the cursor back. Scripts with their own exit trap call it.
progress_cleanup() {
    if [ -n "$progress_pid" ]; then
        kill "$progress_pid" 2>/dev/null || true
    fi
    if [ -n "$progress_tty" ]; then
        printf '\033[?25h'
    fi
    rm -f "$progress_log"
}
trap progress_cleanup EXIT
trap 'exit 130' INT TERM

step() {
    progress_label=$1
    shift
    progress_n=$((progress_n + 1))
    progress_title="[$progress_n/$total] $progress_label"
    progress_t0=$(date +%s)

    if [ -z "$progress_tty" ]; then
        echo "$progress_title"
        if ! "$@" >"$progress_log" 2>&1; then
            cat "$progress_log"
            echo "failed: $progress_label"
            exit 1
        fi
        return
    fi

    printf '\033[?25l'
    "$@" >"$progress_log" 2>&1 &
    progress_pid=$!
    progress_i=0
    while kill -0 "$progress_pid" 2>/dev/null; do
        case $((progress_i % 10)) in
            0) progress_frame='⠋' ;; 1) progress_frame='⠙' ;; 2) progress_frame='⠹' ;;
            3) progress_frame='⠸' ;; 4) progress_frame='⠼' ;; 5) progress_frame='⠴' ;;
            6) progress_frame='⠦' ;; 7) progress_frame='⠧' ;; 8) progress_frame='⠇' ;;
            *) progress_frame='⠏' ;;
        esac
        printf '\r\033[K  \033[33m%s\033[0m %s  \033[2m%ss\033[0m' "$progress_frame" \
            "$progress_title" "$(($(date +%s) - progress_t0))"
        progress_i=$((progress_i + 1))
        sleep 0.1
    done
    if wait "$progress_pid"; then
        progress_status=0
    else
        progress_status=$?
    fi
    progress_pid=
    printf '\033[?25h'

    if [ "$progress_status" -ne 0 ]; then
        printf '\r\033[K  \033[31m✗\033[0m %s\n\n' "$progress_title"
        cat "$progress_log"
        exit "$progress_status"
    fi
    printf '\r\033[K  \033[32m✓\033[0m %s  \033[2m%ss\033[0m\n' "$progress_title" \
        "$(($(date +%s) - progress_t0))"
}

# The closing line, with the time the whole build took.
progress_done() {
    if [ -n "$progress_tty" ]; then
        printf '\n  \033[1m%s\033[0m  \033[2min %ss\033[0m\n' "$1" "$(($(date +%s) - progress_began))"
    else
        echo "$1"
    fi
}
