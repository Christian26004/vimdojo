package vimdojo;

import java.util.ArrayList;
import java.util.List;

/**
 * The reference: every key and command the app responds to, written for someone who has never
 * used Vim. It opens with the words the rest of it relies on. Then come the keys the lessons
 * teach, in lesson order, with short descriptions taken from the lessons themselves so the two
 * never drift apart. After them come the rest of the built-in Vim's keys, then the app's own
 * commands, keys and mouse actions. Anything Vim does has a short example to play back.
 */
final class Docs {
    /** The section that explains terms rather than keys. */
    static final String TERMS = "start here: what the words mean";

    /**
     * One documented key or term. The example starts from {@code start}, where {@code |} marks
     * the cursor, and plays {@code keys}; entries about the app itself have no example. The
     * explanation says exactly what happens, one paragraph per line, and the extra lines list
     * other spellings and details. Key names go in backticks.
     */
    record Entry(String group, String key, String does, String start, String keys,
                 String explain, List<String> more) {
        boolean hasDemo() {
            return start != null;
        }

        /** A word to learn rather than a key to press. */
        boolean isTerm() {
            return group.equals(TERMS);
        }

        /** A fresh Vim at the start of the example. */
        Vim vim() {
            Task task = Task.edit("", "", start, "", "");
            return new Vim(task.start(), task.row(), task.col());
        }
    }

    private static final String FIVE = "one\ntwo\nthree\nfour\nfive";
    private static final String GREEK = "alpha beta\ngamma alpha\nbeta gamma";

    /** Terms used everywhere else: the term, a one-line meaning, then the explanation. */
    private static final String[][] WORDS = {
        {"reading keys", "how to read the key names in boxes",
            "Each box is one key on your keyboard. Press them one after another, from left to "
                + "right, never at the same time: `dw` means press `d`, let go, then press `w`.\n"
                + "A capital letter means hold shift while you press it: `G` is shift and g, "
                + "`A` is shift and a. A lowercase letter means the key on its own.\n"
                + "Some boxes name a special key: `esc` is the escape key, `enter` the return "
                + "key, `bksp` backspace, `tab` the tab key and `space` the space bar. "
                + "`ctrl-r` means hold the control key and press r.\n"
                + "In `fx`, `tx` and `rx` the x stands for any character you choose: `f(` "
                + "looks for a bracket and `ra` types an a. Numbers are the same: in `7G` and "
                + "`3j`, any number works."},
        {"cursor", "the highlighted block where everything happens",
            "The cursor marks your place in the text. Every key acts at the cursor: moves start "
                + "from it, deletes remove text at it, and new text appears at it.\n"
                + "In normal mode the cursor is a block that covers one character. It always "
                + "sits on a character, never between two. In insert mode it becomes a thin "
                + "bar between two characters, showing where the next letter you type will go."},
        {"normal mode", "keys are commands, not text",
            "Vim starts in normal mode, and you spend most of your time there. In this mode "
                + "pressing a letter does not type it. Each key is a command instead: `w` jumps "
                + "to the next word and `x` deletes a character.\n"
                + "The block in the bottom left of the window shows the mode. It says NORMAL "
                + "here. Pressing `esc` always brings you back to normal mode from any other "
                + "mode."},
        {"insert mode", "keys type text, like any other editor",
            "In insert mode the letters you press go into the text at the cursor, as in any "
                + "word processor. `bksp` deletes the character before the cursor and `enter` "
                + "starts a new line. Commands such as `w` and `x` do not work here: they would "
                + "just type a w or an x.\n"
                + "You enter insert mode with `i`, `a`, `I`, `A`, `o`, `O` or `c`, which differ "
                + "only in where the typing starts. Press `esc` when you have finished typing to "
                + "go back to normal mode. The block in the bottom left says INSERT meanwhile."},
        {"visual mode", "select some text first, then act on it",
            "`v` starts selecting single characters and `V` whole lines. The character or line "
                + "under the cursor is highlighted, and as you move the cursor the highlight "
                + "stretches from where you started to where the cursor is now.\n"
                + "Then press `d` to delete the highlighted text, `y` to copy it or `c` to "
                + "replace it with something you type. `esc` stops selecting and changes "
                + "nothing. The block in the bottom left says VISUAL or V-LINE meanwhile."},
        {"motion", "a key that moves the cursor",
            "A motion moves the cursor without changing the text. `h` `j` `k` `l`, `w` `b` `e`, "
                + "`0` `^` `$`, `gg` `G`, `f` `t` `F` `T` and `/` are all motions.\n"
                + "On its own a motion just moves. Typed straight after an operator such as `d`, "
                + "it tells the operator how far to reach instead: the operator acts on the text "
                + "between the cursor and the place the motion would have moved to."},
        {"operator", "d, c or y: a command that waits to be told which text",
            "There are three operators. `d` deletes text, `c` changes it (deletes it and puts "
                + "you in insert mode to type its replacement), and `y` yanks it (copies it).\n"
                + "Pressing an operator does nothing yet: Vim waits for you to say which text "
                + "you mean. You say it with a motion, a text object, or the same operator "
                + "again for the whole line. `dw` deletes from the cursor to the start of the "
                + "next word, `d$` deletes to the end of the line, `diw` deletes the word the "
                + "cursor is in, and `dd` deletes the whole line.\n"
                + "In visual mode you choose the text first, by selecting it, so there the "
                + "operator acts on the selection straight away.\n"
                + "Where these docs say an operator acts on some text, they mean it does its "
                + "job to that text: deletes it, changes it or copies it."},
        {"text object", "a whole thing around the cursor: a word, quotes, brackets",
            "A text object names a piece of text by what it is, not by where the cursor will "
                + "go. It is written as `i` or `a` followed by the kind of thing: `iw` a word, "
                + "`i\"` the inside of double quotes, `i(` the inside of round brackets.\n"
                + "`i` means inner: only what is inside, without the quotes or brackets around "
                + "it, or a word without its spaces. `a` means around: the quotes or brackets "
                + "as well, or the word and the space after it.\n"
                + "A text object covers the whole thing wherever the cursor is inside it, so "
                + "`ciw` changes the entire word even with the cursor in its middle.\n"
                + "Text objects only work straight after an operator or in visual mode. Typed "
                + "on their own, `i` and `a` start insert mode instead."},
        {"count", "a number typed first repeats the command",
            "Type a number, then the command, and the command happens that many times: `3j` "
                + "moves down three lines, `3x` deletes three characters and `2dd` deletes two "
                + "lines. The number appears in the bottom bar while you type it.\n"
                + "With an operator the number can go in front of the operator or in front of "
                + "the motion. `3dw` and `d3w` both delete three words.\n"
                + "In `7G` and `7gg` the number means something else: the line to go to."},
        {"word", "letters, digits and _, or a run of punctuation",
            "To `w`, `b`, `e`, `iw` and `aw` a word is either a run of letters, digits and "
                + "underscores, such as `total_2`, or a run of other symbols, such as `+=` or "
                + "`.`. Spaces and the end of a line separate words. So `items.count` is three "
                + "words: items, the dot, and count.\n"
                + "The capital versions `W`, `B`, `E`, `iW` and `aW` use a looser rule called a "
                + "WORD: everything up to a space counts as one, so `items.count` is a single "
                + "WORD."},
        {"change", "any edit to the text",
            "A change is anything that alters the text: a delete, a replace, or a stretch of "
                + "typing from entering insert mode until you press `esc`. Moving the cursor is "
                + "not a change.\n"
                + "This matters for two keys. `u` undoes one change at a time, so an entire "
                + "stretch of typing disappears in one go. `.` repeats your last change, "
                + "including the text you typed."},
        {"yank and put", "Vim's words for copy and paste",
            "To yank is to copy, with `y`. To put is to paste, with `p` or `P`. Deleting with "
                + "`d`, `c` or `x` also keeps a copy of what was deleted, so cutting and pasting "
                + "is simply delete, move, put.\n"
                + "The copy is kept in Vim's register, a clipboard inside Vim. It is separate "
                + "from your computer's clipboard, and it holds only the latest yank or delete.\n"
                + "Text copied as whole lines, with `yy`, `dd` or `V`, is put back as whole "
                + "lines: on a new line below the cursor's line with `p`, or above it with `P`. "
                + "Anything smaller is put inside the line, just after the cursor with `p` or "
                + "just before it with `P`."},
        {"par", "the fewest keys a fluent Vim user would need",
            "Each task has a par: the number of keys in a short, well-chosen solution. The "
                + "results screen shows that solution for every task, and `r` replays it.\n"
                + "Efficiency is par divided by the keys you pressed, as a percentage. 100% means "
                + "you matched par exactly. Below 100% you pressed extra keys, and above 100% you "
                + "found a shorter way than par."},
        {"guided and practice", "how a lesson changes after your first time",
            "The first time you take a lesson it is guided: eight short tasks in teaching "
                + "order, each saying exactly which keys to use. The introduction card says "
                + "GUIDED at the top.\n"
                + "Every time after that it is practice: eight tasks drawn at random from the "
                + "lesson, with new words and positions each time. The tasks only say what to "
                + "do, not how; the lesson's keys are listed along the bottom. The card says "
                + "PRACTICE. `:guided` brings the guided version back whenever you like."},
        {"locked lessons", "each lesson opens at 50% on the one before",
            "Lessons unlock one at a time. At first only lesson 1 can be played. When you "
                + "finish a lesson with at least 50% efficiency, the next one unlocks, and every "
                + "lesson you have unlocked stays that way.\n"
                + "Locked lessons are greyed out, but you can still open them: their card shows "
                + "the keys and plays the demonstration, faded, with a line saying which lesson "
                + "to pass. Pressing `enter` on it says the same in the bottom bar instead of "
                + "starting. The results screen tells you when a run has unlocked the next "
                + "lesson.\n"
                + "The two mixes unlock once you have passed lesson 1, and the random mix only "
                + "uses unlocked lessons. Erasing your progress in settings locks everything "
                + "after lesson 1 again."},
        {"mixes", "random mix and weak spots, at the end of the lessons",
            "Two entries at the end of the lessons list mix tasks from every lesson, with no "
                + "instructions. The bottom bar shows the keys of the lesson each task comes "
                + "from.\n"
                + "Random mix picks each task's lesson at random. Weak spots picks from the "
                + "lessons you have tried, most often the ones where your recent efficiency is "
                + "lowest, so it keeps you working on what you find hardest. Its introduction "
                + "card names the lessons it currently favours."},
        {"examples", "every Vim key here comes with an example that plays",
            "When you select a Vim key, its example plays on the right by itself. The box shows "
                + "the text, with the cursor as a highlighted block. Under it the keys appear "
                + "one at a time as they are pressed, with the newest one outlined. The label "
                + "next to EXAMPLE shows the mode Vim is in at that moment.\n"
                + "When the example finishes, it pauses and starts again from the beginning."},
    };

