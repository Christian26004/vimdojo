# vimdojo

A local trainer for Vim keybindings. Nineteen short lessons each introduce a few keys, drill them in a small built-in Vim, and score you on keystrokes and time. The first time through, a lesson is guided: eight tasks in teaching order, each saying which keys to use. After that it is practice: random tasks with only the keys listed, the rest up to you. Two mixes draw tasks from every lesson, one at random and one weighted toward the lessons you find hardest. Tasks are filled with different words and positions on every run. Everything runs and is stored on your own machine.

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
another is blocked by macOS because it is not notarized, and a build only matches the chip
(Apple Silicon or Intel) it was made on.

The Windows scripts have not yet been tried on a Windows machine. If one fails, the two commands
inside `run.bat` can be typed by hand.

## Using it

- The first time it opens, a short tour points out each part of the window in turn, with the
  rest blurred. Press **Enter** or click to move on, or **Esc** to skip it; it ends by starting
  your first lesson. `:tour` shows it again.
- Each lesson opens on an introduction card that lists its keys and plays a demonstration:
  the lesson's own tasks solved one key at a time, with each key shown as it is pressed.
- The first run of a lesson is **guided**: each task says exactly which keys to use. Every run
  after that is **practice**: tasks drawn at random from the lesson, saying only what to do,
  with the lesson's keys listed along the bottom. `:guided` brings the guided version back.
- Lessons unlock one at a time. Only lesson 1 can be played at first; scoring at least **50%
  efficiency** on a lesson unlocks the next one. Locked lessons are greyed out, but you can
  still open them to read their keys and watch the demonstration; they just can't be begun.
  The results screen says when the next one unlocks. Erasing your progress locks them again.
- At the end of the list, **random mix** gives ten tasks from lessons picked at random, and
  **weak spots** ten from the lessons you have tried, most often the ones where your recent
  efficiency is lowest.
- **Enter** starts a lesson from its introduction card; **Tab** restarts it.
- After the last task you see your efficiency (keystrokes against par), time, a per-task chart,
  and the par solution for every task. Click a task there (or pick it with `j`/`k` and press
  `r`) to watch par's keys replayed on its text, next to the keys you pressed.
- The bar along the bottom shows Vim's current mode, the keys that work on this screen, and
  links to the other screens.

Everything can be reached from the keyboard, the Vim way:

| Keys | What they do |
| --- | --- |
| `:docs` | the reference, over the current screen: every key, command, alias and mouse action, with a playable example for each Vim key |
| `:tour` | the tour of the app shown on first launch |
| `:lessons` `:stats` `:settings` | open that screen |
| `:lesson` `:ready` | back to the lesson in progress |
| `:next` `:prev` `:7` | another lesson, by direction or number |
| `:guided` | the current lesson's guided version again |
| `:restart` | start the lesson again |
| `:colo paper` | switch theme (ink, paper, moss, indigo) |
| `:dvorak` `:qwerty` | switch keyboard layout |
| `:q` | quit |
| `gt` `gT` | next or previous screen |
| `j` `k` `gg` `G` | move within a list |
| `h` `l` | previous or next lesson, from a lesson's introduction card |

The docs read like a manual page, written for someone who has never used Vim: they open with
the terms everything else relies on (mode, motion, operator, text object and so on), and each
key's entry explains exactly what it does and what its example shows. `/text` searches them, `n`
and `N` move between matches, `d` and `u` scroll a long entry, and `q`, `esc` or `:q` closes them
without quitting the app.

The settings screen lists these too, and holds the theme, the keyboard layout and an option to
erase progress. Erasing asks you to type the location of your data folder (`~/.vimdojo`) first,
the way GitHub asks for a repository's name before deleting it.

The stats screen includes an activity calendar: a year of days shaded by how many lessons you
finished on each, with your current and longest streak of consecutive days.

### Keyboard layout

Set **Keyboard layout** to dvorak in settings, or type `:dvorak` (or Vim's own `:set keymap=dvorak`), if
you use Vim's Dvorak keymap on a keyboard your system treats as QWERTY. It works the way that
Vim option does: text you insert, search patterns and the character given to `f`, `t` and `r`
come out in Dvorak, while normal-mode commands such as `h j k l` and `dw` stay on their usual
keys. `:qwerty` or `:set keymap=` switches back.

The setting assumes your system is set to QWERTY. Leave it on qwerty if your whole system is
set to Dvorak: the keys already arrive as Dvorak, and
vimdojo needs no setting.

The built-in Vim follows Neovim's defaults and covers only the keys the lessons teach: there are
no `:` commands, macros, or marks, and `/` searches for literal text.

## Your data

Results and settings are plain text files in a `.vimdojo` folder in your home directory:
`~/.vimdojo` on macOS, `C:\Users\<you>\.vimdojo` on Windows. Delete the folder to start fresh.

To keep them somewhere else, for example a synced folder, choose **Data folder** in settings
and pick a folder in the window that opens: a `.vimdojo` folder is made inside it and your
files move there. vimdojo then remembers the place in a one-line file, `~/.vimdojo-location`,
which goes away again if you move the folder back to your home folder.

## Tests

```
./test.sh
```

`./test.sh compare` also checks the built-in Vim against a real `nvim`, if one is installed.
