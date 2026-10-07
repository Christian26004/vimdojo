@echo off
rem Compile and launch vimdojo on Windows. Needs only a JDK (17 or newer) on the PATH.
setlocal
cd /d "%~dp0"
rem Opened by double-clicking, the window would close on an error before it could be read.
echo %cmdcmdline% | findstr /i /c:"%~nx0" >nul && set "CLICKED=1"
echo Compiling the source...
javac -d out src\vimdojo\*.java || goto failed
echo Opening vimdojo...
java -cp out vimdojo.App %* || goto failed
exit /b 0

:failed
echo.
echo vimdojo stopped with the error above. It needs a JDK, version 17 or newer, on the PATH.
if defined CLICKED pause
exit /b 1