    /**
     * Lesson id, then for each key the lesson lists, in order: the example's start, the keys it
     * plays, and the explanation. The docs list them in lesson order, whatever the order here.
     */
    private static final Object[][] EXAMPLES = {
        {"hjkl",
            "the quick b|rown fox", "hhh",
            "Moves the cursor one character to the left, staying on the same line. At the "
                + "very start of a line nothing happens: `h` never moves onto the line above.\n"
                + "A count moves further: `5h` goes five characters left, or as far as the start "
                + "of the line if that comes first.\n"
                + "In the example the cursor starts on the r of brown. Each `h` moves it one "
                + "character left, so three of them land it on the k of quick.",
            "the |quick brown\njumps over\nthe lazy dog", "jj",
            "Moves the cursor down to the next line. It tries to stay in the same column. If "
                + "the next line is too short for that, it goes to that line's last character, "
                + "but remembers the column, so moving on to a longer line returns to it. On the "
                + "last line nothing happens.\n"
                + "In the example the cursor starts on the q of quick, five columns in. Two "
                + "`j`s take it down two lines to the l of lazy, in the same column.",
            "the quick brown\njumps over\nthe |lazy dog", "kk",
            "Moves the cursor up to the line above, trying to stay in the same column, just "
                + "as `j` does going down. On the first line nothing happens.\n"
                + "In the example the cursor starts on the l of lazy on the third line. Two "
                + "`k`s take it up to the q of quick on the first line.",
            "the |quick brown fox", "lll",
            "Moves the cursor one character to the right, staying on the same line. On the "
                + "last character of a line nothing happens: `l` never moves onto the next "
                + "line.\n"
                + "In the example the cursor starts on the q of quick and three `l`s move it "
                + "to the c."},
        {"words",
            "|the quick brown fox jumps", "www",
            "Jumps the cursor forward to the first letter of the next word. From the last "
                + "word of a line it goes on to the first word of the next line. See the word "
                + "term at the top: punctuation such as a dot counts as a word of its own.\n"
                + "In the example the cursor starts on the t of the. Three `w`s jump to quick, "
                + "then brown, then fox.",
            "the quick brown fox |jumps", "bbb",
            "Jumps the cursor back to the first letter of a word. If the cursor is in the "
                + "middle of a word, the first `b` goes to the start of that same word. If it is "
                + "already at the start, `b` goes back to the start of the word before.\n"
                + "In the example the cursor starts on the j of jumps. Three `b`s go back to "
                + "fox, then brown, then quick.",
            "|the quick brown fox", "eee",
            "Jumps the cursor forward to the last letter of a word. If the cursor is in the "
                + "middle of a word, `e` goes to the end of that word. If it is already on the "
                + "last letter, `e` goes on to the end of the next word.\n"
                + "In the example the cursor starts on the t of the. Three `e`s land on the e "
                + "of the, the k of quick and the n of brown."},
        {"line",
            "  return total |+ count;", "0",
            "Jumps the cursor to the very first column of the line, whatever is there. If "
                + "the line begins with spaces, as indented code does, `0` lands on the first "
                + "space.\n"
                + "In the example the line is indented by two spaces. `0` moves the cursor from "
                + "the + to the first of those spaces.",
            "  return total |+ count;", "^",
            "Jumps the cursor to the first character of the line that is not a space or tab. "
                + "On an indented line this skips past the indentation to where the text "
                + "begins, which is usually more useful than `0`.\n"
                + "In the example `^` moves the cursor from the + to the r of return, skipping "
                + "the two spaces of indentation.",
            "  return |total + count;", "$",
            "Jumps the cursor to the last character of the line.\n"
                + "In the example `$` moves the cursor from the t of total to the semicolon at "
                + "the end of the line."},
        {"jumps",
            at(FIVE, 14), "gg",
            "Jumps the cursor to the first line of the text, keeping it in the same column if "
                + "that line is long enough.\n"
                + "In the example the cursor starts on line four, four, and `gg` takes it up to "
                + "line one, one.",
            at(FIVE, 4), "G",
            "Jumps the cursor to the last line of the text, keeping it in the same column if "
                + "that line is long enough. Press shift and g.\n"
                + "In the example the cursor starts on line two and `G` takes it down to the "
                + "last line, five.",
            "|" + FIVE, "4G",
            "Type a line number, then `G`, to jump straight to that line: `4G` goes to line "
                + "four and `12G` to line twelve. The line numbers are shown down the left of "
                + "the text. A number past the end goes to the last line.\n"
                + "In the example `4G` moves the cursor from line one to line four.",
            "|" + FIVE, "3j",
            "A number typed before a motion repeats the motion that many times. `3j` moves "
                + "down three lines, the same as pressing `j` three times but with fewer keys. "
                + "It works with every motion: `4w` jumps four words and `2b` goes back two.\n"
                + "In the example `3j` moves the cursor from line one to line four."},
        {"find",
            "|pack my box with jugs", "fx",
            "Press `f`, then any character: the cursor jumps right, onto the next place that "
                + "character appears in the current line. It never looks at other lines, and it "
                + "matches the exact character, so capitals count. If the character isn't "
                + "there, nothing happens.\n"
                + "In the example `fx` jumps from the p at the start of the line onto the x of "
                + "box.",
            "|pack my box with jugs", "tx",
            "Like `f`, but the cursor stops on the character just before the one you typed. "
                + "The t stands for till: up to, not including. It is useful with an operator: "
                + "`dt)` deletes everything up to a bracket but leaves the bracket.\n"
                + "In the example `tx` jumps from the p onto the o of box, just before the x.",
            "pack my box with |jugs", "FxTp",
            "`F` and `T`, with shift, work like `f` and `t` but search to the left of the "
                + "cursor. `F` lands on the character itself and `T` stops just after it, on "
                + "the side nearer where you started.\n"
                + "In the example the cursor starts on the j of jugs. `Fx` jumps back onto the "
                + "x of box. Then `Tp` searches left for a p and stops just after it, on the a "
                + "of pack.",
            "|a-b-c-d-e", "f-;;",
            "Repeats your last `f`, `t`, `F` or `T` with the same character, in the same "
                + "direction, so you don't have to type the character again. Press it as many "
                + "times as you need.\n"
                + "In the example `f-` jumps to the first dash, and each `;` jumps on to the "
                + "next dash.",
            "|a-b-c-d-e", "f-;;,",
            "Repeats your last `f`, `t`, `F` or `T` in the opposite direction. Use it when "
                + "`;` has taken you one step too far.\n"
                + "In the example `f-` and two `;`s reach the third dash, then `,` goes back to "
                + "the second."},
        {"chars",
            "the q|uuick fox", "x",
            "Deletes the character the cursor is on. The rest of the line closes up, and the "
                + "cursor stays put, now on the character that followed. A count deletes more: "
                + "`3x` deletes three characters.\n"
                + "In the example the word is misspelled quuick. The cursor is on the first u "
                + "and `x` deletes it, leaving quick.",
            "the quick br|awn fox", "ro",
            "Press `r`, then any character, to replace the character under the cursor with "
                + "it. Only that one character changes, and you stay in normal mode, so there is "
                + "no need to press `esc` afterwards.\n"
                + "In the example the word is misspelled brawn. The cursor is on the a, and "
                + "`ro` replaces it with an o to make brown.",
            "the |very quick fox", "dwu",
            "Undoes your last change, putting the text back the way it was before it. Press "
                + "it again to undo the change before that, and so on. A whole stretch of typing, "
                + "from entering insert mode to pressing `esc`, is undone in one go. `ctrl-r` "
                + "redoes what you undid.\n"
                + "In the example `dw` deletes the word very, then `u` brings it back."},
        {"insert",
            "the quick br|wn fox", "io<esc>",
            "Switches to insert mode with the typing position just before the character the "
                + "cursor is on. Everything you type now goes into the text until you press "
                + "`esc`.\n"
                + "In the example the word is brwn, missing its o. The cursor is on the w, so "
                + "`i` starts typing just before it, `o` types the missing letter, and `esc` "
                + "goes back to normal mode.",
            "the qui|c brown fox", "ak<esc>",
            "Switches to insert mode with the typing position just after the character the "
                + "cursor is on. The a stands for append. Everything you type now goes into the "
                + "text until you press `esc`.\n"
                + "In the example the word is quic, missing its k. The cursor is on the c, so "
                + "`a` starts typing just after it, `k` types the letter, and `esc` goes back to "
                + "normal mode.",
            "hell|o world", "a,<esc>",
            "Leaves insert mode, or visual mode, and goes back to normal mode, where keys are "
                + "commands again. After typing, the cursor steps back onto the last character "
                + "you typed. That is normal: the block cursor has to sit on a character.\n"
                + "In normal mode, `esc` cancels a command you have only half typed, such as a "
                + "`d` that is still waiting for its motion.\n"
                + "In the example `a` starts typing after hello, `,` types a comma, and `esc` "
                + "returns to normal mode."},
        {"open",
            "total |= 0;", "Ilet <esc>",
            "Switches to insert mode at the start of the line's text, wherever the cursor is "
                + "in the line. It skips the indentation, like `^`. Press `esc` when you have "
                + "finished typing.\n"
                + "In the example the cursor is on the =, and `I` starts typing before total. "
                + "Typing let and a space, then `esc`, makes it let total = 0;.",
            "return |total", "A;<esc>",
            "Switches to insert mode at the end of the line, wherever the cursor is in it. "
                + "Press `esc` when you have finished typing.\n"
                + "In the example the cursor is on the t of total, and `A` starts typing after "
                + "the last character. `;` adds a semicolon, then `esc`.",
            "one\n|two\nfour", "othree<esc>",
            "Opens a new, empty line below the cursor's line and switches to insert mode on "
                + "it. The new line starts with the same indentation as the line above. Press "
                + "`esc` when you have finished typing.\n"
                + "In the example the line three is missing. With the cursor on two, `o` opens "
                + "a line under it, and three is typed there.",
            "|two\nthree", "Oone<esc>",
            "Opens a new, empty line above the cursor's line and switches to insert mode on "
                + "it. Press shift and o. Press `esc` when you have finished typing.\n"
                + "In the example the cursor is on the first line, two. `O` opens a line above "
                + "it, and one is typed there."},
        {"delete",
            "the |very quick fox", "dw",
            "Deletes from the cursor to the start of the next word: the rest of this word "
                + "and the space after it. With the cursor on the first letter, that is the "
                + "whole word. It is the delete operator `d` followed by the motion `w`.\n"
                + "In the example the cursor is on the v of very, and `dw` deletes very and its "
                + "space, leaving the quick fox.",
            "keep\n|drop this\nkeep", "dd",
            "Deletes the whole line the cursor is on, wherever the cursor is in it. The "
                + "lines below move up to fill the gap. The deleted line is kept in the "
                + "register, so `p` can put it back elsewhere.\n"
                + "In the example the cursor is on the middle line, drop this, and `dd` removes "
                + "it.",
            "total = 0;| // old", "D",
            "Deletes from the cursor to the end of the line, including the character the "
                + "cursor is on. Everything to the left stays. It is the same as `d$`.\n"
                + "In the example the cursor is on the space after the semicolon, and `D` "
                + "deletes the old comment, leaving total = 0;.",
            "sum(a, b|, c, d)", "dt)",
            "`d` is an operator: it deletes whatever text you name next, and it waits until "
                + "you do. Follow it with any motion and it deletes from the cursor to where "
                + "that motion would have gone. If the motion lands on a character, as `e`, `f` "
                + "and `$` do, that character is deleted too. `t` stops before its character, "
                + "so that one stays.\n"
                + "For example: `d0` deletes back to the start of the line, `de` to the end of "
                + "the word, `dG` everything from this line to the end, and `dj` this line and "
                + "the one below.\n"
                + "In the example the cursor is on the comma after b. `dt)` deletes up to the "
                + "closing bracket, leaving sum(a, b)."},
        {"change",
            "the |slow fox", "cwquick<esc>",
            "Deletes from the cursor to the end of the word and switches to insert mode, so "
                + "you can type a replacement. Unlike `dw`, it leaves the space after the word "
                + "alone, so your new word doesn't run into the next. Press `esc` when you "
                + "have finished typing.\n"
                + "In the example the cursor is on the s of slow. `cw` deletes slow, quick is "
                + "typed in its place, and `esc` finishes.",
            "one\n|tow\nthree", "cctwo<esc>",
            "Deletes all the text on the cursor's line, keeping the line itself and its "
                + "indentation, and switches to insert mode so you can type the line again. "
                + "Press `esc` when you have finished.\n"
                + "In the example the middle line says tow. `cc` empties it and two is typed "
                + "instead.",
            "return |a + b;", "Ctotal;<esc>",
            "Deletes from the cursor to the end of the line and switches to insert mode, so "
                + "you can type a new ending. It is the same as `c$`. Press `esc` when you have "
                + "finished.\n"
                + "In the example the cursor is on the a. `C` deletes a + b; and total; is "
                + "typed instead.",
            "color: |red; /* keep */", "ct;blue<esc>",
            "`c` is an operator, like `d`: follow it with any motion or text object and it "
                + "deletes that text, then switches to insert mode for you to type the "
                + "replacement. Press `esc` when you have finished.\n"
                + "In the example the cursor is on the r of red. `ct;` deletes up to the "
                + "semicolon, blue is typed in its place, and the comment after it is "
                + "untouched."},
        {"put",
            "|echo hello\ndone", "yyp",
            "Yanks, that is copies, the whole line the cursor is on into the register. The "
                + "text doesn't change and nothing seems to happen, but the copy is ready for "
                + "`p` or `P` to put somewhere.\n"
                + "In the example `yy` copies the line echo hello, then `p` puts the copy on a "
                + "new line below it.",
            "|very good", "ywP",
            "Yanks from the cursor to the start of the next word: the word and the space "
                + "after it. It is the yank operator `y` followed by the motion `w`.\n"
                + "In the example `yw` copies very and its space, then `P` puts the copy before "
                + "the cursor, making very very good.",
            "|second\nfirst\nthird", "ddp",
            "Puts, that is pastes, whatever was last yanked or deleted. Whole lines go on a "
                + "new line below the cursor's line. Anything smaller goes into the line, just "
                + "after the cursor. A count puts several copies.\n"
                + "In the example `dd` deletes the line second, which moves the cursor onto "
                + "first. `p` puts second back below it, so the two lines swap places.",
            "two\n|one\nthree", "ddkP",
            "Like `p`, but on the other side: whole lines go on a new line above the "
                + "cursor's line, and anything smaller goes just before the cursor. Press "
                + "shift and p.\n"
                + "In the example `dd` deletes the line one, `k` moves up to two, and `P` puts "
                + "one back above it.",
            "|b\na\nc", "ddp",
            "Deleting with `d`, `c` or `x` keeps a copy of what was deleted in the register, "
                + "just as yanking does. So to move text: delete it, move the cursor, and put it "
                + "back with `p` or `P`.\n"
                + "In the example `dd` deletes the line b, and `p` puts it back below a, putting "
                + "the lines in order."},
        {"counts",
            "the |very very quick fox", "d2w",
            "Deletes two words: the operator `d`, then a count of 2, then the motion `w`. It "
                + "deletes as far as `2w` would move, so the words and the space after each go.\n"
                + "In the example the cursor is on the first very, and `d2w` deletes very very "
                + "and their spaces.",
            "keep\n|drop\ndrop\ndrop\nkeep", "3dd",
            "Deletes three whole lines: the cursor's line and the two below it. A count in "
                + "front of a doubled operator such as `dd` or `yy` means that many lines.\n"
                + "In the example the cursor is on the first drop, and `3dd` deletes all three "
                + "drop lines.",
            "the |dark red fox", "c2wbrown<esc>",
            "Deletes two words and switches to insert mode to type their replacement. As "
                + "with `cw`, the space after the last word stays. Press `esc` when you have "
                + "finished.\n"
                + "In the example `c2w` deletes dark red and brown is typed instead.",
            "|ho ho ho and a bottle", "y3wP",
            "Every operator, count and motion can be combined. Say what to do, how many "
                + "times, and how far: `y3w` yanks three words, `d4j` deletes this line and four "
                + "more, `c2e` changes to the end of the second word. The count can go before "
                + "the operator instead: `3yw` is the same as `y3w`.\n"
                + "In the example `y3w` copies ho ho ho and `P` puts the copy before the cursor."},
        {"objects",
            "the bro|ken fox", "ciwbrown<esc>",
            "Inner word: the whole word the cursor is in, from its first letter to its last, "
                + "without the spaces around it. Wherever the cursor is in the word, the whole "
                + "word is used. Use it after an operator, for example `ciw` or `diw`.\n"
                + "In the example the cursor is in the middle of broken. `ciw` deletes the "
                + "whole word and brown is typed in its place.",
            "the very qu|ick fox", "daw",
            "A word: the whole word the cursor is in, plus the space after it, or the space "
                + "before it if there is none after. Use it to delete a word without leaving "
                + "two spaces behind: `daw`.\n"
                + "In the example the cursor is inside quick, and `daw` deletes quick and one "
                + "space, leaving the very fox.",
            "say(\"hel|lo there\")", "ci\"goodbye<esc>",
            "Inside double quotes: the text between a pair of \" marks on the cursor's line, "
                + "without the quote marks themselves. The cursor can be anywhere inside the "
                + "quotes, or before them on the same line.\n"
                + "In the example the cursor is inside \"hello there\". `ci\"` deletes hello "
                + "there, keeping both quote marks, and goodbye is typed in its place.",
            "call(alpha, |beta)", "di(",
            "Inside round brackets: everything between the ( and the matching ) around the "
                + "cursor, without the brackets themselves. If brackets are nested, it uses the "
                + "innermost pair around the cursor. `i)` and `ib` mean the same.\n"
                + "In the example the cursor is on beta. `di(` deletes alpha, beta and leaves "
                + "call().",
            "if (x |> 10) {", "ci(ready<esc>",
            "A text object on its own does nothing. Type it straight after an operator, and "
                + "the operator acts on that object. `ciw` changes a word, `di(` deletes inside "
                + "brackets, and `ya\"` yanks a quoted string with its quotes. In visual mode, "
                + "typing a text object selects it.\n"
                + "In the example `ci(` deletes x > 10 between the brackets, and ready is typed "
                + "instead."},
        {"repeat",
            "|no no no yes", "dw..",
            "Repeats your last change exactly, at the cursor, including any text you typed. "
                + "If your last change was `cwquick<esc>`, then `.` changes the word at the "
                + "cursor to quick too. Motions aren't changes, so they are never repeated.\n"
                + "In the example `dw` deletes the first no, then each `.` deletes another one, "
                + "leaving yes.",
            "the |very quick fox", "dwu",
            "Undoes your last change, putting the text back the way it was before it. Press "
                + "it again to undo the change before that. A whole stretch of typing counts as "
                + "one change.\n"
                + "In the example `dw` deletes very and `u` brings it back.",
            "the |very quick fox", "dwu<c-r>",
            "Redoes the change that `u` just undid, as though you hadn't undone it. Hold the "
                + "control key and press r. Redo only works straight after undoing: once you "
                + "make a new change, the undone ones are gone.\n"
                + "In the example `dw` deletes very, `u` undoes that, and `ctrl-r` deletes it "
                + "again."},
        {"visual",
            "max(|a + b)", "vt)d",
            "Starts visual mode, selecting one character at a time. The character under the "
                + "cursor is highlighted, and moving stretches the highlight from there to the "
                + "cursor. Both ends are included.\n"
                + "In the example `v` starts selecting at the a, `t)` stretches the selection "
                + "to just before the bracket, and `d` deletes a + b.",
            "keep\n|drop\ndrop too\nkeep", "Vjd",
            "Starts visual mode, selecting whole lines. The cursor's line is highlighted from "
                + "end to end, and moving up or down adds more lines. Press shift and v.\n"
                + "In the example `V` selects the line drop, `j` adds the line below, and `d` "
                + "deletes both.",
            "the |quick brown fox", "veed",
            "While selecting, every motion works as usual: the cursor moves, and the "
                + "selection stretches or shrinks to follow it. The end where you pressed `v` "
                + "stays put.\n"
                + "In the example `v` starts selecting at the q, each `e` stretches the "
                + "selection to the end of another word, and `d` deletes quick brown.",
            "start\n|old 1\nold 2\nend", "Vjcnew<esc>",
            "With text selected, `d` deletes it, `y` copies it and `c` deletes it and "
                + "switches to insert mode to type a replacement. The selection is the text to "
                + "act on, so there is no need for a motion after the operator. Afterwards, "
                + "visual mode ends.\n"
                + "In the example `V` and `j` select the two old lines, and `c` replaces them "
                + "with one line, new.",
            "the |quick brown fox", "vee<esc>",
            "Stops selecting and goes back to normal mode without changing anything. The "
                + "cursor stays where the selection ended.\n"
                + "In the example `vee` selects quick brown, then `esc` drops the selection, "
                + "leaving the text as it was."},
        {"search",
            "|" + GREEK, "/gam<enter>",
            "Press `/`, type some text, then press `enter`: the cursor jumps to the next place "
                + "after it where that text appears, on any line. What you type shows in the "
                + "bottom bar, and every match is highlighted. The search matches the exact "
                + "letters, so capitals count. If it reaches the end of the text it carries on "
                + "from the top. If the text appears nowhere, the cursor stays put. `esc` "
                + "abandons a search you are typing.\n"
                + "In the example `/gam` and `enter` jump to gamma at the start of line two. A "
                + "few letters are often enough.",
            "|" + GREEK, "/beta<enter>n",
            "Jumps to the next match of your last search, so you don't have to type it "
                + "again. Press it repeatedly to visit every match, going on from the top when "
                + "it reaches the end.\n"
                + "In the example `/beta` jumps to the beta on line one, and `n` goes on to the "
                + "beta on line three.",
            "|" + GREEK, "/beta<enter>nN",
            "Jumps to the previous match of your last search, the opposite of `n`. Press "
                + "shift and n.\n"
                + "In the example `/beta` and `n` reach the beta on line three, and `N` goes back "
                + "to the one on line one."},
        {"bigwords",
            "|let x = items.count + 1;", "WWW",
            "Jumps the cursor forward to the start of the next WORD. A WORD is everything "
                + "between two spaces, punctuation included, so `items.count` is one WORD where "
                + "`w` sees three words. In code full of dots and brackets, `W` gets about in far "
                + "fewer jumps.\n"
                + "In the example three `W`s jump to x, then =, then items.count.",
            "let x = items.count |+ 1;", "BB",
            "Jumps the cursor back to the start of a WORD, the backward version of `W`. Like "
                + "`b`, from the middle of a WORD it first goes to that WORD's start.\n"
                + "In the example the cursor starts on the +. Two `B`s jump back to the start of "
                + "items.count, then to the =.",
            "|items.count + max(a, b);", "E",
            "Jumps the cursor forward to the last character of a WORD, the WORD version of "
                + "`e`.\n"
                + "In the example `E` jumps from the i to the t at the end of items.count, "
                + "straight past the dot where `e` would have stopped.",
            "if |(total > max(a, b)) {", "%",
            "With the cursor on a bracket, `%` jumps to the bracket that pairs with it, "
                + "skipping any pairs in between. It works on `(` `)` `[` `]` `{` `}`. If the "
                + "cursor isn't on a bracket, it uses the next bracket to the right on the same "
                + "line.\n"
                + "In the example the cursor is on the first (. `%` jumps to the ) that closes "
                + "it, past the brackets of max(a, b)."},
        {"small",
            "the quu|ick fox", "X",
            "Deletes the character to the left of the cursor, like backspace in other "
                + "editors. The cursor's own character stays. Press shift and x. A count "
                + "deletes more: `3X` deletes three.\n"
                + "In the example the cursor is on the i of quuick, and `X` deletes the u "
                + "before it.",
            "the |kuick fox", "sq<esc>",
            "Deletes the character under the cursor and switches to insert mode, so you can "
                + "type any amount in its place. Press `esc` when you have finished. Use `r` "
                + "instead when one character replaces one character. It is the same as `cl`.\n"
                + "In the example `s` deletes the k of kuick, and q is typed in its place.",
            "one\n|tow\nthree", "Stwo<esc>",
            "Deletes all the text on the cursor's line and switches to insert mode to type "
                + "it again. Press shift and s, then `esc` when you have finished. It is the "
                + "same as `cc`.\n"
                + "In the example the middle line, tow, is rewritten as two.",
            "|the quick\nbrown fox", "J",
            "Moves the line below up onto the end of the cursor's line, with one space "
                + "between them. Press shift and j. Press it again to join the next line too.\n"
                + "In the example the two lines become one: the quick brown fox.",
            "|vim is fun", "~",
            "Turns the letter under the cursor from lowercase to uppercase or back, then "
                + "moves the cursor one character right. On anything that isn't a letter it only "
                + "moves right. A count switches several letters: `4~` does four.\n"
                + "In the example `~` turns the v of vim into a capital V."},
        {"brackets",
            "say \"hel|lo\" now", "da\"",
            "Like `i\"`, but takes the quote marks too, plus the space after the closing one. "
                + "Use it to remove a quoted string entirely. `a'` and `a`` do the same for "
                + "single quotes and backticks.\n"
                + "In the example `da\"` deletes \"hello\" and the space after it, leaving say "
                + "now.",
            "call(alpha, |beta) + 1", "da(",
            "Like `i(`, but takes the round brackets themselves too. `a)` and `ab` are the "
                + "same.\n"
                + "In the example `da(` deletes (alpha, beta), leaving call + 1.",
            "list[|index + 1]", "ci[0<esc>",
            "Like `i(`, but for square brackets [ ]: everything between them, without the "
                + "brackets. `a[` takes the brackets too, and `i]` and `a]` are the same.\n"
                + "In the example `ci[` deletes index + 1 and 0 is typed instead.",
            "if (ok) { |run(); }", "di{",
            "Like `i(`, but for curly braces { }: everything between them, without the "
                + "braces. `a{` takes the braces too, and `i}`, `a}`, `iB` and `aB` are the "
                + "same.\n"
                + "In the example `di{` deletes everything between the braces.",
            "copy src/ma|in.c now", "ciWlib.c<esc>",
            "Like `iw`, but a WORD: everything between two spaces, punctuation included. "
                + "`aW` takes the space after it too.\n"
                + "In the example the cursor is inside src/main.c. `ciW` deletes all of it, "
                + "where `ciw` would only have taken main, and lib.c is typed instead."},
    };

