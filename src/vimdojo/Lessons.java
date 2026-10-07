package vimdojo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * The curriculum. Each lesson runs guided the first time, its tasks in teaching order with a
 * hint saying which keys to use, and as practice after that: tasks drawn at random from the same
 * pool, saying only what to do. Movement lessons generate fresh targets every run and work out
 * par by searching for the shortest key sequence. Editing lessons are built from templates filled
 * with random words, each with a known solution that is checked in the built-in Vim before the
 * task is used. Two mixes at the end draw on every lesson: at random, or weighted toward the
 * ones you find hardest.
 */
final class Lessons {
    /** Tasks in a lesson run: one per level when guided. */
    static final int LESSON_TASKS = 8;
    /** Tasks in a mixed run. */
    static final int MIX_TASKS = 10;

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

    /** Code dense with punctuation and brackets, where W, B, E and % earn their keep. */
    private static final String CALLS = """
            total = sum(items.map(x => x.price));
            if (user.isAdmin() && !locked) {
              save(path.join(root, "out.txt"));
            }
            return [first, second].concat(rest);
            log("done: " + count.toString());""";

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
    private static final List<String> BIG = plus(LINE, "W", "B", "E", "%");

    private static final String REACH = "move to the highlighted character";

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
    private static final String[] PEOPLE = {"alice", "bruno", "carla", "dmitri", "elena", "farid",
        "greta", "hiro"};
    private static final String[] PATHS = {"src/main.c", "lib/util.js", "docs/intro.md",
        "app/view.py", "test/run.sh"};
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

    // ---- the editing lessons, one generator per level, in teaching order ----

    private static final List<Function<Random, Task>> CHARS = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String rest = " " + pick(r, NOUN) + " " + pick(r, VERB);
            String typo = word.substring(0, i + 1) + word.substring(i);
            return Task.edit("delete the extra `" + word.charAt(i) + "`",
                "the cursor is on the extra `" + word.charAt(i) + "`: press `x` to delete it",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            String start = with(goal, 4 + i, absentLetter(r, goal));
            return Task.edit("fix the wrong letter",
                "the cursor is on the wrong letter: press `r`, then the right letter",
                at(start, 4 + i), goal, "r" + word.charAt(i));
        },
        r -> {
            String noun = pick(r, NOUN);
            String goal = pick(r, ADJ) + " " + noun + " " + pick(r, VERB);
            return Task.edit("remove the stray `!` at the end",
                "`$` jumps to the end of the line, then `x` deletes the `!`",
                "|" + goal + "!", goal, "$x");
        },
        r -> {
            String word = pick(r, PREP);
            int i = r.nextInt(word.length());
            String verb = pick(r, VERB);
            String rest = " the " + pick(r, ADJ) + " " + pick(r, NOUN);
            String typo = word.substring(0, i + 1) + word.substring(i);
            return Task.edit("find the doubled letter and delete one",
                "`f" + word.charAt(i) + "` jumps onto the doubled `" + word.charAt(i)
                    + "`, then `x` deletes it",
                "|" + verb + " " + typo + rest, verb + " " + word + rest,
                "f" + word.charAt(i) + "x");
        },
        r -> {
            String name = pick(r, NAME);
            int from = 1 + r.nextInt(8);
            int to = from + 1;
            return Task.edit("make the number `" + to + "`",
                "`$` reaches the number, then `r" + to + "` replaces it",
                "|" + name + " = " + from, name + " = " + to, "$r" + to);
        },
        r -> {
            String word = pick(r, "box", "bag", "cup", "jar", "mug", "pot");
            String goal = pick(r, "fill", "pack", "stock") + " my " + word + " with "
                    + pick(r, ITEM);
            char wrong = absentLetter(r, goal);
            String typo = with(word, 1, wrong);
            return Task.edit("fix the word `" + typo + "`",
                "`f" + wrong + "` jumps onto the wrong letter, then `r" + word.charAt(1)
                    + "` fixes it",
                "|" + goal.replace(" " + word + " ", " " + typo + " "), goal,
                "f" + wrong + "r" + word.charAt(1));
        },
        r -> {
            int n = 2 + r.nextInt(4);
            String noise = pick(r, "#", "*", "~", "%").repeat(n);
            String noun = pick(r, NOUN);
            return Task.edit("remove the `" + noise + "`",
                "a count repeats `x`: `" + n + "x` removes all " + n + " at once",
                "feed |" + noise + "the " + noun, "feed the " + noun, n + "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            char wrong = absentLetter(r, goal);
            String start = with(goal, 4 + i, wrong) + goal.charAt(goal.length() - 1);
            return Task.edit("two typos: fix both",
                "`f" + wrong + "` and `r" + word.charAt(i) + "` fix the wrong letter, then `$x`"
                    + " removes the doubled one at the end",
                "|" + start, goal, "f" + wrong + "r" + word.charAt(i) + "$x");
        });

