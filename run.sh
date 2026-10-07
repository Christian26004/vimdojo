#!/bin/sh
# Compile and launch vimdojo. Needs only a JDK (17 or newer).
set -e
cd "$(dirname "$0")"
. tools/progress.sh
total=1
step "Compiling the source" javac -d out src/vimdojo/*.java
echo
echo "  Opening vimdojo..."
progress_cleanup
trap - EXIT INT TERM
exec java -cp out vimdojo.App "$@"
