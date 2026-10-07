package vimdojo;

import java.util.ArrayList;
import java.util.List;

/**
 * The reference: every key and command the app responds to. Keys the lessons teach come first,
 * in lesson order, with names and descriptions taken from the lessons themselves so the two
 * never drift apart. After them come the rest of the built-in Vim's keys, then the app's own
 * commands, keys and mouse actions. Anything Vim does has a short example to play back.
 */
final class Docs {
    /**
     * One documented key. The example starts from {@code start}, where {@code |} marks the
     * cursor, and plays {@code keys}; entries about the app itself have no example. The extra
     * lines list other spellings and details, with key names in backticks.
     */
    record Entry(String group, String key, String does, String start, String keys,
                 List<String> more) {
        boolean hasDemo() {
            return start != null;
        }

        /** A fresh Vim at the start of the example. */
        Vim vim() {
            Task task = Task.edit("", start, "", "");
            return new Vim(task.start(), task.row(), task.col());
        }
    }

    private static final String FIVE = "one\ntwo\nthree\nfour\nfive";
    private static final String GREEK = "alpha beta\ngamma alpha\nbeta gamma";

    /** Lesson id, then one {start, keys} example per key, in the order the lesson lists them. */
    private static final Object[][] EXAMPLES = {
        {"hjkl", "the quick b|rown fox", "hhh",
            "the |quick brown\njumps over\nthe lazy dog", "jj",
            "the quick brown\njumps over\nthe |lazy dog", "kk",
            "the |quick brown fox", "lll"},
        {"words", "|the quick brown fox jumps", "www",
            "the quick brown fox |jumps", "bbb",
            "|the quick brown fox", "eee"},
        {"line", "  return total |+ count;", "0",
            "  return total |+ count;", "^",
            "  return |total + count;", "$"},
        {"jumps", at(FIVE, 14), "gg",
            at(FIVE, 4), "G",
            "|" + FIVE, "4G",
            "|" + FIVE, "3j"},
        {"find", "|pack my box with jugs", "fx",
            "|pack my box with jugs", "tx",
            "pack my box with |jugs", "FxTp",
            "|a-b-c-d-e", "f-;;",
            "|a-b-c-d-e", "f-;;,"},
        {"chars", "the q|uuick fox", "x",
            "the quick br|awn fox", "ro",
            "the |very quick fox", "dwu"},
        {"insert", "the quick br|wn fox", "io<esc>",
            "the qui|c brown fox", "ak<esc>",
            "hell|o world", "a,<esc>"},
        {"open", "total |= 0;", "Ilet <esc>",
            "return |total", "A;<esc>",
            "one\n|two\nfour", "othree<esc>",
            "|two\nthree", "Oone<esc>"},
        {"delete", "the |very quick fox", "dw",
            "keep\n|drop this\nkeep", "dd",
            "total = 0;| // old", "D",
            "sum(a, b|, c, d)", "dt)"},
        {"change", "the |slow fox", "cwquick<esc>",
            "one\n|tow\nthree", "cctwo<esc>",
            "return |a + b;", "Ctotal;<esc>",
            "color: |red; /* keep */", "ct;blue<esc>"},
        {"put", "|echo hello\ndone", "yyp",
            "|very good", "ywP",
            "|second\nfirst\nthird", "ddp",
            "two\n|one\nthree", "ddkP",
            "|b\na\nc", "ddp"},
        {"counts", "the |very very quick fox", "d2w",
            "keep\n|drop\ndrop\ndrop\nkeep", "3dd",
            "the |dark red fox", "c2wbrown<esc>",
            "|ho ho ho and a bottle", "y3wP"},
        {"objects", "the bro|ken fox", "ciwbrown<esc>",
            "the very qu|ick fox", "daw",
            "say(\"hel|lo there\")", "ci\"goodbye<esc>",
            "call(alpha, |beta)", "di(",
            "if (x |> 10) {", "ci(ready<esc>"},
        {"repeat", "|no no no yes", "dw..",
            "the |very quick fox", "dwu",
            "the |very quick fox", "dwu<c-r>"},
        {"visual", "max(|a + b)", "vt)d",
            "keep\n|drop\ndrop too\nkeep", "Vjd",
            "the |quick brown fox", "veed",
            "start\n|old 1\nold 2\nend", "Vjcnew<esc>",
            "the |quick brown fox", "vee<esc>"},
        {"search", "|" + GREEK, "/gam<enter>",
            "|" + GREEK, "/beta<enter>n",
            "|" + GREEK, "/beta<enter>nN"},
    };