    private static final List<Function<Random, Task>> INSERT = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("add the missing `" + word.charAt(i) + "`",
                "press `i`, type the missing `" + word.charAt(i) + "`, then `esc`",
                at("the " + without(word, i) + rest, 4 + i), "the " + word + rest,
                "i" + word.charAt(i) + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 1);
            String rest = " " + pick(r, NOUN);
            return Task.edit("add the missing `" + word.charAt(i) + "`",
                "`a` types after the cursor: `a`, then `" + word.charAt(i) + "`, then `esc`",
                at("the " + without(word, i) + rest, 4 + i - 1), "the " + word + rest,
                "a" + word.charAt(i) + "<esc>");
        },
        r -> {
            String goal = pick(r, "hello", "thanks", "see you", "good night") + " "
                    + pick(r, PEOPLE);
            return Task.edit("add a `!` at the end",
                "the cursor is on the last letter: `a`, then `!`, then `esc`",
                at(goal, goal.length() - 1), goal + "!", "a!<esc>");
        },
        r -> {
            String extra = pick(r, EXTRA);
            String adj = pick(r, ADJ);
            String noun = pick(r, NOUN);
            return Task.edit("add the word `" + extra + "`",
                "`i` types before the cursor: type `" + extra + "` and a space, then `esc`",
                "the |" + adj + " " + noun, "the " + extra + " " + adj + " " + noun,
                "i" + extra + " <esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = pick(r, NOUN);
            return Task.edit("add the missing word `" + adj[0] + "`",
                "`w` moves to the next word, then `i`, type `" + adj[0] + "` and a space, `esc`",
                "|the " + adj[1] + " " + noun, "the " + adj[0] + " " + adj[1] + " " + noun,
                "wi" + adj[0] + " <esc>");
        },
        r -> {
            String first = pick(r, "hello", "sorry", "thanks", "okay", "well", "listen");
            String second = pick(r, NOUN);
            return Task.edit("add a comma after `" + first + "`",
                "`e` reaches the end of the word, then `a,` and `esc`",
                "|" + first + " " + second, first + ", " + second, "ea,<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String op = pick(r, "*", "+", "-", "/");
            return Task.edit("put a `" + op + "` between " + n[1] + " and " + n[2],
                "`4w` jumps to " + n[2] + ", then `i`, type `" + op + "` and a space, `esc`",
                "|let " + n[0] + " = " + n[1] + " " + n[2] + ";",
                "let " + n[0] + " = " + n[1] + " " + op + " " + n[2] + ";",
                "4wi" + op + " <esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String op = pick(r, "+", "-", "*");
            int digit = 1 + r.nextInt(9);
            String goal = name + " " + op + "= " + digit + "0";
            return Task.edit("make it `" + goal + "`",
                "two insertions: `wi" + op + "` and `esc`, then `$a0` and `esc`",
                "|" + name + " = " + digit, goal, "wi" + op + "<esc>$a0<esc>");
        });

    private static final List<Function<Random, Task>> OPEN = List.of(
        r -> {
            String line = "return " + pick(r, NAME);
            return Task.edit("add `;` at the end of the line",
                "`A` types at the end of the line: `A`, then `;`, then `esc`",
                "|" + line, line + ";", "A;<esc>");
        },
        r -> {
            String word = pick(r, "let", "const", "var");
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("add `" + word + "` at the start of the line",
                "`I` types at the start of the line: `I`, then `" + word + "` and a space, `esc`",
                at(line, line.indexOf('=')), word + " " + line, "I" + word + " <esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add `" + s[2] + "` as a new line after `" + s[1] + "`",
                "`o` opens a line below: `o`, type `" + s[2] + "`, then `esc`",
                s[0] + "\n|" + s[1] + "\n" + s[3], String.join("\n", s), "o" + s[2] + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add `" + s[0] + "` as a new line at the top",
                "`O` opens a line above: `O`, type `" + s[0] + "`, then `esc`",
                "|" + s[1] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "O" + s[0] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String first = assignment(r, n[0]);
            String second = assignment(r, n[1]);
            return Task.edit("add `;` to the end of the second line",
                "`j` moves down, then `A;` and `esc`",
                "|" + first + "\n" + second, first + "\n" + second + ";", "jA;<esc>");
        },
        r -> {
            String comment = "// " + pick(r, "setup", "inputs", "defaults", "helpers");
            String line = assignment(r, pick(r, NAME));
            return Task.edit("add the comment `" + comment + "` above",
                "`O` opens a line above: type `" + comment + "`, then `esc`",
                "|" + line, comment + "\n" + line, "O" + comment + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add a line above and a line below",
                "`O" + s[0] + "` and `esc` adds the line above, `j` comes back down, then `o"
                    + s[2] + "` and `esc`",
                "|" + s[1], s[0] + "\n" + s[1] + "\n" + s[2],
                "O" + s[0] + "<esc>jo" + s[2] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String func = pick(r, FUNC);
            return Task.edit("make it `const " + name + " = " + func + "();`",
                "add to both ends: `Iconst ` and `esc`, then `A();` and `esc`",
                name + " |= " + func, "const " + name + " = " + func + "();",
                "Iconst <esc>A();<esc>");
        });

    /** Small edits that each have a key of their own. */
    private static final List<Function<Random, Task>> SMALL = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 1);
            String rest = " " + pick(r, NOUN);
            String typo = word.substring(0, i) + word.charAt(i - 1) + word.substring(i);
            return Task.edit("remove the doubled letter",
                "`X` deletes the letter before the cursor",
                at("the " + typo + rest, 4 + i + 1), "the " + word + rest, "X");
        },
        r -> {
            int n = 2 + r.nextInt(3);
            String name = pick(r, NAME);
            String mark = pick(r, "!", "?", "_");
            int value = 1 + r.nextInt(9);
            return Task.edit("remove the `" + mark.repeat(n) + "`",
                "`" + n + "X` deletes the " + n + " characters before the cursor",
                name + mark.repeat(n) + "| = " + value, name + " = " + value, n + "X");
        },
        r -> {
            String[] it = several(r, ITEM, 2);
            return Task.edit("replace the `&` with `and`",
                "`s` deletes the `&` and starts typing: type `and`, then `esc`",
                it[0] + " |& " + it[1], it[0] + " and " + it[1], "sand<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            String typo = "" + s[1].charAt(1) + s[1].charAt(0) + s[1].substring(2);
            return Task.edit("rewrite the middle line as `" + s[1] + "`",
                "`S` empties the line and starts typing: `" + s[1] + "`, then `esc`",
                s[0] + "\n|" + typo + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2],
                "S" + s[1] + "<esc>");
        },
        r -> {
            String first = "the " + pick(r, ADJ);
            String second = pick(r, NOUN) + " " + pick(r, VERB);
            return Task.edit("join the two lines into one",
                "`J` joins the next line onto this one, with a space between",
                "|" + first + "\n" + second, first + " " + second, "J");
        },
        r -> {
            String[] it = several(r, ITEM, 3);
            return Task.edit("make the three lines one",
                "`J` twice joins all three",
                "|" + String.join("\n", it), String.join(" ", it), "JJ");
        },
        r -> {
            String name = pick(r, PEOPLE);
            String rest = " " + pick(r, VERB) + " home";
            String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return Task.edit("capitalize `" + name + "`",
                "`~` switches the letter under the cursor between lower and upper case",
                "|" + name + rest, upper + rest, "~");
        },
        r -> {
            String word = pick(r, "todo", "note", "fixme", "bug");
            String rest = ": " + pick(r, FUNC) + " the " + pick(r, ITEM);
            return Task.edit("make `" + word + "` all capitals",
                "a count works with `~`: `" + word.length() + "~` switches " + word.length()
                    + " letters",
                "|" + word + rest, word.toUpperCase() + rest, word.length() + "~");
        });

    private static final List<Function<Random, Task>> DELETE = List.of(
        r -> {
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("delete the extra word",
                "`dw` deletes from the cursor to the next word",
                "the |" + pick(r, EXTRA) + " " + rest, "the " + rest, "dw");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            List<String> lines = new ArrayList<>(List.of(assignment(r, n[0]),
                    assignment(r, n[1])));
            String goal = String.join("\n", lines);
            lines.add(r.nextInt(3), "|" + pick(r, JUNK));
            return Task.edit("delete the junk line", "`dd` deletes the whole line",
                String.join("\n", lines), goal, "dd");
        },
        r -> {
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("delete the comment",
                "`D` deletes from the cursor to the end of the line",
                line + "| // " + pick(r, "temporary", "fix later", "old value", "remove"), line,
                "D");
        },
        r -> {
            String call = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("delete the label before the call",
                "`d` takes any motion: `d0` deletes back to the start of the line",
                pick(r, "debug", "todo", "note", "temp") + ": |" + call, call, "d0");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String keep = pick(r, FUNC) + "(" + n[0] + ", " + n[1];
            return Task.edit("keep only the first two arguments",
                "`dt)` deletes up to the closing bracket",
                keep + "|, " + n[2] + ", " + n[3] + ")", keep + ")", "dt)");
        },
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both junk lines",
                "`dj` deletes this line and the one below",
                keep[0] + "\n|junk\nmore junk\n" + keep[1], keep[0] + "\n" + keep[1], "dj");
        },
        r -> {
            String keep = pick(r, NAME) + " report";
            StringBuilder start = new StringBuilder();
            int n = 2 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                start.append(i == n - 1 ? "|" : "").append("junk ").append(i + 1).append("\n");
            }
            return Task.edit("delete every junk line",
                "`dgg` deletes from this line up to the first",
                start + keep, keep, "dgg");
        },
        r -> {
            String header = pick(r, NAME) + " report";
            StringBuilder start = new StringBuilder(header);
            for (int i = 0, n = 2 + r.nextInt(3); i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("junk ").append(i + 1);
            }
            return Task.edit("delete everything below the first line",
                "`dG` deletes from this line to the end of the text",
                start.toString(), header, "dG");
        });

    private static final List<Function<Random, Task>> CHANGE = List.of(
        r -> {
            String[] adj = several(r, ADJ, 3);
            String rest = " " + adj[2] + " " + pick(r, NOUN);
            return Task.edit("make `" + adj[1] + "` say `" + adj[0] + "`",
                "`cw` deletes the word and starts typing: `" + adj[0] + "`, then `esc`",
                "the |" + adj[1] + rest, "the " + adj[0] + rest, "cw" + adj[0] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("make it return `" + n[2] + "`",
                "`C` changes the rest of the line: type `" + n[2] + ";`, then `esc`",
                "return |" + n[0] + " + " + n[1] + ";", "return " + n[2] + ";",
                "C" + n[2] + ";<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            String typo = "" + s[1].charAt(1) + s[1].charAt(0) + s[1].substring(2);
            return Task.edit("fix `" + typo + "`",
                "`cc` rewrites the whole line: type `" + s[1] + "`, then `esc`",
                s[0] + "\n|" + typo + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2],
                "cc" + s[1] + "<esc>");
        },
        r -> {
            String[] c = several(r, new String[] {"red", "blue", "gold", "teal", "pink", "gray"},
                    2);
            String property = pick(r, "color", "border", "fill");
            return Task.edit("make the value `" + c[1] + "`",
                "`ct;` changes up to the semicolon: type `" + c[1] + "`, then `esc`",
                property + ": |" + c[0] + "; /* keep */", property + ": " + c[1] + "; /* keep */",
                "ct;" + c[1] + "<esc>");
        },
        r -> {
            String func = pick(r, FUNC);
            String[] n = several(r, NAME, 2);
            return Task.edit("make the argument `" + n[1] + "`",
                "`ct)` changes up to the bracket: type `" + n[1] + "`, then `esc`",
                func + "(|" + n[0] + ")", func + "(" + n[1] + ")", "ct)" + n[1] + "<esc>");
        },
        r -> {
            String[] labels = several(r, new String[] {"old", "draft", "temp", "todo", "new",
                "final"}, 2);
            String value = pick(r, ITEM);
            return Task.edit("change the label to `" + labels[1] + ":`",
                "`c0` changes everything before the cursor: type `" + labels[1]
                    + ": `, then `esc`",
                labels[0] + ": |" + value, labels[1] + ": " + value,
                "c0" + labels[1] + ": <esc>");
        },
        r -> {
            String name = pick(r, NAME);
            int to = 100 + r.nextInt(900);
            return Task.edit("change the number to `" + to + "`",
                "`$` and `b` reach the number, then `cw" + to + "` and `esc`",
                "|const " + name + " = " + (10 + r.nextInt(90)) + ";",
                "const " + name + " = " + to + ";", "$bcw" + to + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String[] prep = several(r, PREP, 2);
            String middle = " " + pick(r, NOUN) + " " + pick(r, VERB) + " ";
            return Task.edit("two words are wrong: change both",
                "`2w` and `cw" + adj[2] + "` fix the first; `$b` and `cw" + prep[1]
                    + "` the last",
                "|the " + adj[0] + " " + adj[1] + middle + prep[0],
                "the " + adj[0] + " " + adj[2] + middle + prep[1],
                "2wcw" + adj[2] + "<esc>$bcw" + prep[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> PUT = List.of(
        r -> {
            String line = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("duplicate the line",
                "`yy` copies the line, `p` puts the copy below",
                "|" + line, line + "\n" + line, "yyp");
        },
        r -> {
            String word = pick(r, "very", "so", "too", "far");
            String rest = " " + pick(r, ADJ);
            return Task.edit("say `" + word + "` twice",
                "`yw` copies the word and its space, `P` puts it before the cursor",
                "|" + word + rest, word + " " + word + rest, "ywP");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("put the lines in order",
                "`dd` cuts the line, `p` puts it back below the next one",
                "|" + s[1] + "\n" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move `" + s[0] + "` to the top",
                "`dd` cuts it, `k` moves up, `P` puts it above",
                s[1] + "\n|" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddkP");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length() - 1);
            String typo = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i + 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("swap the two letters",
                "`x` cuts the letter, `p` puts it back after the next one",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "xp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move `" + s[0] + "` to the top",
                "`dd` cuts it, `gg` goes to the first line, `P` puts it above",
                s[1] + "\n" + s[2] + "\n|" + s[0], s[0] + "\n" + s[1] + "\n" + s[2], "ddggP");
        },
        r -> {
            String head = pick(r, NAME) + " list";
            String[] it = several(r, ITEM, 2);
            return Task.edit("copy the first line to the end",
                "`yy` copies it, `G` goes to the last line, `p` puts it below",
                "|" + head + "\n" + it[0] + "\n" + it[1],
                head + "\n" + it[0] + "\n" + it[1] + "\n" + head, "yyGp");
        },
        r -> {
            String word = pick(r, ITEM);
            int copies = 2 + r.nextInt(2);
            return Task.edit("make " + (copies + 1) + " rows of `" + word + "`",
                copies == 2 ? "`yy` copies the line, then `p` twice" : "`yy`, then `3p` puts"
                    + " three copies",
                "|" + word + "\nend", (word + "\n").repeat(copies + 1) + "end",
                copies == 2 ? "yypp" : "yy3p");
        });

    private static final List<Function<Random, Task>> COUNTS = List.of(
        r -> {
            int n = 2 + r.nextInt(2);
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("delete the " + n + " extra words",
                "`d" + n + "w` deletes " + n + " words at once",
                "the |" + (pick(r, EXTRA) + " ").repeat(n) + rest, "the " + rest, "d" + n + "w");
        },
        r -> {
            int n = 2 + r.nextInt(3);
            StringBuilder start = new StringBuilder("keep");
            for (int i = 0; i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("drop ").append(i + 1);
            }
            return Task.edit("delete the " + n + " drop lines",
                "`" + n + "dd` deletes " + n + " lines",
                start + "\nkeep too", "keep\nkeep too", n + "dd");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String noun = " " + pick(r, NOUN);
            return Task.edit("replace both words with `" + adj[2] + "`",
                "`c2w` changes two words: type `" + adj[2] + "`, then `esc`",
                "the |" + adj[0] + " " + adj[1] + noun, "the " + adj[2] + noun,
                "c2w" + adj[2] + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 4);
            String noun = " " + pick(r, NOUN);
            return Task.edit("replace the three words with `" + adj[3] + "`",
                "`c3w` changes three words: type `" + adj[3] + "`, then `esc`",
                "the |" + adj[0] + " " + adj[1] + " " + adj[2] + noun, "the " + adj[3] + noun,
                "c3w" + adj[3] + "<esc>");
        },
        r -> {
            String word = pick(r, "ho", "la", "na", "ha");
            String end = pick(r, "end", "done", "stop");
            return Task.edit("double the `" + word + " " + word + "`",
                "`y2w` copies two words, `P` puts them before the cursor",
                "|" + word + " " + word + " " + end, (word + " ").repeat(4) + end, "y2wP");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move the first two lines to the bottom",
                "`2dd` cuts two lines, `j` moves down, `p` puts them below",
                "|" + s[2] + "\n" + s[3] + "\n" + s[0] + "\n" + s[1], String.join("\n", s),
                "2ddjp");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String two = assignment(r, n[0]) + "\n" + assignment(r, n[1]);
            return Task.edit("copy the first two lines to the end",
                "`2yy` copies two lines, `G` goes to the end, `p` puts them below",
                "|" + two + "\n---", two + "\n---\n" + two, "2yyGp");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String verb = pick(r, VERB);
            return Task.edit("delete the two words before `" + verb + "`",
                "`d2b` deletes two words backward",
                "the " + adj[0] + " " + adj[1] + " " + pick(r, NOUN) + " |" + verb,
                "the " + adj[0] + " " + verb, "d2b");
        });

    private static final List<Function<Random, Task>> OBJECTS = List.of(
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = " " + pick(r, NOUN);
            return Task.edit("make the word `" + adj[1] + "`",
                "`ciw` changes the whole word the cursor is in: type `" + adj[1] + "`, `esc`",
                at("the " + adj[0] + noun, 4 + 1 + r.nextInt(adj[0].length() - 1)),
                "the " + adj[1] + noun, "ciw" + adj[1] + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            String before = "the " + pick(r, EXTRA) + " ";
            String noun = pick(r, NOUN);
            return Task.edit("delete the word `" + word + "`",
                "`daw` deletes the word and a space, from anywhere inside it",
                at(before + word + " " + noun, before.length() + 1 + r.nextInt(word.length() - 1)),
                before + noun, "daw");
        },
        r -> {
            String func = pick(r, "say", "print", "log", "show");
            String[] words = several(r, ITEM, 3);
            String old = words[0] + " " + words[1];
            return Task.edit("make the quoted text `" + words[2] + "`",
                "`ci\"` changes what is inside the quotes: type `" + words[2] + "`, `esc`",
                at(func + "(\"" + old + "\")", func.length() + 2 + 1 + r.nextInt(old.length() - 1)),
                func + "(\"" + words[2] + "\")", "ci\"" + words[2] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String[] words = several(r, ITEM, 2);
            return Task.edit("empty the quotes",
                "`di\"` deletes everything inside the quotes",
                name + " = \"|" + words[0] + " " + words[1] + "\";", name + " = \"\";", "di\"");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String func = pick(r, FUNC);
            return Task.edit("empty the brackets",
                "`di(` deletes everything inside the brackets",
                func + "(" + n[0] + ", |" + n[1] + ", " + n[2] + ")", func + "()", "di(");
        },
        r -> {
            String flag = pick(r, "ready", "done", "valid", "empty");
            return Task.edit("make the condition `" + flag + "`",
                "`ci(` replaces what is inside the brackets: type `" + flag + "`, `esc`",
                "if (" + pick(r, NAME) + " |" + pick(r, ">", "<", "==") + " "
                        + (2 + r.nextInt(98)) + ") {",
                "if (" + flag + ") {", "ci(" + flag + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String[] f = several(r, FUNC, 2);
            String args = n[0] + ", " + n[1];
            return Task.edit("give `" + f[1] + "()` the same arguments",
                "`yi(` copies inside the brackets, `j` lands on the `)` below, `P` puts it before",
                f[0] + "(|" + args + ")\n" + f[1] + "()", f[0] + "(" + args + ")\n" + f[1] + "("
                    + args + ")", "yi(jP");
        },
        r -> {
            String name = pick(r, NAME);
            String[] words = several(r, ITEM, 2);
            return Task.edit("make the quoted text `" + words[1] + "`",
                "`ci\"` works from before the quotes too: type `" + words[1] + "`, `esc`",
                "|" + name + " = \"old " + words[0] + "\";", name + " = \"" + words[1] + "\";",
                "ci\"" + words[1] + "<esc>");
        });

    /** Text objects for the rest of the brackets and quotes, and for WORDs. */
    private static final List<Function<Random, Task>> BRACKETS = List.of(
        r -> {
            String verb = pick(r, "say", "shout", "write", "sing");
            String rest = pick(r, "now", "again", "twice", "softly");
            String item = pick(r, ITEM);
            return Task.edit("remove the quoted word",
                "`da\"` deletes the quotes, what is inside, and the space after",
                at(verb + " \"" + item + "\" " + rest, verb.length() + 2 + r.nextInt(item.length())),
                verb + " " + rest, "da\"");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String func = pick(r, FUNC);
            return Task.edit("delete the brackets and their contents",
                "`da(` takes the brackets as well as what is inside them",
                func + "(" + n[0] + ", |" + n[1] + ") + " + n[2], func + " + " + n[2], "da(");
        },
        r -> {
            String list = pick(r, "items", "rows", "cells", "keys");
            int to = r.nextInt(10);
            return Task.edit("make the index `" + to + "`",
                "`ci[` changes inside the square brackets: type `" + to + "`, `esc`",
                list + "[|" + pick(r, NAME) + " + 1]", list + "[" + to + "]",
                "ci[" + to + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String list = pick(r, "items", "rows", "cells", "keys");
            return Task.edit("remove the `[i]`",
                "`da[` deletes the square brackets and what is in them",
                name + " = " + list + "[|i];", name + " = " + list + ";", "da[");
        },
        r -> {
            String name = pick(r, NAME);
            String func = pick(r, FUNC);
            return Task.edit("empty the braces",
                "`di{` deletes everything inside the curly braces",
                "if (" + name + ") { |" + func + "(); }", "if (" + name + ") {}", "di{");
        },
        r -> {
            String[] f = several(r, FUNC, 2);
            return Task.edit("make the body `return 1;`",
                "`ci{` changes inside the braces: type ` return 1; `, then `esc`",
                f[0] + "() { |" + f[1] + "(); }", f[0] + "() { return 1; }",
                "ci{ return 1; <esc>");
        },
        r -> {
            String[] p = several(r, PATHS, 2);
            String verb = pick(r, "copy", "open", "edit", "move");
            String rest = pick(r, "now", "later", "first");
            int inside = 1 + r.nextInt(p[0].length() - 1);
            return Task.edit("replace `" + p[0] + "` with `" + p[1] + "`",
                "`ciW` changes the whole WORD, slashes and dots included",
                at(verb + " " + p[0] + " " + rest, verb.length() + 1 + inside),
                verb + " " + p[1] + " " + rest, "ciW" + p[1] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String[] it = several(r, ITEM, 2);
            return Task.edit("make the quoted text `" + it[1] + "`",
                "`ci'` changes inside single quotes: type `" + it[1] + "`, `esc`",
                name + " = '|" + it[0] + "';", name + " = '" + it[1] + "';",
                "ci'" + it[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> REPEAT = List.of(
        r -> {
            int n = 3 + r.nextInt(2);
            String last = pick(r, "yes", "go", "done", "stop");
            return Task.edit("delete every word but `" + last + "`",
                "`dw` deletes one, then `.` repeats it for each of the rest",
                "|" + (pick(r, "no", "um", "so", "ha") + " ").repeat(n) + last, last,
                "dw" + ".".repeat(n - 1));
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String[] lines = {assignment(r, n[0]), assignment(r, n[1]), assignment(r, n[2])};
            return Task.edit("end every line with `;`",
                "`A;` and `esc` on one line, then `j.` for each of the rest",
                "|" + String.join("\n", lines), String.join(";\n", lines) + ";", "A;<esc>j.j.");
        },
        r -> {
            String[] keep = several(r, ITEM, 3);
            String drop = pick(r, "drop", "junk", "skip");
            return Task.edit("delete every `" + drop + "` line",
                "`dd` deletes one, `j` moves to the next, `.` deletes it too",
                keep[0] + "\n|" + drop + "\n" + keep[1] + "\n" + drop + "\n" + keep[2],
                String.join("\n", keep), "ddj.");
        },
        r -> {
            String[] items = several(r, ITEM, 3);
            String bullet = pick(r, "-", "*", ">");
            return Task.edit("start every line with `" + bullet + " `",
                "`I" + bullet + " ` and `esc` on the first line, then `j.` for each of the rest",
                "|" + String.join("\n", items),
                bullet + " " + String.join("\n" + bullet + " ", items),
                "I" + bullet + " <esc>j.j.");
        },
        r -> {
            String[] words = several(r, new String[] {"wow", "nice", "great", "fine", "yes",
                "cool"}, 3);
            return Task.edit("remove both `!`",
                "`f!` and `x` remove the first, `;` finds the next, `.` deletes it",
                "|" + words[0] + "! " + words[1] + "! " + words[2],
                words[0] + " " + words[1] + " " + words[2], "f!x;.");
        },
        r -> {
            String[] letters = several(r, "a b c d e g h k m n p s".split(" "), 4);
            String gap = pick(r, "-", "_", "+");
            return Task.edit("turn each `" + gap + "` into a space",
                "`f" + gap + "` and `r ` fix one; `;` finds the next and `.` repeats the change",
                "|" + String.join(gap, letters), String.join(" ", letters),
                "f" + gap + "r ;.;.");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            return Task.edit("rename both `" + n[0] + "` to `" + n[1] + "`",
                "`cw" + n[1] + "` and `esc` on the first, then `j`, `b` and `.` on the second",
                "|" + n[0] + " = 1\n" + n[0] + " = 2", n[1] + " = 1\n" + n[1] + " = 2",
                "cw" + n[1] + "<esc>jb.");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("rename every `" + n[0] + "` to `" + n[1] + "`",
                "`cw" + n[1] + "` and `esc`, then `ww.` and `$b.` for the others",
                "|" + n[0] + "(" + n[0] + ", " + n[2] + ", " + n[0] + ")",
                n[1] + "(" + n[1] + ", " + n[2] + ", " + n[1] + ")",
                "cw" + n[1] + "<esc>ww.$b.");
        });

    private static final List<Function<Random, Task>> VISUAL = List.of(
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both drop lines",
                "`V` selects the line, `j` adds the next, `d` deletes both",
                keep[0] + "\n|drop\ndrop too\n" + keep[1], keep[0] + "\n" + keep[1], "Vjd");
        },
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both drop lines",
                "`V` selects the line, `k` adds the one above, `d` deletes both",
                keep[0] + "\ndrop\n|drop too\n" + keep[1], keep[0] + "\n" + keep[1], "Vkd");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = pick(r, NOUN);
            return Task.edit("change `" + adj[0] + "` to `" + adj[1] + "`",
                "`ve` selects to the end of the word, `c` replaces it: type `" + adj[1]
                    + "`, `esc`",
                "the |" + adj[0] + " " + noun, "the " + adj[1] + " " + noun,
                "vec" + adj[1] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String func = pick(r, "max", "min", "abs", "sum");
            return Task.edit("make the argument `" + n[3] + "`",
                "`vt)` selects up to the bracket, `c` replaces it: type `" + n[3] + "`, `esc`",
                func + "(|" + n[0] + " + " + n[1] + " * " + n[2] + ")", func + "(" + n[3] + ")",
                "vt)c" + n[3] + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            String noun = pick(r, NOUN);
            return Task.edit("delete the word `" + word + "`",
                "`vaw` selects the word and its space, `d` deletes them",
                at("the " + word + " " + noun, 4 + 1 + r.nextInt(word.length() - 1)),
                "the " + noun, "vawd");
        },
        r -> {
            String[] items = several(r, ITEM, 2);
            String two = items[0] + "\n" + items[1];
            return Task.edit("copy the first two lines to the end",
                "`Vj` selects them, `y` copies, `G` goes to the end, `p` puts them below",
                "|" + two + "\n---", two + "\n---\n" + two, "VjyGp");
        },
        r -> {
            String fresh = pick(r, ITEM);
            return Task.edit("replace both old lines with `" + fresh + "`",
                "`Vj` selects both lines, `c` replaces them: type `" + fresh + "`, `esc`",
                "start\n|old line 1\nold line 2\nend", "start\n" + fresh + "\nend",
                "Vjc" + fresh + "<esc>");
        },
        r -> {
            String title = pick(r, NAME) + " list";
            String[] items = several(r, ITEM, 2 + r.nextInt(3));
            return Task.edit("delete everything below the title",
                "`VG` selects to the end of the text, `d` deletes it",
                title + "\n|" + String.join("\n", items), title, "VGd");
        });

    // ---- searching ----

    /** The walk-through: fixed targets on one text, each search teaching something new. */
    private static final List<Task> SEARCH_GUIDED = List.of(
        Task.motion(REACH, "search for `sort`: type `/sort`, then `enter`",
            PIPELINE, 0, 0, 4, 7, 6, "/sort<enter>"),
        Task.motion(REACH, "a few letters are enough: `/no` and `enter` finds `notify`",
            PIPELINE, 4, 7, 8, 8, 4, "/no<enter>"),
        Task.motion(REACH, "the search goes past the end and on from the top: `/load`, `enter`,"
                + " then `n` for the next match",
            PIPELINE, 8, 8, 1, 8, 7, "/load<enter>n"),
        Task.motion(REACH, "`N` goes back to the previous match",
            PIPELINE, 1, 8, 0, 8, 1, "N"),
        Task.motion(REACH, "`/golf` finds it on line 7 first; `n` goes on to the one in the"
                + " brackets",
            PIPELINE, 0, 8, 7, 25, 7, "/golf<enter>n"),
        Task.motion(REACH, "`/me` and `enter` finds `merge`",
            PIPELINE, 7, 25, 2, 10, 4, "/me<enter>"),
        Task.motion(REACH, "`/cl` and `enter` finds `close`",
            PIPELINE, 2, 10, 9, 9, 4, "/cl<enter>"),
        Task.motion(REACH, "from the bottom, `/al` and `enter` goes round to `alpha` at the top",
            PIPELINE, 9, 9, 0, 0, 4, "/al<enter>"));

    /**
     * Practice for searching: a chain of word starts to reach, on a random text, each with the
     * shortest search that gets there, counting any n or N presses.
     */
    private static List<Task> searches(Random r) {
        String text = pick(r, PIPELINE, FOX, TOOLS, RENDER);
        String[] lines = text.split("\n", -1);
        List<int[]> starts = new ArrayList<>();
        for (int row = 0; row < lines.length; row++) {
            for (int col = 0; col < lines[row].length(); col++) {
                boolean letter = Character.isLetter(lines[row].charAt(col));
                boolean first = col == 0 || !Character.isLetterOrDigit(lines[row].charAt(col - 1));
                if (letter && first) {
                    starts.add(new int[] {row, col});
                }
            }
        }
        List<Task> tasks = new ArrayList<>();
        int row = 0;
        int col = 0;
        while (tasks.size() < LESSON_TASKS) {
            int[] goal = starts.get(r.nextInt(starts.size()));
            // Close by is quicker with other keys; searching pays off further away.
            if (Math.abs(goal[0] - row) < 2) {
                continue;
            }
            String keys = shortestSearch(lines, row, col, goal[0], goal[1]);
            String word = lines[goal[0]].substring(goal[1]).split("[^A-Za-z0-9_]")[0];
            String[] parts = keys.split("<enter>", -1);
            String hint = "search for `" + word + "`: `" + parts[0] + "`, then `enter`"
                    + (parts[1].isEmpty() ? "" : ", then `" + parts[1] + "`");
            tasks.add(Task.motion(REACH, hint, text, row, col, goal[0], goal[1],
                    Keys.parse(keys).length(), keys));
            row = goal[0];
            col = goal[1];
        }
        return tasks;
    }

    /** The fewest keys to reach a word start with / and then n or N. */
    private static String shortestSearch(String[] lines, int row, int col, int goalRow,
                                         int goalCol) {
        String best = null;
        String word = lines[goalRow].substring(goalCol);
        for (int length = 1; length <= word.length(); length++) {
            String pattern = word.substring(0, length);
            // Every match, in the order enter, then n, would visit them.
            List<int[]> order = new ArrayList<>();
            List<int[]> wrapped = new ArrayList<>();
            for (int r = 0; r < lines.length; r++) {
                for (int c = lines[r].indexOf(pattern); c >= 0; c = lines[r].indexOf(pattern, c + 1)) {
                    boolean after = r > row || (r == row && c > col);
                    (after ? order : wrapped).add(new int[] {r, c});
                }
            }
            order.addAll(wrapped);
            int at = -1;
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i)[0] == goalRow && order.get(i)[1] == goalCol) {
                    at = i;
                }
            }
            if (at < 0) {
                continue;
            }
            int back = (order.size() - at) % order.size();
            String keys = "/" + pattern + "<enter>"
                    + (at <= back ? "n".repeat(at) : "N".repeat(back));
            if (best == null || Keys.parse(keys).length() < Keys.parse(best).length()) {
                best = keys;
            }
        }
        return best;
    }

    // ---- the lessons ----

    static final List<Lesson> ALL = List.of(
        movement("hjkl", "moving around", List.of(
                key("h", "move the cursor one character left"),
                key("j", "move the cursor down one line"),
                key("k", "move the cursor up one line"),
                key("l", "move the cursor one character right")),
            drill(List.of(FOX, TOOLS, RENDER), text -> BASIC, null, 2, 7)),

        movement("words", "word by word", List.of(
                key("w", "jump to the start of the next word"),
                key("b", "jump back to the start of a word"),
                key("e", "jump to the end of a word")),
            drill(List.of(FOX, TOOLS, RENDER), text -> WORD, text -> BASIC, 1, 4)),

        movement("line", "ends of the line", List.of(
                key("0", "jump to the very start of the line"),
                key("^", "jump to the first non-space character"),
                key("$", "jump to the end of the line")),
            drill(List.of(FOX, TOOLS, RENDER), text -> LINE, text -> WORD, 1, 3)),

        movement("jumps", "counts and big jumps", List.of(
                key("gg", "jump to the first line"), key("G", "jump to the last line"),
                key("7G", "jump to line 7 - any number works"),
                key("3j", "a number first repeats a move: 3j, 4w")),
            drill(List.of(PIPELINE), Lessons::jumpMoves, text -> LINE, 1, 4)),

        movement("find", "find a character", List.of(
                key("fx", "jump onto the next x in this line"),
                key("tx", "jump to just before the next x"),
                key("F T", "like f and t, but to the left"),
                key(";", "repeat the last f, t, F or T"),
                key(",", "repeat it in the opposite direction")),
            drill(List.of(FOX, TOOLS, RENDER), Lessons::findMoves, text -> LINE, 2, 3)),

        movement("bigwords", "WORDs and brackets", List.of(
                key("W", "jump to the next WORD, past punctuation"),
                key("B", "jump back a WORD"),
                key("E", "jump to the end of a WORD"),
                key("%", "jump to the matching bracket")),
            drill(List.of(CALLS, RENDER), text -> BIG, text -> LINE, 1, 4)),

        editing("chars", "fixing characters", List.of(
                key("x", "delete the character under the cursor"),
                key("rx", "replace the cursor's character with x"),
                key("u", "undo the last change")),
            CHARS),

        editing("insert", "inserting text", List.of(
                key("i", "start typing before the cursor"),
                key("a", "start typing after the cursor"),
                key("esc", "stop typing: back to normal mode")),
            INSERT),

        editing("open", "inserting at the edges", List.of(
                key("I", "start typing at the start of the line"),
                key("A", "start typing at the end of the line"),
                key("o", "add a line below and type on it"),
                key("O", "add a line above and type on it")),
            OPEN),

        editing("small", "small edits", List.of(
                key("X", "delete the character before the cursor"),
                key("s", "replace one character with typing"),
                key("S", "empty the line, then type it again"),
                key("J", "join the next line onto this one"),
                key("~", "switch a letter's case")),
            SMALL),

        editing("delete", "the delete operator", List.of(
                key("dw", "delete from the cursor to the next word"),
                key("dd", "delete the whole line"),
                key("D", "delete from the cursor to the line's end"),
                key("d + motion", "delete as far as a move goes: d0, dt)")),
            DELETE),

        editing("change", "the change operator", List.of(
                key("cw", "delete to the word's end, then type"),
                key("cc", "empty the line, then type it again"),
                key("C", "delete to the line's end, then type"),
                key("c + motion", "like d + motion, then start typing")),
            CHANGE),

        editing("put", "copy and paste", List.of(
                key("yy", "yank (copy) the whole line"),
                key("yw", "yank from the cursor to the next word"),
                key("p", "put (paste) after the cursor or below"),
                key("P", "put before the cursor or above"),
                key("dd p", "deleting copies too, so p puts it back")),
            PUT),

        editing("counts", "counts with operators", List.of(
                key("d2w", "delete two words"), key("3dd", "delete three lines"),
                key("c2w", "replace two words with what you type"),
                key("y3w", "operator, count and motion combine")),
            COUNTS),

        editing("objects", "text objects", List.of(
                key("iw", "the whole word the cursor is in"),
                key("aw", "the word and the space after it"),
                key("i\"", "the text inside the quotes"),
                key("i(", "the text inside the brackets"),
                key("d c y", "type one after an operator: ciw, di(")),
            OBJECTS),

        editing("brackets", "brackets and quotes", List.of(
                key("a\"", "the quotes, what's inside, and a space"),
                key("a(", "the brackets and what's inside"),
                key("i[", "inside square brackets"),
                key("i{", "inside curly braces"),
                key("iW", "a WORD: everything up to the spaces")),
            BRACKETS),

        editing("repeat", "repeat and undo", List.of(
                key(".", "do the last change again"), key("u", "undo the last change"),
                key("ctrl-r", "redo what u undid")),
            REPEAT),

        editing("visual", "visual mode", List.of(
                key("v", "start selecting characters"),
                key("V", "start selecting whole lines"),
                key("motions", "move to grow or shrink the selection"),
                key("d y c", "delete, copy or change the selection"),
                key("esc", "stop selecting, change nothing")),
            VISUAL),

        new Lesson("search", "searching", Lesson.Kind.LESSON, List.of(
                key("/text", "then enter: jump to the next match"),
                key("n", "jump to the next match"),
                key("N", "jump to the previous match")),
            r -> SEARCH_GUIDED.stream().map(t -> t.in("search").guided()).toList(),
            r -> searches(r).stream().map(t -> t.in("search")).toList(),
            r -> {
                List<Task> all = searches(r);
                return all.get(r.nextInt(all.size())).in("search");
            }, null),

        new Lesson("random", "random mix", Lesson.Kind.RANDOM_MIX, List.of(), null, null, null,
            "Ten tasks, each from a lesson picked at random. No hints: the bar along the "
                + "bottom shows the keys of the lesson each task comes from, and the rest is up "
                + "to you."),

        new Lesson("weak", "weak spots", Lesson.Kind.WEAK_SPOTS, List.of(), null, null, null,
            "Ten tasks from the lessons you have tried, drawn most often from the ones where "
                + "your recent efficiency is lowest. No hints."));

    /** The lessons proper, without the mixes. */
    static final List<Lesson> LESSONS = ALL.stream().filter(l -> !l.isMix()).toList();

    private Lessons() {
    }

    static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    static Lesson byId(String id) {
        int i = indexOf(id);
        return i < 0 ? null : ALL.get(i);
    }

    // ---- building lessons ----

    /** An editing lesson: guided, one task per generator in order; practice, drawn at random. */
    private static Lesson editing(String id, String title, List<Lesson.Key> keys,
                                  List<Function<Random, Task>> generators) {
        return new Lesson(id, title, Lesson.Kind.LESSON, keys,
            r -> generators.stream().map(g -> checked(g, r).in(id).guided()).toList(),
            r -> {
                List<Task> tasks = new ArrayList<>();
                int last = -1;
                while (tasks.size() < LESSON_TASKS) {
                    int g = r.nextInt(generators.size());
                    if (g != last) {
                        tasks.add(checked(generators.get(g), r).in(id));
                        last = g;
                    }
                }
                return tasks;
            },
            r -> checked(generators.get(r.nextInt(generators.size())), r).in(id), null);
    }

    /**
     * A movement lesson: a drill of targets, the same either way but for the hints. For the
     * mixes, a single target from a random place.
     */
    private static Lesson movement(String id, String title, List<Lesson.Key> keys, Drill drill) {
        return new Lesson(id, title, Lesson.Kind.LESSON, keys,
            r -> drill.make(r, false).stream().map(t -> t.in(id).guided()).toList(),
            r -> drill.make(r, false).stream().map(t -> t.in(id)).toList(),
            r -> drill.make(r, true).get(0).in(id), null);
    }

    /** Makes a drill's chain of targets, or one target from a random starting place. */
    private interface Drill {
        List<Task> make(Random random, boolean single);
    }

    // ---- the mixes ----

    /** Ten practice tasks, each from a lesson picked at random, never the same one twice running. */
    static List<Task> randomMix(Random r) {
        List<Task> tasks = new ArrayList<>();
        Lesson last = null;
        while (tasks.size() < MIX_TASKS) {
            Lesson lesson = LESSONS.get(r.nextInt(LESSONS.size()));
            if (lesson != last) {
                tasks.add(lesson.one().apply(r));
                last = lesson;
            }
        }
        return tasks;
    }

    /**
     * Ten practice tasks from the lessons with a known efficiency, each lesson picked with a
     * weight that grows the further below par its recent efficiency is. With nothing known yet,
     * it is the random mix.
     */
    static List<Task> weakSpots(Random r, Map<String, Double> efficiency) {
        List<Lesson> tried = LESSONS.stream().filter(l -> efficiency.containsKey(l.id())).toList();
        if (tried.isEmpty()) {
            return randomMix(r);
        }
        double[] weights = tried.stream().mapToDouble(l -> weight(efficiency.get(l.id())))
                .toArray();
        double total = java.util.Arrays.stream(weights).sum();
        List<Task> tasks = new ArrayList<>();
        Lesson last = null;
        while (tasks.size() < MIX_TASKS) {
            double at = r.nextDouble() * total;
            int i = 0;
            while (i < weights.length - 1 && at >= weights[i]) {
                at -= weights[i++];
            }
            Lesson lesson = tried.get(i);
            if (lesson != last || tried.size() == 1) {
                tasks.add(lesson.one().apply(r));
                last = lesson;
            }
        }
        return tasks;
    }

    /** How strongly weak spots favour a lesson: far more below par than near it. */
    static double weight(double efficiency) {
        double gap = 130 - Math.max(20, Math.min(125, efficiency));
        return gap * gap;
    }

    // ---- helpers ----

    /**
     * Runs a generator until it produces a task whose solution really works: random words can
     * collide, for example when the letter to find also appears earlier in the line.
     */
    private static Task checked(Function<Random, Task> generator, Random random) {
        Task task = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            task = generator.apply(random);
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
        throw new IllegalStateException("a task generator never produced a solvable task: '"
                + task.hint() + "', '" + task.solution() + "' on:\n" + task.start());
    }

    private static Lesson.Key key(String key, String does) {
        return new Lesson.Key(key, does);
    }

    private static List<String> plus(List<String> base, String... more) {
        List<String> all = new ArrayList<>(base);
        all.addAll(List.of(more));
        return List.copyOf(all);
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
     * {@code known} keys from earlier lessons could manage. The hint is the shortest way.
     */
    private static Drill drill(
            List<String> texts, Function<String, List<String>> moves,
            Function<String, List<String>> known, int minPar, int maxPar) {
        return (random, single) -> {
            String text = texts.get(random.nextInt(texts.size()));
            String[] lines = text.split("\n");
            Vim vim = new Vim(text, 0, 0);
            List<Task> tasks = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            int row = 0;
            int col = 0;
            if (single) {
                // Anywhere on the text, as long as it is on a character.
                do {
                    row = random.nextInt(lines.length);
                    col = lines[row].isEmpty() ? 0 : random.nextInt(lines[row].length());
                } while (lines[row].isEmpty() || lines[row].charAt(col) == ' ');
            }
            // The column j and k aim for carries over from one target to the next.
            int want = col;
            for (int t = 0; t < (single ? 1 : LESSON_TASKS); t++) {
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
                tasks.add(Task.motion(REACH, "reach the highlight with `" + path.keys + "`", text,
                        row, col, goal[0], goal[1], path.cost, path.keys));
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