    /**
     * Keys the built-in Vim understands that no lesson introduces. Each row is the key, what it
     * does, an example as {start, keys}, the explanation, then any extra lines.
     */
    private static final String[][] MORE_VIM = {
        {"count", "a number before a command repeats it", "|xxxgood", "3x",
            "Type a number and then a command, and the command is done that many times. `3x` "
                + "deletes three characters and `5j` moves down five lines. With an operator and "
                + "a motion, two numbers multiply: `2d3w` deletes six words.\n"
                + "In the example `3x` deletes the three x characters in front of good.",
            "works with almost everything:", "`3x` `2dd` `4p` `3u` `5j` `2fx` `3rx`"},
        {"3gg", "go to line 3 - the same as 3G", "|" + FIVE, "3gg",
            "A number before `gg` goes to that line, just as it does before `G`.\n"
                + "In the example `3gg` moves the cursor from line one to line three."},
        {"2fx", "a count finds the second match", "|a-b-c-d", "2f-",
            "A number before `f`, `t`, `F` or `T` skips matches: `2f-` goes to the second dash "
                + "to the right, not the first.\n"
                + "In the example `2f-` jumps straight to the second dash.",
            "works with `f` `F` `t` `T` `;` `,`"},
        {"3rx", "replace the next three characters", "|aaa bbb", "3rx",
            "A number before `r` replaces that many characters, starting at the cursor, all "
                + "with the same character.\n"
                + "In the example `3rx` turns aaa into xxx."},
        {"y + motion", "yank wherever a motion goes", "|copy this, not that", "yt,$p",
            "`y` is an operator, like `d`: follow it with any motion or text object, and it "
                + "copies that text into the register instead of deleting it. The text stays "
                + "as it is.\n"
                + "In the example `yt,` copies copy this, up to the comma, `$` moves to the end "
                + "of the line, and `p` puts the copy there.",
            "any motion or text object works:", "`yw` `y$` `yiw` `yi(` `yy`"},
        {"3p", "put three copies", "|row\nend", "yy3p",
            "A number before `p` or `P` puts that many copies at once.\n"
                + "In the example `yy` copies the line row and `3p` puts three copies below it.",
            "`P` takes a count too"},
        {"i'  i`", "inside single quotes or backticks", "name = '|old';", "ci'new<esc>",
            "Like `i\"`, but between a pair of single quotes, or a pair of backticks, on the "
                + "cursor's line.\n"
                + "In the example `ci'` deletes old between the quotes and new is typed instead."},
        {"ib  iB", "ib is i( and iB is i{", "max(|a, b)", "dib",
            "Shorter names for two text objects: `ib` (b for brackets) is the same as `i(`, "
                + "and `iB` (big brackets) is the same as `i{`.\n"
                + "In the example `dib` deletes a, b from inside the brackets.",
            "`i)` and `i}` work too"},
        {"i<  a<", "inside, or around, angle brackets", "List<|String> names",
            "ci<Integer<esc>",
            "Like `i(` and `a(`, but for angle brackets < >.\n"
                + "In the example `ci<` deletes String and Integer is typed instead.",
            "`i>` and `a>` are the same"},
        {"o", "in visual mode: jump to the other end of the selection",
            "one |two three four", "veobd",
            "While selecting, `o` moves the cursor to the opposite end of the selection, so "
                + "you can stretch the selection from that side instead. The selected text "
                + "stays the same. In normal mode `o` does something else: it opens a new "
                + "line.\n"
                + "In the example `ve` selects two, `o` jumps back to its start, `b` stretches "
                + "the selection back over one, and `d` deletes one two.",
            },
        {"x  s", "in visual mode: x deletes like d, s changes like c",
            "the |very quick fox", "vex",
            "While selecting, `x` deletes the selection exactly as `d` does, and `s` replaces "
                + "it exactly as `c` does.\n"
                + "In the example `ve` selects very and `x` deletes it."},
        {"v  V", "in visual mode: switch kind, or press the same one to leave",
            "one\n|two\nthree", "vVd",
            "While selecting characters with `v`, pressing `V` switches to selecting whole "
                + "lines, and the other way round. Pressing the key you started with again "
                + "stops selecting, like `esc`.\n"
                + "In the example `v` starts selecting characters, `V` switches to the whole "
                + "line two, and `d` deletes it."},
        {"bksp", "in insert mode: delete the character before the cursor",
            "the quic| fox", "ax<bs>k<esc>",
            "While typing, backspace deletes the character just before the cursor, as in any "
                + "editor. At the start of a line it joins the line onto the one above.\n"
                + "In the example a wrong x is typed after quic, `bksp` removes it, and k is "
                + "typed instead."},
        {"enter", "in insert mode: start a new line", "first|second", "i<enter><esc>",
            "While typing, `enter` breaks the line at the cursor: whatever is after the "
                + "cursor moves down onto a new line. The new line starts with the same "
                + "indentation as the one it came from.\n"
                + "In the example `i` starts typing before second, and `enter` moves second "
                + "onto a line of its own."},
        {"esc", "cancel a half-typed command", "the |quick fox", "d2<esc>l",
            "In normal mode, `esc` throws away a command you have started but not finished, "
                + "such as a `d` waiting for its motion, or a count. Nothing happens to the "
                + "text.\n"
                + "In the example `d2` is typed and then cancelled with `esc`, so the next key, "
                + "`l`, only moves the cursor instead of deleting.",
            "also leaves insert mode, visual mode", "and an unfinished search"},
        {"/ bksp", "while typing a search: correct it", "|alpha beta\ngamma alpha",
            "/gx<bs>am<enter>",
            "While typing a search after `/`, backspace deletes the last character you "
                + "typed, so you can fix a mistake before pressing `enter`.\n"
                + "In the example gx is typed, `bksp` removes the x, and am is typed to search "
                + "for gam.",
            "`esc` abandons the search",
            "`bksp` on an empty search abandons it too"},
    };

