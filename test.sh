#!/bin/sh
# Compile and run the checks. "./test.sh compare" also checks the emulator against a real nvim.
set -e
cd "$(dirname "$0")"
javac -d out/test src/vimdojo/*.java test/vimdojo/*.java
java -cp out/test vimdojo.LessonTest
rm -rf out/test-data
java -Djava.awt.headless=true -Dvimdojo.dir=out/test-data -cp out/test vimdojo.NavTest
if [ "$1" = "compare" ]; then
    java -cp out/test vimdojo.VimCompare
fi
