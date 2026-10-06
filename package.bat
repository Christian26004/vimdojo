@echo off
rem Build dist\vimdojo\vimdojo.exe, a self-contained Windows app with its own Java runtime.
rem Needs a JDK (17 or newer) on the PATH. Copy the whole dist\vimdojo folder to install it.
setlocal
cd /d "%~dp0"
if exist out\package rmdir /s /q out\package
if exist dist\vimdojo rmdir /s /q dist\vimdojo
mkdir out\package\classes
mkdir out\package\jar

javac -d out\package\classes src\vimdojo\*.java || exit /b 1
jar --create --file out\package\jar\vimdojo.jar --main-class vimdojo.App -C out\package\classes . || exit /b 1
java -Djava.awt.headless=true tools\Icon.java 256 out\package\vimdojo.ico || exit /b 1

jpackage --type app-image --name vimdojo --app-version 1.0.0 ^
    --input out\package\jar --main-jar vimdojo.jar --main-class vimdojo.App ^
    --add-modules java.desktop --icon out\package\vimdojo.ico ^
    --dest dist || exit /b 1

echo built dist\vimdojo\vimdojo.exe