    /**
     * Commands and keys of the app itself: group, key, what it does, the explanation, then any
     * extra lines.
     */
    private static final String[][] APP = {
        {"commands", ":", "open the command line",
            "Press `:` (shift and the semicolon key) to type a command for the app itself, "
                + "such as `:stats` or `:q`. What you type appears in the bottom bar after the "
                + "colon. Press `enter` to run it, or `esc` to cancel. If the app doesn't know "
                + "the command, the bottom bar says so.",
            "`bksp` on an empty line cancels too",
            "mid-lesson it works in normal and visual mode"},
        {"commands", ":docs", "open or close this manual",
            "Opens this manual over whatever screen you are on. Nothing underneath changes, "
                + "and a lesson in progress waits for you.",
            "`:doc` works too", "`q` `esc` or `:q` closes it; the app stays open"},
        {"commands", ":tour", "show the tour of the app again",
            "Shows the short tour the app gives the first time it opens. It points out each "
                + "part of the window in turn and ends by starting your current lesson.",
            "`enter` or a click moves on", "`esc` ends it early"},
        {"commands", ":lessons", "open the list of lessons",
            "Opens the list of every lesson, with the keys each one teaches and your best "
                + "result in it. Move with `j` and `k` and press `enter` to start one.",
            "`:ls` works too"},
        {"commands", ":stats", "open your statistics",
            "Opens your statistics: how many lessons you have finished, your best efficiency "
                + "and time in each, and a calendar of the days you practiced."},
        {"commands", ":settings", "theme, keyboard layout, erase progress",
            "Opens the settings: the color theme, the keyboard layout, and a button to erase "
                + "all your results.",
            "`:set` `:help` `:h` open it too"},
        {"commands", ":lesson  :ready", "back to the current lesson",
            "Returns from any screen to the lesson you were on. A lesson part way through "
                + "carries on where you left it.",
            "`:l` works too"},
        {"commands", ":next  :prev", "the lesson after or before",
            "Opens the next lesson, or the previous one, on its introduction card.",
            "`:n` for next", "`:p` `:previous` `:N` for previous"},
        {"commands", ":7", "lesson 7 - any number",
            "A colon and a number opens that lesson, on its introduction card. The numbers are "
                + "the ones in the lessons list.",
            "from `:1` to `:" + Lessons.ALL.size() + "`"},
        {"commands", ":guided", "the current lesson's guided version again",
            "The first time you take a lesson, its tasks come in teaching order and each says "
                + "which keys to use. After that the lesson gives random tasks without "
                + "instructions. `:guided` starts the guided version again, for when you want "
                + "a reminder.",
            "`:guide` works too"},
        {"commands", ":restart", "start the lesson again",
            "Starts the current lesson again from its introduction card, with fresh tasks. "
                + "The unfinished attempt isn't recorded.",
            "`:e` and `:e!` work too", "`tab` does the same during a lesson"},
        {"commands", ":colo paper", "switch the color theme",
            "Changes the app's colors. There are four themes: ink, paper, moss and indigo. "
                + "Your choice is remembered.",
            "`:colorscheme` `:color` `:theme` work too",
            "with no name it moves to the next theme"},
        {"commands", ":dvorak  :qwerty", "switch keyboard layout",
            "For people who type Dvorak on a keyboard their computer treats as QWERTY. With "
                + "dvorak on, the text you type, and the characters after `f`, `t` and `r`, come "
                + "out as Dvorak, while commands stay on their usual keys, just as Vim's own "
                + "keymap option does. Leave it on qwerty if your computer is already set to "
                + "Dvorak.",
            "`:set keymap=dvorak` turns it on", "`:set keymap=` turns it off"},
        {"commands", ":q", "quit the app",
            "Closes vimdojo. Your results are already saved. With the docs or a replay open, "
                + "it only closes those.",
            "`:quit` `:qa` `:q!` `:wq` `:x` work too"},

        {"moving around the app", "gt  gT", "next or previous screen",
            "Steps through the screens in order: the lesson, lessons, stats, settings, and "
                + "round again. `gT`, with shift, goes the other way.",
            "in order: lesson, lessons, stats, settings"},
        {"moving around the app", "j  k", "move down or up in a list",
            "On the lessons and settings screens, `j` moves the highlight down a row and `k` "
                + "moves it up.",
            "the arrow keys work too", "on the stats screen they scroll"},
        {"moving around the app", "gg  G", "top or bottom of a list",
            "Jumps the highlight to the first row of a list, or with `G` to the last."},
        {"moving around the app", "h  l", "other lessons, from the ready screen",
            "On a lesson's introduction card, `h` goes to the lesson before and `l` to the "
                + "lesson after.",
            "in settings they change the selected option",
            "the left and right arrows work there too"},
        {"moving around the app", "enter", "begin, open or confirm",
            "What `enter` does depends on the screen.",
            "ready screen: begin the lesson", "results: on to the next lesson",
            "lessons: start the selected one", "stats: back to the lesson",
            "settings: change the option; erasing takes two"},
        {"moving around the app", "tab", "restart the lesson",
            "During a lesson, starts it again with fresh tasks.",
            "on the results screen: try again"},
        {"moving around the app", "esc", "close or go back",
            "Outside a lesson's text, `esc` closes whatever is open or goes back a step.",
            "docs: close them", "stats: back to the lesson", "settings: cancel an erase"},
        {"moving around the app", "q", "close the docs",
            "Closes this manual and returns to the screen underneath.",
            "`esc` and `:q` close them too"},
        {"moving around the app", "r", "replay a task, on the results screen",
            "After a lesson, the results screen lists every task. `r` plays the chosen task "
                + "again in a small window, with par's keys and the keys you pressed, so you can "
                + "see where a shorter way was.",
            "`j` `k` pick the task first; `space` works too", "clicking a task does the same",
            "inside it, `j` `k` step through the tasks", "`q` `esc` or `:q` closes it"},
        {"moving around the app", "ctrl-r", "redo, inside a lesson",
            "Brings back a change you undid with `u`. Hold the control key and press r."},

        {"reading the docs", "/text", "search the docs, then enter",
            "Press `/`, type a word, then `enter` to jump to the first entry that mentions it. "
                + "It looks in key names, descriptions, explanations, notes and section names. "
                + "Capitals don't matter here, and matches light up while you type.",
            "`esc` gives up; `/` then `enter` repeats the last"},
        {"reading the docs", "n  N", "next or previous match",
            "After a search, `n` jumps to the next entry that matches and `N` to the previous "
                + "one. Both carry on round from the other end.",
            "both wrap around the ends"},
        {"reading the docs", "j  k", "next or previous entry",
            "Moves down or up the list on the left. The right side shows the entry you are "
                + "on.",
            "the arrow keys work too"},
        {"reading the docs", "d  u", "scroll the text on the right",
            "When an entry is too long for the window, `d` scrolls its text half a window "
                + "down and `u` back up. The mouse wheel over the right side works too.\n"
                + "A fading line at the bottom shows when there is more to read."},
        {"reading the docs", "space  b", "a page down or up",
            "Moves a whole window's worth of entries down the list, or up with `b`.",
            "`f` pages down as well"},
        {"reading the docs", "gg  G", "first or last entry",
            "Jumps to the first entry in the docs, or with `G` to the last."},
        {"reading the docs", "q", "leave the docs",
            "Closes this manual and returns to the screen underneath.",
            "`esc` `:q` `:docs` and the docs link do too"},

        {"mouse", "click", "anything that looks like a link or a row",
            "Everything can be done from the keyboard, but the mouse works too.",
            "the logo restarts the current lesson",
            "bottom bar: docs, lessons, stats, settings",
            "lessons: click one to start it", "settings: click a choice; erasing takes two",
            "docs: click a key to see it"},
        {"mouse", "scroll", "the wheel scrolls anything longer than the window",
            "Turn the mouse wheel, or swipe on a trackpad, over anything too long for the "
                + "window to see the rest.",
            "lessons, stats, settings, docs", "and the ready screen in a small window"},
    };

