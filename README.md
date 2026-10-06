# vimdojo

A local trainer for Vim keybindings. Sixteen short lessons each introduce a few keys, drill them in a small built-in Vim, and score you on keystrokes and time. Everything runs and is stored on your own machine.

## Requirements

A JDK, version 17 or newer, with `java` and `javac` on your PATH. There are no other dependencies.
Check with:

```
java -version
```

If that fails, install a JDK such as [Eclipse Temurin](https://adoptium.net/).

## Run it

Download or clone this folder, open a terminal in it, and run the script for your system. Each
one compiles the source and opens the app.

**macOS (or Linux)**

```
./run.sh
```

**Windows**

```
run.bat
```

You can also double-click `run.bat` in File Explorer.

## Build a standalone app

These build an app that carries its own Java runtime, so it starts with a double-click and no
longer needs the JDK.

**macOS**

```
./package.sh
```

This creates `dist/vimdojo.app`. Drag it into your Applications folder.

**Windows**

```
package.bat
```

This creates the folder `dist\vimdojo`, with `vimdojo.exe` inside. Keep the whole folder together;
the `.exe` does not work on its own. Move the folder wherever you like and make a shortcut to the
`.exe`.

Build the app on the machine that will use it. An app built on one Mac and downloaded onto
another is blocked by macOS because it is not notarised, and a build only matches the chip
(Apple Silicon or Intel) it was made on.

The Windows scripts have not yet been tried on a Windows machine. If one fails, the two commands
inside `run.bat` can be typed by hand.

## Using it

- **Enter** starts a lesson from its introduction card; **Tab** restarts it.
- After the last task you see your efficiency (keystrokes against par), time, and a per-task chart.
- **lessons** (top right) lists all 16 with your best for each: `j`/`k` to move, Enter to start.
- **stats** shows totals, a best per lesson, and every past run.

The built-in Vim follows Neovim's defaults and covers only the keys the lessons teach: there are
no `:` commands, macros, or marks, and `/` searches for literal text.

## Your data

Results and settings are plain text files in a `.vimdojo` folder in your home directory:
`~/.vimdojo` on macOS, `C:\Users\<you>\.vimdojo` on Windows. Delete the folder to start fresh.

## Tests

```
./test.sh
```

`./test.sh compare` also checks the built-in Vim against a real `nvim`, if one is installed.
