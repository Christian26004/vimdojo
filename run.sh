#!/bin/sh
# Compile and launch vimdojo. Needs only a JDK (17 or newer).
set -e
cd "$(dirname "$0")"
javac -d out src/vimdojo/*.java
exec java -cp out vimdojo.App "$@"