    /**
     * Keys the built-in Vim understands that no lesson introduces. Each row is the key, what it
     * does, an example as {start, keys}, then any extra lines.
     */
    private static final String[][] MORE_VIM = {
        {"count", "a number before a key repeats it", "|xxxgood", "3x",
            "works with almost everything:", "`3x` `2dd` `4p` `3u` `5j` `2fx` `3rx`",
            "with an operator it multiplies: `2d3w`"},
        {"W  B  E", "like w b e, but only spaces end a word",
            "|let x = items.count + 1;", "WWWBB",
            "`w` stops at punctuation; `W` does not"},
        {"%", "jump to the matching bracket", "if |(total > max(a, b)) {", "%",
            "works on `(` `)` `[` `]` `{` `}`", "from elsewhere, uses the next bracket on the line"},
        {"3gg", "go to line 3 - the same as 3G", "|" + FIVE, "3gg"},
        {"2fx", "a count finds the second match", "|a-b-c-d", "2f-",
            "works with `f` `F` `t` `T` `;` `,`"},
        {"X", "delete the character before the cursor", "the quu|ick fox", "X"},
        {"3rx", "replace the next three characters", "|aaa bbb", "3rx"},
        {"s", "replace one character and keep typing", "the |kuick fox", "sq<esc>",
            "the same as `cl`"},
        {"S", "rewrite the whole line", "one\n|tow\nthree", "Stwo<esc>", "the same as `cc`"},
        {"J", "join the next line onto this one", "|the quick\nbrown fox", "J"},
        {"~", "switch a letter between uppercase and lowercase", "|vim is fun", "~"},
        {"y + motion", "yank wherever a motion goes", "|copy this, not that", "yt,$p",
            "any motion or text object works:", "`yw` `y$` `yiw` `yi(` `yy`"},
        {"3p", "put three copies", "|row\nend", "yy3p", "`P` takes a count too"},
        {"iW  aW", "a word in the W sense: up to the spaces", "copy src/ma|in.c now", "daW"},
        {"i'  i`", "inside single quotes or backticks", "name = '|old';", "ci'new<esc>"},
        {"a\"  a'  a`", "the quotes as well, and the space after",
            "say \"hel|lo\" now", "da\""},
        {"a(", "the parentheses and everything in them", "call(alpha, |beta) + 1", "da(",
            "`a)` and `ab` are the same"},
        {"ib  iB", "ib is i( and iB is i{", "max(|a, b)", "dib", "`i)` and `i}` work too"},
        {"i[  a[", "inside, or around, square brackets", "list[|index + 1]", "ci[0<esc>",
            "`i]` and `a]` are the same"},
        {"i{  a{", "inside, or around, curly braces", "if (ok) { |run(); }", "di{",
            "`i}` `a}` `aB` are the same"},
        {"i<  a<", "inside, or around, angle brackets", "List<|String> names",
            "ci<Integer<esc>", "`i>` and `a>` are the same"},
        {"o", "in visual mode: jump to the other end of the selection",
            "one |two three four", "veobd"},
        {"x  s", "in visual mode: x deletes like d, s changes like c",
            "the |very quick fox", "vex"},
        {"v  V", "in visual mode: switch kind, or press the same one to leave",
            "one\n|two\nthree", "vVd"},
        {"bksp", "in insert mode: delete the character before the cursor",
            "the quic| fox", "ax<bs>k<esc>", "at the start of a line it joins the line above"},
        {"enter", "in insert mode: start a new line", "first|second", "i<enter><esc>",
            "the new line keeps the indentation"},
        {"esc", "cancel a half-typed command", "the |quick fox", "d2<esc>l",
            "also leaves insert mode, visual mode", "and an unfinished search"},
        {"/ bksp", "while typing a search: correct it", "|alpha beta\ngamma alpha",
            "/gx<bs>am<enter>", "`esc` abandons the search",
            "`bksp` on an empty search abandons it too"},
    };

