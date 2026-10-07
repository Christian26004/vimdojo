#!/bin/sh
# Build dist/vimdojo.app, a self-contained macOS app with its own Java runtime.
set -e
cd "$(dirname "$0")"
. tools/progress.sh
# The bundle is assembled outside the project: folders synced by iCloud tag new files with
# Finder metadata, which makes code signing fail.
stage=$(mktemp -d)
trap 'rm -rf "$stage"; progress_cleanup' EXIT
rm -rf out/package dist/vimdojo.app
mkdir -p out/package/classes out/package/jar out/package/icon.iconset dist

icons() {
    for size in 16 32 128 256 512; do
        java -Djava.awt.headless=true tools/Icon.java $size \
            out/package/icon.iconset/icon_${size}x${size}.png
        java -Djava.awt.headless=true tools/Icon.java $((size * 2)) \
            out/package/icon.iconset/icon_${size}x${size}@2x.png
    done
    iconutil -c icns out/package/icon.iconset -o out/package/vimdojo.icns
}

total=5
step "Compiling the source" javac -d out/package/classes src/vimdojo/*.java
step "Packing the jar" jar --create --file out/package/jar/vimdojo.jar \
    --main-class vimdojo.App -C out/package/classes .
step "Drawing the icon" icons
step "Building the app and its Java runtime" \
    jpackage --type app-image --name vimdojo --app-version 1.0.0 \
    --input out/package/jar --main-jar vimdojo.jar --main-class vimdojo.App \
    --add-modules java.desktop --icon out/package/vimdojo.icns \
    --java-options -Dapple.awt.application.appearance=system \
    --dest "$stage"
step "Copying it to dist" ditto "$stage/vimdojo.app" dist/vimdojo.app

progress_done "Built dist/vimdojo.app"
