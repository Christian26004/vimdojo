@echo off
rem Build dist\vimdojo\vimdojo.exe, a self-contained Windows app with its own Java runtime.
rem Needs a JDK (17 or newer). Copy the whole dist\vimdojo folder to install it.
setlocal
cd /d "%~dp0"

rem Opened by double-clicking, the window would close the moment this ends, taking any error
rem with it; so it waits for a key first.
echo %cmdcmdline% | findstr /i /c:"%~nx0" >nul && set "CLICKED=1"

rem Some Java installers put only java and javac on the PATH, not jar or jpackage, so every
rem tool is run from the JDK's own bin folder: JAVA_HOME's, or the one java on the PATH is in.
set "JDK_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JDK_BIN=%JAVA_HOME%\bin"
if defined JDK_BIN goto found
where java >nul 2>nul || goto nojava
set "JAVA_DIR="
for /f "tokens=2 delims==" %%h in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home ="') do set "JAVA_DIR=%%h"
if not defined JAVA_DIR goto nojdk
set "JDK_BIN=%JAVA_DIR:~1%\bin"
:found
if not exist "%JDK_BIN%\jpackage.exe" goto nojdk
echo Using the JDK in %JDK_BIN%

if exist out\package rmdir /s /q out\package
if exist dist\vimdojo rmdir /s /q dist\vimdojo
mkdir out\package\classes
mkdir out\package\jar

rem Each step says what it is doing first, so a long one doesn't look like a hang.
echo [1/4] Compiling the source...
"%JDK_BIN%\javac.exe" -d out\package\classes src\vimdojo\*.java || goto failed
echo [2/4] Packing the jar...
"%JDK_BIN%\jar.exe" --create --file out\package\jar\vimdojo.jar --main-class vimdojo.App -C out\package\classes . || goto failed
echo [3/4] Drawing the icon...
"%JDK_BIN%\java.exe" -Djava.awt.headless=true tools\Icon.java 256 out\package\vimdojo.ico || goto failed

echo [4/4] Building the app and its Java runtime, which takes a minute...
"%JDK_BIN%\jpackage.exe" --type app-image --name vimdojo --app-version 1.0.0 ^
    --input out\package\jar --main-jar vimdojo.jar --main-class vimdojo.App ^
    --add-modules java.desktop --icon out\package\vimdojo.ico ^
    --dest dist || goto failed

echo.
echo Built dist\vimdojo\vimdojo.exe
if defined CLICKED pause
exit /b 0

:nojava
echo.
echo Java isn't installed, or isn't on the PATH. Install a JDK, version 17 or newer, for
echo example Eclipse Temurin from https://adoptium.net, then run this again.
goto stop

:nojdk
echo.
echo Couldn't find jpackage, the tool that builds the app. It comes with a full JDK, version 17
echo or newer, for example Eclipse Temurin from https://adoptium.net. If one is installed, set
echo JAVA_HOME to its folder, the one with bin inside, and run this again.
goto stop

:failed
echo.
echo The build stopped at the step above; its error is printed just before this.

:stop
if defined CLICKED pause
exit /b 1
