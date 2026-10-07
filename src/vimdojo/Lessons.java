package vimdojo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * The curriculum. Movement lessons generate fresh targets every run and work out par by searching
 * for the shortest key sequence. Editing lessons are built from templates filled with random
 * words, each with a known solution that is checked in the built-in Vim before the task is used.
 */
final class Lessons {
    private static final int DRILL_TASKS = 8;
    private static final int REVIEW_TASKS = 10;

    private static final String FOX = """
            the quick brown fox jumps over
            the lazy dog while seven wizards
            quietly judge five boxing matches
            and pack my box with a dozen jugs
            of bright violet liquid for luck""";

    private static final String TOOLS = """
            vim rewards patience: every key
            is a small tool, and tools combine.
            learn one motion at a time, then
            pair it with an operator and you
            can reshape text without a mouse.""";

    private static final String RENDER = """
            function render(items, limit) {
              let total = 0;
              for (const item of items) {
                total += item.price * item.count;
              }
              return format(total, limit);
            }""";

    private static final String PIPELINE = """
            alpha = load("alpha.txt")
            bravo = load("bravo.txt")
            charlie = merge(alpha, bravo)
            delta = filter(charlie, is_valid)
            echo = sort(delta, by_name)
            foxtrot = group(echo, 4)
            golf = render(foxtrot)
            hotel = write("out.txt", golf)
            india = notify(hotel, "done")
            juliet = close(india)""";

    private static final List<String> BASIC = List.of("h", "j", "k", "l");
    private static final List<String> WORD = plus(BASIC, "w", "b", "e");
    private static final List<String> LINE = plus(WORD, "0", "^", "$");


    // ---- words the editing tasks are filled with ----

    private static final String[] ADJ = {"quick", "lazy", "brown", "small", "quiet", "bright",
        "green", "clever", "sleepy", "brave", "fuzzy", "shiny"};
    private static final String[] NOUN = {"fox", "dog", "cat", "owl", "hen", "crow", "wolf",
        "frog", "goat", "mole", "hare", "duck"};
    private static final String[] VERB = {"jumps", "walks", "runs", "hops", "leaps", "steps",
        "climbs", "slides"};
    private static final String[] PREP = {"over", "under", "past", "near", "around", "behind"};
    private static final String[] NAME = {"total", "count", "price", "limit", "index", "value",
        "width", "score", "speed", "delay"};
    private static final String[] FUNC = {"load", "save", "send", "draw", "sort", "scan", "find",
        "copy"};
    private static final String[] EXTRA = {"very", "really", "quite", "rather", "truly"};
    private static final String[] JUNK = {"TODO remove me", "delete this line", "print(debug)",
        "old leftover code"};
    private static final String[] ITEM = {"milk", "eggs", "bread", "rice", "salt", "tea", "jam",
        "oats", "plums", "honey"};
    private static final String[][] SEQUENCE = {
        {"one", "two", "three", "four"}, {"north", "east", "south", "west"},
        {"spring", "summer", "fall", "winter"}, {"alpha", "beta", "gamma", "delta"},
        {"first", "second", "third", "fourth"}, {"red", "orange", "yellow", "green"}};

    private static String pick(Random r, String... options) {
        return options[r.nextInt(options.length)];
    }

    /** Distinct picks from one pool, in random order. */
    private static String[] several(Random r, String[] pool, int n) {
        List<String> all = new ArrayList<>(List.of(pool));
        Collections.shuffle(all, r);
        return all.subList(0, n).toArray(String[]::new);
    }

    /** A lower-case letter that appears nowhere in the text, so f can find it unambiguously. */
    private static char absentLetter(Random r, String text) {
        char c;
        do {
            c = (char) ('a' + r.nextInt(26));
        } while (text.indexOf(c) >= 0);
        return c;
    }

    /** Marks where the cursor starts, in the notation {@link Task#edit} reads. */
    private static String at(String text, int index) {
        return text.substring(0, index) + "|" + text.substring(index);
    }

    private static String without(String word, int index) {
        return word.substring(0, index) + word.substring(index + 1);
    }

    private static String with(String word, int index, char c) {
        return word.substring(0, index) + c + word.substring(index + 1);
    }

    private static String assignment(Random r, String name) {
        return name + " = " + (1 + r.nextInt(9));
    }