    static final List<Entry> ALL = build();

    private Docs() {
    }

    private static String at(String text, int index) {
        return text.substring(0, index) + "|" + text.substring(index);
    }

    private static List<Entry> build() {
        List<Entry> entries = new ArrayList<>();
        for (String[] row : WORDS) {
            entries.add(new Entry(TERMS, row[0], row[1], null, null, row[2], List.of()));
        }
        for (Lesson lesson : Lessons.LESSONS) {
            Object[] row = java.util.Arrays.stream(EXAMPLES).filter(e -> e[0].equals(lesson.id()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("no examples for "
                            + lesson.id()));
            if (row.length != 1 + lesson.keys().size() * 3) {
                throw new IllegalStateException("examples for " + row[0] + " don't match its keys");
            }
            for (int i = 0; i < lesson.keys().size(); i++) {
                Lesson.Key key = lesson.keys().get(i);
                entries.add(new Entry(lesson.title(), key.key(), key.does(),
                        (String) row[1 + i * 3], (String) row[2 + i * 3], (String) row[3 + i * 3],
                        List.of()));
            }
        }
        for (String[] row : MORE_VIM) {
            entries.add(new Entry("more vim keys", row[0], row[1], row[2], row[3], row[4],
                    List.of(row).subList(5, row.length)));
        }
        for (String[] row : APP) {
            entries.add(new Entry(row[0], row[1], row[2], null, null, row[3],
                    List.of(row).subList(4, row.length)));
        }
        return List.copyOf(entries);
    }
}
