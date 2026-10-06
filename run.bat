@echo off
rem Compile and launch vimdojo on Windows. Needs only a JDK (17 or newer) on the PATH.
setlocal
cd /d "%~dp0"
javac -d out src\vimdojo\*.java || exit /b 1
java -cp out vimdojo.App %*