    // ---- the editing lessons, one generator per task ----

    private static final List<Function<Random, Task>> CHARS = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String rest = " " + pick(r, NOUN) + " " + pick(r, VERB);
            String typo = word.substring(0, i + 1) + word.substring(i);
            return Task.edit("delete the extra `" + word.charAt(i) + "` with `x`",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            String start = with(goal, 4 + i, absentLetter(r, goal));
            return Task.edit("replace the wrong letter: `r` then the right one",
                at(start, 4 + i), goal, "r" + word.charAt(i));
        },
        r -> {
            String word = pick(r, PREP);
            int i = r.nextInt(word.length());
            String verb = pick(r, VERB);
            String rest = " the " + pick(r, ADJ) + " " + pick(r, NOUN);
            String typo = word.substring(0, i + 1) + word.substring(i);
            return Task.edit("find the doubled letter and delete one",
                "|" + verb + " " + typo + rest, verb + " " + word + rest,
                "f" + word.charAt(i) + "x");
        },
        r -> {
            String word = pick(r, "box", "bag", "cup", "jar", "mug", "pot");
            String goal = pick(r, "fill", "pack", "stock") + " my " + word + " with "
                    + pick(r, ITEM);
            char wrong = absentLetter(r, goal);
            String typo = with(word, 1, wrong);
            return Task.edit("fix the word `" + typo + "`",
                "|" + goal.replace(" " + word + " ", " " + typo + " "), goal,
                "f" + wrong + "r" + word.charAt(1));
        },
        r -> {
            int n = 2 + r.nextInt(4);
            String noise = pick(r, "#", "*", "~", "%").repeat(n);
            String noun = pick(r, NOUN);
            return Task.edit("a count repeats `x`: remove all " + n + " with `" + n + "x`",
                "feed |" + noise + "the " + noun, "feed the " + noun, n + "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            char wrong = absentLetter(r, goal);
            String start = with(goal, 4 + i, wrong) + goal.charAt(goal.length() - 1);
            return Task.edit("two typos: fix both", "|" + start, goal,
                "f" + wrong + "r" + word.charAt(i) + "$x");
        });