    /** Commands and keys of the app itself: group, key, what it does, then any extra lines. */
    private static final String[][] APP = {
        {"commands", ":", "open the command line", "`enter` runs what you typed",
            "`esc` cancels", "`bksp` on an empty line cancels too",
            "mid-lesson it works in normal and visual mode"},
        {"commands", ":docs", "this manual", "`:doc` works too",
            "`q` `esc` or `:q` closes it; the app stays open"},
        {"commands", ":tour", "the tour of the app",
            "shown by itself the first time the app opens", "`enter` or a click moves on",
            "`esc` ends it early"},
        {"commands", ":lessons", "the lesson list", "`:ls` works too"},
        {"commands", ":stats", "your statistics"},
        {"commands", ":settings", "theme, keyboard layout, erase progress",
            "`:set` `:help` `:h` open it too"},
        {"commands", ":lesson  :ready", "back to the current lesson", "`:l` works too",
            "the lesson picks up where you left off"},
        {"commands", ":next  :prev", "the lesson after or before", "`:n` for next",
            "`:p` `:previous` `:N` for previous"},
        {"commands", ":7", "lesson 7 - any number", "from `:1` to `:17`"},
        {"commands", ":restart", "start the lesson again", "`:e` and `:e!` work too",
            "`tab` does the same during a lesson"},
        {"commands", ":colo paper", "switch theme",
            "`:colorscheme` `:color` `:theme` work too", "themes: ink, paper, moss, indigo",
            "with no name it moves to the next theme"},
        {"commands", ":dvorak  :qwerty", "switch keyboard layout",
            "`:set keymap=dvorak` turns it on", "`:set keymap=` turns it off",
            "it converts typed text, not commands"},
        {"commands", ":q", "quit", "`:quit` `:qa` `:q!` `:wq` `:x` work too",
            "with the docs open it only closes them"},

        {"moving around the app", "gt  gT", "next or previous screen",
            "in order: lesson, lessons, stats, settings"},
        {"moving around the app", "j  k", "move down or up in a list",
            "the arrow keys work too", "on the stats screen they scroll"},
        {"moving around the app", "gg  G", "top or bottom of a list"},
        {"moving around the app", "h  l", "other lessons, from the ready screen",
            "in settings they change the selected option", "the left and right arrows work there too"},
        {"moving around the app", "enter", "begin, open or confirm",
            "ready screen: begin the lesson", "results: on to the next lesson",
            "lessons: start the selected one", "stats: back to the lesson",
            "settings: change the option; erasing takes two"},
        {"moving around the app", "tab", "restart the lesson", "on the results screen: try again"},
        {"moving around the app", "esc", "close or go back", "docs: close them",
            "stats: back to the lesson", "settings: cancel an erase"},
        {"moving around the app", "q", "close the docs", "`esc` and `:q` close them too"},

        {"reading the docs", "/text", "search the docs, then enter",
            "looks in keys, descriptions, notes and sections", "case doesn't matter",
            "matches light up as you type", "`esc` gives up; `/` then `enter` repeats the last"},
        {"reading the docs", "n  N", "next or previous match", "both wrap around the ends"},
        {"reading the docs", "j  k", "next or previous entry", "the arrow keys work too"},
        {"reading the docs", "space  b", "a page down or up", "`f` pages down as well"},
        {"reading the docs", "gg  G", "first or last entry"},
        {"reading the docs", "q", "leave the docs",
            "`esc` `:q` `:docs` and the docs link do too"},
        {"moving around the app", "r", "replay a task, on the results screen",
            "`j` `k` pick the task first; `space` works too", "clicking a task does the same",
            "it shows par's keys at work, and yours", "inside it, `j` `k` step through the tasks",
            "`q` `esc` or `:q` closes it"},
        {"moving around the app", "ctrl-r", "redo, inside a lesson",
            "hold control and press `r`"},

        {"mouse", "click", "anything that looks like a link or a row",
            "the logo restarts the current lesson", "bottom bar: docs, lessons, stats, settings",
            "lessons: click one to start it", "settings: click a choice; erasing takes two",
            "docs: click a key to see it"},
        {"mouse", "scroll", "the wheel scrolls anything longer than the window",
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
        for (Object[] row : EXAMPLES) {
            Lesson lesson = Lessons.ALL.stream().filter(l -> l.id().equals(row[0])).findFirst()
                    .orElseThrow();
            if (row.length != 1 + lesson.keys().size() * 2) {
                throw new IllegalStateException("examples for " + row[0] + " don't match its keys");
            }
            for (int i = 0; i < lesson.keys().size(); i++) {
                Lesson.Key key = lesson.keys().get(i);
                entries.add(new Entry(lesson.title(), key.key(), key.does(),
                        (String) row[1 + i * 2], (String) row[2 + i * 2], List.of()));
            }
        }
        for (String[] row : MORE_VIM) {
            entries.add(new Entry("more vim keys", row[0], row[1], row[2], row[3],
                    List.of(row).subList(4, row.length)));
        }
        for (String[] row : APP) {
            entries.add(new Entry(row[0], row[1], row[2], null, null,
                    List.of(row).subList(3, row.length)));
        }
        return List.copyOf(entries);
    }
}