    private static final List<Function<Random, Task>> INSERT = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("press `i`, type the missing `" + word.charAt(i) + "`, then `esc`",
                at("the " + without(word, i) + rest, 4 + i), "the " + word + rest,
                "i" + word.charAt(i) + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 1);
            String rest = " " + pick(r, NOUN);
            return Task.edit("`a` appends after the cursor: add the `" + word.charAt(i) + "`",
                at("the " + without(word, i) + rest, 4 + i - 1), "the " + word + rest,
                "a" + word.charAt(i) + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = pick(r, NOUN);
            return Task.edit("add the missing word `" + adj[0] + "`",
                "|the " + adj[1] + " " + noun, "the " + adj[0] + " " + adj[1] + " " + noun,
                "wi" + adj[0] + " <esc>");
        },
        r -> {
            String first = pick(r, "hello", "sorry", "thanks", "okay", "well", "listen");
            String second = pick(r, NOUN);
            return Task.edit("add a comma after `" + first + "`",
                "|" + first + " " + second, first + ", " + second, "ea,<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String op = pick(r, "*", "+", "-", "/");
            return Task.edit("put a `" + op + "` between " + n[1] + " and " + n[2],
                "|let " + n[0] + " = " + n[1] + " " + n[2] + ";",
                "let " + n[0] + " = " + n[1] + " " + op + " " + n[2] + ";",
                "4wi" + op + " <esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String op = pick(r, "+", "-", "*");
            int digit = 1 + r.nextInt(9);
            String goal = name + " " + op + "= " + digit + "0";
            return Task.edit("two insertions: make it `" + goal + "`",
                "|" + name + " = " + digit, goal, "wi" + op + "<esc>$a0<esc>");
        });

    private static final List<Function<Random, Task>> OPEN = List.of(
        r -> {
            String line = "return " + pick(r, NAME);
            return Task.edit("`A` appends at the end of the line: add the `;`",
                "|" + line, line + ";", "A;<esc>");
        },
        r -> {
            String word = pick(r, "let", "const", "var");
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("`I` inserts at the start: add `" + word + "`",
                at(line, line.indexOf('=')), word + " " + line, "I" + word + " <esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("`o` opens a line below: add `" + s[2] + "`",
                s[0] + "\n|" + s[1] + "\n" + s[3], String.join("\n", s), "o" + s[2] + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("`O` opens a line above: add `" + s[0] + "`",
                "|" + s[1] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "O" + s[0] + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add a line above and a line below",
                "|" + s[1], s[0] + "\n" + s[1] + "\n" + s[2],
                "O" + s[0] + "<esc>jo" + s[2] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String func = pick(r, FUNC);
            return Task.edit("add to both ends of the line",
                name + " |= " + func, "const " + name + " = " + func + "();",
                "Iconst <esc>A();<esc>");
        });

    private static final List<Function<Random, Task>> DELETE = List.of(
        r -> {
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("delete the extra word with `dw`",
                "the |" + pick(r, EXTRA) + " " + rest, "the " + rest, "dw");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            List<String> lines = new ArrayList<>(List.of(assignment(r, n[0]),
                    assignment(r, n[1])));
            String goal = String.join("\n", lines);
            lines.add(r.nextInt(3), "|" + pick(r, JUNK));
            return Task.edit("`dd` deletes the whole line", String.join("\n", lines), goal, "dd");
        },
        r -> {
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("`D` deletes from the cursor to the end of the line",
                line + "| // " + pick(r, "temporary", "fix later", "old value", "remove"), line,
                "D");
        },
        r -> {
            String call = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("`d` takes any motion: `d0` deletes back to the line start",
                pick(r, "debug", "todo", "note", "temp") + ": |" + call, call, "d0");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String keep = pick(r, FUNC) + "(" + n[0] + ", " + n[1];
            return Task.edit("`dt)` deletes up to the parenthesis",
                keep + "|, " + n[2] + ", " + n[3] + ")", keep + ")", "dt)");
        },
        r -> {
            String header = pick(r, NAME) + " report";
            StringBuilder start = new StringBuilder(header);
            for (int i = 0, n = 2 + r.nextInt(3); i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("junk ").append(i + 1);
            }
            return Task.edit("`dG` deletes from this line to the end of the file",
                start.toString(), header, "dG");
        });

    private static final List<Function<Random, Task>> CHANGE = List.of(
        r -> {
            String[] adj = several(r, ADJ, 3);
            String rest = " " + adj[2] + " " + pick(r, NOUN);
            return Task.edit("`cw` replaces a word: make it `" + adj[0] + "`",
                "the |" + adj[1] + rest, "the " + adj[0] + rest, "cw" + adj[0] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("`C` changes the rest of the line: return `" + n[2] + "`",
                "return |" + n[0] + " + " + n[1] + ";", "return " + n[2] + ";",
                "C" + n[2] + ";<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            String typo = "" + s[1].charAt(1) + s[1].charAt(0) + s[1].substring(2);
            return Task.edit("`cc` rewrites the whole line: fix `" + typo + "`",
                s[0] + "\n|" + typo + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2],
                "cc" + s[1] + "<esc>");
        },
        r -> {
            String[] c = several(r, new String[] {"red", "blue", "gold", "teal", "pink", "gray"},
                    2);
            String property = pick(r, "color", "border", "fill");
            return Task.edit("`ct;` changes up to the semicolon: make it `" + c[1] + "`",
                property + ": |" + c[0] + "; /* keep */", property + ": " + c[1] + "; /* keep */",
                "ct;" + c[1] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            int to = 100 + r.nextInt(900);
            return Task.edit("change the number to `" + to + "`",
                "|const " + name + " = " + (10 + r.nextInt(90)) + ";",
                "const " + name + " = " + to + ";", "$bcw" + to + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String[] prep = several(r, PREP, 2);
            String middle = " " + pick(r, NOUN) + " " + pick(r, VERB) + " ";
            return Task.edit("two words are wrong: change both",
                "|the " + adj[0] + " " + adj[1] + middle + prep[0],
                "the " + adj[0] + " " + adj[2] + middle + prep[1],
                "2wcw" + adj[2] + "<esc>$bcw" + prep[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> PUT = List.of(
        r -> {
            String line = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("duplicate the line: `yy` then `p`",
                "|" + line, line + "\n" + line, "yyp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move the line down: `dd` then `p`",
                "|" + s[1] + "\n" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move `" + s[0] + "` to the top: `P` puts above",
                s[1] + "\n|" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddkP");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length() - 1);
            String typo = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i + 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("swap two letters with `xp`",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "xp");
        },
        r -> {
            String word = pick(r, "very", "so", "too", "far");
            String rest = " " + pick(r, ADJ);
            return Task.edit("copy a word: `yw`, then put it",
                "|" + word + rest, word + " " + word + rest, "ywP");
        },
        r -> {
            String word = pick(r, ITEM);
            int copies = 2 + r.nextInt(2);
            return Task.edit("make " + (copies + 1) + " rows of `" + word + "`",
                "|" + word + "\nend", (word + "\n").repeat(copies + 1) + "end",
                copies == 2 ? "yypp" : "yy3p");
        });

    private static final List<Function<Random, Task>> COUNTS = List.of(
        r -> {
            int n = 2 + r.nextInt(2);
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("`d" + n + "w` deletes " + n + " words at once",
                "the |" + (pick(r, EXTRA) + " ").repeat(n) + rest, "the " + rest, "d" + n + "w");
        },
        r -> {
            int n = 2 + r.nextInt(3);
            StringBuilder start = new StringBuilder("keep");
            for (int i = 0; i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("drop ").append(i + 1);
            }
            return Task.edit("`" + n + "dd` deletes " + n + " lines",
                start + "\nkeep too", "keep\nkeep too", n + "dd");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String noun = " " + pick(r, NOUN);
            return Task.edit("`c2w` changes two words: make it `" + adj[2] + "`",
                "the |" + adj[0] + " " + adj[1] + noun, "the " + adj[2] + noun,
                "c2w" + adj[2] + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move two lines to the bottom: `2dd`, then put",
                "|" + s[2] + "\n" + s[3] + "\n" + s[0] + "\n" + s[1], String.join("\n", s),
                "2ddjp");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String two = assignment(r, n[0]) + "\n" + assignment(r, n[1]);
            return Task.edit("copy the first two lines to the end: `2yy`",
                "|" + two + "\n---", two + "\n---\n" + two, "2yyGp");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String verb = pick(r, VERB);
            return Task.edit("`d2b` deletes two words backward",
                "the " + adj[0] + " " + adj[1] + " " + pick(r, NOUN) + " |" + verb,
                "the " + adj[0] + " " + verb, "d2b");
        });

    private static final List<Function<Random, Task>> OBJECTS = List.of(
        r -> {
            String word = pick(r, ADJ);
            String before = "the " + pick(r, EXTRA) + " ";
            String noun = pick(r, NOUN);
            return Task.edit("`daw` deletes a word from anywhere inside it",
                at(before + word + " " + noun, before.length() + 1 + r.nextInt(word.length() - 1)),
                before + noun, "daw");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = " " + pick(r, NOUN);
            return Task.edit("`ciw` changes the word under the cursor: make it `" + adj[1] + "`",
                at("the " + adj[0] + noun, 4 + 1 + r.nextInt(adj[0].length() - 1)),
                "the " + adj[1] + noun, "ciw" + adj[1] + "<esc>");
        },
        r -> {
            String func = pick(r, "say", "print", "log", "show");
            String[] words = several(r, ITEM, 3);
            String old = words[0] + " " + words[1];
            return Task.edit("`ci\"` changes what is inside the quotes: make it `" + words[2] + "`",
                at(func + "(\"" + old + "\")", func.length() + 2 + 1 + r.nextInt(old.length() - 1)),
                func + "(\"" + words[2] + "\")", "ci\"" + words[2] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String func = pick(r, FUNC);
            return Task.edit("`di(` empties the parentheses",
                func + "(" + n[0] + ", |" + n[1] + ", " + n[2] + ")", func + "()", "di(");
        },
        r -> {
            String flag = pick(r, "ready", "done", "valid", "empty");
            return Task.edit("`ci(` replaces the condition with `" + flag + "`",
                "if (" + pick(r, NAME) + " |" + pick(r, ">", "<", "==") + " "
                        + (2 + r.nextInt(98)) + ") {",
                "if (" + flag + ") {", "ci(" + flag + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String[] words = several(r, ITEM, 2);
            return Task.edit("`ci\"` even reaches the next quotes on the line: make it `"
                    + words[1] + "`",
                "|" + name + " = \"old " + words[0] + "\";", name + " = \"" + words[1] + "\";",
                "ci\"" + words[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> REPEAT = List.of(
        r -> {
            int n = 3 + r.nextInt(2);
            String last = pick(r, "yes", "go", "done", "stop");
            return Task.edit("delete a word, then repeat with `.`",
                "|" + (pick(r, "no", "um", "so", "ha") + " ").repeat(n) + last, last,
                "dw" + ".".repeat(n - 1));
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String[] lines = {assignment(r, n[0]), assignment(r, n[1]), assignment(r, n[2])};
            return Task.edit("append `;` to one line, then `j.` for the rest",
                "|" + String.join("\n", lines), String.join(";\n", lines) + ";", "A;<esc>j.j.");
        },
        r -> {
            String[] keep = several(r, ITEM, 3);
            String drop = pick(r, "drop", "junk", "skip");
            return Task.edit("delete every `" + drop + "` line: `dd`, move, `.`",
                keep[0] + "\n|" + drop + "\n" + keep[1] + "\n" + drop + "\n" + keep[2],
                String.join("\n", keep), "ddj.");
        },
        r -> {
            String[] items = several(r, ITEM, 3);
            String bullet = pick(r, "-", "*", ">");
            return Task.edit("make it a list: `I" + bullet + " `, then repeat on each line",
                "|" + String.join("\n", items),
                bullet + " " + String.join("\n" + bullet + " ", items),
                "I" + bullet + " <esc>j.j.");
        },
        r -> {
            String[] letters = several(r, "a b c d e g h k m n p s".split(" "), 4);
            String gap = pick(r, "-", "_", "+");
            return Task.edit("turn each `" + gap + "` into a space: `;` repeats the find, `.`"
                    + " the change",
                "|" + String.join(gap, letters), String.join(" ", letters),
                "f" + gap + "r ;.;.");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("rename every `" + n[0] + "` to `" + n[1] + "`",
                "|" + n[0] + "(" + n[0] + ", " + n[2] + ", " + n[0] + ")",
                n[1] + "(" + n[1] + ", " + n[2] + ", " + n[1] + ")",
                "cw" + n[1] + "<esc>ww.$b.");
        });

    private static final List<Function<Random, Task>> VISUAL = List.of(
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("select both lines with `V` and `j`, then `d`",
                keep[0] + "\n|drop\ndrop too\n" + keep[1], keep[0] + "\n" + keep[1], "Vjd");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String func = pick(r, "max", "min", "abs", "sum");
            return Task.edit("select up to the parenthesis with `vt)`, then `c`: make it `" + n[3]
                    + "`",
                func + "(|" + n[0] + " + " + n[1] + " * " + n[2] + ")", func + "(" + n[3] + ")",
                "vt)c" + n[3] + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            String noun = pick(r, NOUN);
            return Task.edit("`vaw` selects a word; delete it",
                at("the " + word + " " + noun, 4 + 1 + r.nextInt(word.length() - 1)),
                "the " + noun, "vawd");
        },
        r -> {
            String[] items = several(r, ITEM, 2);
            String two = items[0] + "\n" + items[1];
            return Task.edit("copy two lines to the end: `Vjy`, then put",
                "|" + two + "\n---", two + "\n---\n" + two, "VjyGp");
        },
        r -> {
            String fresh = pick(r, ITEM);
            return Task.edit("replace both lines with `" + fresh + "`: select them, then `c`",
                "start\n|old line 1\nold line 2\nend", "start\n" + fresh + "\nend",
                "Vjc" + fresh + "<esc>");
        },
        r -> {
            String title = pick(r, NAME) + " list";
            String[] items = several(r, ITEM, 2 + r.nextInt(3));
            return Task.edit("`VG` selects to the end of the file",
                title + "\n|" + String.join("\n", items), title, "VGd");
        });

    /** Every editing task there is, for the review to draw on. */
    private static final List<Function<Random, Task>> EVERYTHING = joined(List.of(CHARS, INSERT,
            OPEN, DELETE, CHANGE, PUT, COUNTS, OBJECTS, REPEAT, VISUAL));

    static final List<Lesson> ALL = List.of(
        new Lesson("hjkl", "moving around", List.of(
                key("h", "move the cursor one character left"),
                key("j", "move the cursor down one line"),
                key("k", "move the cursor up one line"),
                key("l", "move the cursor one character right")),
            drill("move to the highlighted character", List.of(FOX, TOOLS, RENDER),
                text -> BASIC, null, 2, 7)),

        new Lesson("words", "word by word", List.of(
                key("w", "jump to the start of the next word"),
                key("b", "jump back to the start of a word"),
                key("e", "jump to the end of a word")),
            drill("jump there by words", List.of(FOX, TOOLS, RENDER),
                text -> WORD, text -> BASIC, 1, 4)),

        new Lesson("line", "ends of the line", List.of(
                key("0", "jump to the very start of the line"),
                key("^", "jump to the first non-space character"),
                key("$", "jump to the end of the line")),
            drill("get there using the ends of the line", List.of(FOX, TOOLS, RENDER),
                text -> LINE, text -> WORD, 1, 3)),

        new Lesson("jumps", "counts and big jumps", List.of(
                key("gg", "jump to the first line"), key("G", "jump to the last line"),
                key("7G", "jump to line 7 - any number works"),
                key("3j", "a number first repeats a move: 3j, 4w")),
            drill("get there in as few keys as you can", List.of(PIPELINE),
                Lessons::jumpMoves, text -> LINE, 1, 4)),

        new Lesson("find", "find a character", List.of(
                key("fx", "jump onto the next x in this line"),
                key("tx", "jump to just before the next x"),
                key("F T", "like f and t, but to the left"),
                key(";", "repeat the last f, t, F or T"),
                key(",", "repeat it in the opposite direction")),
            drill("find your way to the highlight", List.of(FOX, TOOLS, RENDER),
                Lessons::findMoves, text -> LINE, 2, 3)),

        new Lesson("chars", "fixing characters", List.of(
                key("x", "delete the character under the cursor"),
                key("rx", "replace the cursor's character with x"),
                key("u", "undo the last change")),
            each(CHARS)),

        new Lesson("insert", "inserting text", List.of(
                key("i", "start typing before the cursor"),
                key("a", "start typing after the cursor"),
                key("esc", "stop typing: back to normal mode")),
            each(INSERT)),

        new Lesson("open", "inserting at the edges", List.of(
                key("I", "start typing at the start of the line"),
                key("A", "start typing at the end of the line"),
                key("o", "add a line below and type on it"),
                key("O", "add a line above and type on it")),
            each(OPEN)),

        new Lesson("delete", "the delete operator", List.of(
                key("dw", "delete from the cursor to the next word"),
                key("dd", "delete the whole line"),
                key("D", "delete from the cursor to the line's end"),
                key("d + motion", "delete as far as a move goes: d0, dt)")),
            each(DELETE)),

        new Lesson("change", "the change operator", List.of(
                key("cw", "delete to the word's end, then type"),
                key("cc", "empty the line, then type it again"),
                key("C", "delete to the line's end, then type"),
                key("c + motion", "like d + motion, then start typing")),
            each(CHANGE)),

        new Lesson("put", "copy and paste", List.of(
                key("yy", "yank (copy) the whole line"),
                key("yw", "yank from the cursor to the next word"),
                key("p", "put (paste) after the cursor or below"),
                key("P", "put before the cursor or above"),
                key("dd p", "deleting copies too, so p puts it back")),
            each(PUT)),

        new Lesson("counts", "counts with operators", List.of(
                key("d2w", "delete two words"), key("3dd", "delete three lines"),
                key("c2w", "replace two words with what you type"),
                key("y3w", "operator, count and motion combine")),
            each(COUNTS)),

        new Lesson("objects", "text objects", List.of(
                key("iw", "the whole word the cursor is in"),
                key("aw", "the word and the space after it"),
                key("i\"", "the text inside the quotes"),
                key("i(", "the text inside the brackets"),
                key("d c y", "type one after an operator: ciw, di(")),
            each(OBJECTS)),

        new Lesson("repeat", "repeat and undo", List.of(
                key(".", "do the last change again"), key("u", "undo the last change"),
                key("ctrl-r", "redo what u undid")),
            each(REPEAT)),

        new Lesson("visual", "visual mode", List.of(
                key("v", "start selecting characters"),
                key("V", "start selecting whole lines"),
                key("motions", "move to grow or shrink the selection"),
                key("d y c", "delete, copy or change the selection"),
                key("esc", "stop selecting, change nothing")),
            each(VISUAL)),

        new Lesson("search", "searching", List.of(
                key("/text", "then enter: jump to the next match"),
                key("n", "jump to the next match"),
                key("N", "jump to the previous match")),
            fixed(
                Task.motion("search for `sort`: type `/sort` then enter",
                    PIPELINE, 0, 0, 4, 7, 6, "/sort<enter>"),
                Task.motion("search for `notify` - a few letters are enough",
                    PIPELINE, 4, 7, 8, 8, 4, "/no<enter>"),
                Task.motion("reach the second `load`: search, then `n`",
                    PIPELINE, 8, 8, 1, 8, 7, "/load<enter>n"),
                Task.motion("`N` goes back to the previous match",
                    PIPELINE, 1, 8, 0, 8, 1, "N"),
                Task.motion("jump to `golf` inside the parentheses on line 8",
                    PIPELINE, 0, 8, 7, 25, 7, "/golf<enter>n"),
                Task.motion("finish on `merge`",
                    PIPELINE, 7, 25, 2, 10, 4, "/me<enter>"))),

        new Lesson("review", "mixed review", List.of(), Lessons::review,
            "No hints here. Ten edits drawn from every lesson, in random order: make the text "
                + "match the goal with whichever keys you think are best."));

    private Lessons() {
    }

    private static List<Function<Random, Task>> joined(List<List<Function<Random, Task>>> lists) {
        List<Function<Random, Task>> all = new ArrayList<>();
        lists.forEach(all::addAll);
        return List.copyOf(all);
    }

    /** A lesson made of one task from each generator, in teaching order. */
    private static Function<Random, List<Task>> each(List<Function<Random, Task>> generators) {
        return random -> generators.stream().map(g -> checked(g, random)).toList();
    }

    /** Ten tasks from anywhere in the curriculum, with the telltale prompts removed. */
    private static List<Task> review(Random random) {
        List<Function<Random, Task>> pool = new ArrayList<>(EVERYTHING);
        Collections.shuffle(pool, random);
        List<Task> tasks = new ArrayList<>();
        for (Function<Random, Task> generator : pool.subList(0, REVIEW_TASKS)) {
            Task t = checked(generator, random);
            tasks.add(new Task("make the text match the goal", t.start(), t.row(), t.col(),
                    t.goal(), t.goalRow(), t.goalCol(), t.par(), t.solution()));
        }
        return tasks;
    }

    /**
     * Runs a generator until it produces a task whose solution really works: random words can
     * collide, for example when the letter to find also appears earlier in the line.
     */
    private static Task checked(Function<Random, Task> generator, Random random) {
        for (int attempt = 0; attempt < 200; attempt++) {
            Task task = generator.apply(random);
            Vim vim = new Vim(task.start(), task.row(), task.col());
            String keys = Keys.parse(task.solution());
            boolean early = task.reached(vim);
            for (int i = 0; i < keys.length() && !early; i++) {
                vim.key(keys.charAt(i));
                early = i < keys.length() - 1 && task.reached(vim);
            }
            if (!early && task.reached(vim)) {
                return task;
            }
        }
        throw new IllegalStateException("a task generator never produced a solvable task");
    }

    private static Lesson.Key key(String key, String does) {
        return new Lesson.Key(key, does);
    }

    private static List<String> plus(List<String> base, String... more) {
        List<String> all = new ArrayList<>(base);
        all.addAll(List.of(more));
        return List.copyOf(all);
    }

    private static Function<Random, List<Task>> fixed(Task... tasks) {
        return random -> List.of(tasks);
    }

    private static List<String> jumpMoves(String text) {
        List<String> moves = new ArrayList<>(LINE);
        moves.add("gg");
        moves.add("G");
        int rows = text.split("\n").length;
        for (int n = 2; n < rows; n++) {
            moves.add(n + "G");
        }
        for (int n = 2; n <= 9; n++) {
            moves.add(n + "j");
            moves.add(n + "k");
        }
        for (int n = 2; n <= 5; n++) {
            moves.add(n + "w");
            moves.add(n + "b");
            moves.add(n + "e");
        }
        return moves;
    }

    private static List<String> findMoves(String text) {
        List<String> moves = new ArrayList<>(LINE);
        text.chars().filter(c -> c > ' ').distinct().forEach(c -> {
            for (char kind : "fFtT".toCharArray()) {
                moves.add("" + kind + (char) c);
            }
        });
        return moves;
    }

    /**
     * A movement drill: a chain of targets, each one starting where the last ended. Targets are
     * chosen so the lesson's new keys are the shortest way there, quicker than what the
     * {@code known} keys from earlier lessons could manage.
     */
    private static Function<Random, List<Task>> drill(
            String prompt, List<String> texts, Function<String, List<String>> moves,
            Function<String, List<String>> known, int minPar, int maxPar) {
        return random -> {
            String text = texts.get(random.nextInt(texts.size()));
            String[] lines = text.split("\n");
            Vim vim = new Vim(text, 0, 0);
            List<Task> tasks = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            int row = 0;
            int col = 0;
            // The column j and k aim for carries over from one target to the next.
            int want = 0;
            for (int t = 0; t < DRILL_TASKS; t++) {
                Path[][] best = shortest(vim, row, col, want, moves.apply(text), maxPar);
                Path[][] old = known == null ? null
                        : shortest(vim, row, col, want, known.apply(text), maxPar);
                List<int[]> candidates = new ArrayList<>();
                // Relax the requirements step by step if nothing qualifies.
                for (int strictness = 2; strictness >= 0 && candidates.isEmpty(); strictness--) {
                    for (int r = 0; r < lines.length; r++) {
                        for (int c = 0; c < lines[r].length(); c++) {
                            Path path = best[r][c];
                            if (path == null || lines[r].charAt(c) == ' ' || (r == row && c == col)
                                    || used.contains(r * 1000 + c)) {
                                continue;
                            }
                            boolean newKeysHelp = old == null || old[r][c] == null
                                    || old[r][c].cost > path.cost;
                            if ((strictness < 2 || newKeysHelp)
                                    && (strictness < 1 || path.cost >= minPar)) {
                                candidates.add(new int[] {r, c});
                            }
                        }
                    }
                }
                int[] goal = candidates.get(random.nextInt(candidates.size()));
                Path path = best[goal[0]][goal[1]];
                tasks.add(Task.motion(prompt, text, row, col, goal[0], goal[1], path.cost,
                        path.keys));
                used.add(goal[0] * 1000 + goal[1]);
                row = goal[0];
                col = goal[1];
                want = path.want;
            }
            return tasks;
        };
    }

    private record Path(int cost, String keys, int row, int col, int want) {
    }

    /** Cheapest key sequence from the start to every position reachable within the limit. */
    private static Path[][] shortest(Vim vim, int row, int col, int want, List<String> moves,
                                     int limit) {
        List<String> lines = vim.lines();
        Path[][] best = new Path[lines.size()][];
        for (int r = 0; r < best.length; r++) {
            best[r] = new Path[Math.max(1, lines.get(r).length())];
        }
        // Where j and k land depends on the remembered column, so it is part of the state.
        Set<Long> seen = new HashSet<>();
        PriorityQueue<Path> queue = new PriorityQueue<>((a, b) -> Integer.compare(a.cost, b.cost));
        queue.add(new Path(0, "", row, col, want));
        while (!queue.isEmpty()) {
            Path at = queue.poll();
            long state = ((long) at.row << 44) | ((long) at.col << 24) | Math.min(at.want, 0xFFFFFF);
            if (!seen.add(state)) {
                continue;
            }
            if (best[at.row][at.col] == null) {
                best[at.row][at.col] = at;
            }
            for (String move : moves) {
                int cost = at.cost + move.length();
                if (cost > limit) {
                    continue;
                }
                vim.place(at.row, at.col, at.want);
                for (char k : move.toCharArray()) {
                    vim.key(k);
                }
                if (vim.row() != at.row || vim.col() != at.col || vim.want() != at.want) {
                    queue.add(new Path(cost, at.keys + move, vim.row(), vim.col(), vim.want()));
                }
            }
        }
        return best;
    }
}
