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
                "`x` deletes the one character under the cursor. Here the cursor already sits on "
                    + "the "
                    + "extra letter",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            String start = with(goal, 4 + i, absentLetter(r, goal));
            return Task.edit("fix the wrong letter",
                "`r` and then a letter replaces the character under the cursor with that letter, "
                    + "without going into insert mode. The cursor is already on the wrong one",
                at(start, 4 + i), goal, "r" + word.charAt(i));
        },
        r -> {
            String noun = pick(r, NOUN);
            String goal = pick(r, ADJ) + " " + noun + " " + pick(r, VERB);
            return Task.edit("remove the stray `!` at the end",
                "`$` jumps to the last character of the line from anywhere on it, and `x` deletes "
                    + "the "
                    + "character under the cursor",
                "|" + goal + "!", goal, "$x");
        },
        r -> {
            String word = pick(r, PREP);
            int i = r.nextInt(word.length());
            String verb = pick(r, VERB);
            String rest = " the " + pick(r, ADJ) + " " + pick(r, NOUN);
            String typo = word.substring(0, i + 1) + word.substring(i);
            return Task.edit("find the doubled letter and delete one",
                "`f` and a letter jump onto the next copy of that letter on the line, quicker than "
                    + "stepping there with `l`. Get onto either of the doubled letters, then `x`",
                "|" + verb + " " + typo + rest, verb + " " + word + rest,
                "f" + word.charAt(i) + "x");
        },
        r -> {
            String name = pick(r, NAME);
            int from = 1 + r.nextInt(8);
            int to = from + 1;
            return Task.edit("make the number `" + to + "`",
                "The number is the last thing on the line, so `$` reaches it in one key, and `r` "
                    + "swaps one character for another",
                "|" + name + " = " + from, name + " = " + to, "$r" + to);
        },
        r -> {
            String word = pick(r, "box", "bag", "cup", "jar", "mug", "pot");
            String goal = pick(r, "fill", "pack", "stock") + " my " + word + " with "
                    + pick(r, ITEM);
            char wrong = absentLetter(r, goal);
            String typo = with(word, 1, wrong);
            return Task.edit("fix the word `" + typo + "`",
                "`f` and a letter jump straight onto a letter you can name, like the wrong one "
                    + "here, "
                    + "and `r` then replaces it",
                "|" + goal.replace(" " + word + " ", " " + typo + " "), goal,
                "f" + wrong + "r" + word.charAt(1));
        },
        r -> {
            int n = 2 + r.nextInt(4);
            String noise = pick(r, "#", "*", "~", "%").repeat(n);
            String noun = pick(r, NOUN);
            return Task.edit("remove the `" + noise + "`",
                "A number typed before a command repeats it, so a number before `x` deletes that "
                    + "many "
                    + "characters at once",
                "feed |" + noise + "the " + noun, "feed the " + noun, n + "x");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length());
            String goal = "the " + word + " " + pick(r, NOUN) + " " + pick(r, VERB);
            char wrong = absentLetter(r, goal);
            String start = with(goal, 4 + i, wrong) + goal.charAt(goal.length() - 1);
            return Task.edit("two typos: fix both",
                "One fix at a time: `f` reaches a letter you name and `r` replaces it; `$` "
                    + "reaches the "
                    + "end of the line, where `x` can delete",
                "|" + start, goal, "f" + wrong + "r" + word.charAt(i) + "$x");
        });

    private static final List<Function<Random, Task>> INSERT = List.of(
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("add the missing `" + word.charAt(i) + "`",
                "`i` starts typing just before the character under the cursor, so the missing "
                    + "letter "
                    + "lands in front of it. `esc` stops typing",
                at("the " + without(word, i) + rest, 4 + i), "the " + word + rest,
                "i" + word.charAt(i) + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = 1 + r.nextInt(word.length() - 1);
            String rest = " " + pick(r, NOUN);
            return Task.edit("add the missing `" + word.charAt(i) + "`",
                "`a` (append) starts typing just after the character under the cursor: right when "
                    + "the "
                    + "missing letter belongs after it. `esc` stops typing",
                at("the " + without(word, i) + rest, 4 + i - 1), "the " + word + rest,
                "a" + word.charAt(i) + "<esc>");
        },
        r -> {
            String goal = pick(r, "hello", "thanks", "see you", "good night") + " "
                    + pick(r, PEOPLE);
            return Task.edit("add a `!` at the end",
                "The `!` belongs after the last letter, where the cursor is, so this is a job for "
                    + "`a`, "
                    + "which types after the cursor rather than before it",
                at(goal, goal.length() - 1), goal + "!", "a!<esc>");
        },
        r -> {
            String extra = pick(r, EXTRA);
            String adj = pick(r, ADJ);
            String noun = pick(r, NOUN);
            return Task.edit("add the word `" + extra + "`",
                "`i` types in front of the character under the cursor. The new word goes in front "
                    + "of "
                    + "this one, so remember the space between them",
                "the |" + adj + " " + noun, "the " + extra + " " + adj + " " + noun,
                "i" + extra + " <esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = pick(r, NOUN);
            return Task.edit("add the missing word `" + adj[0] + "`",
                "Move to where the word goes first: `w` jumps to the start of the next word. Then "
                    + "`i` "
                    + "types in front of it",
                "|the " + adj[1] + " " + noun, "the " + adj[0] + " " + adj[1] + " " + noun,
                "wi" + adj[0] + " <esc>");
        },
        r -> {
            String first = pick(r, "hello", "sorry", "thanks", "okay", "well", "listen");
            String second = pick(r, NOUN);
            return Task.edit("add a comma after `" + first + "`",
                "The comma belongs right after the word's last letter: `e` jumps to the end of a "
                    + "word, "
                    + "and `a` types after the cursor",
                "|" + first + " " + second, first + ", " + second, "ea,<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String op = pick(r, "*", "+", "-", "/");
            return Task.edit("put a `" + op + "` between " + n[1] + " and " + n[2],
                "A number before `w` jumps that many words at once. Count the words to where the "
                    + "sign "
                    + "goes, then type in front of that word with `i`",
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
                "Two spots need text: before the `=`, which `w` reaches, and after the last "
                    + "digit, which "
                    + "`$` reaches. `i` types before the cursor, `a` after it",
                "|" + name + " = " + digit, goal, "wi" + op + "<esc>$a0<esc>");
        });

    private static final List<Function<Random, Task>> OPEN = List.of(
        r -> {
            String line = "return " + pick(r, NAME);
            return Task.edit("add `;` at the end of the line",
                "`A` starts typing at the very end of the line wherever the cursor is, so there "
                    + "is no "
                    + "need to move there first",
                "|" + line, line + ";", "A;<esc>");
        },
        r -> {
            String word = pick(r, "let", "const", "var");
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("add `" + word + "` at the start of the line",
                "`I` starts typing at the start of the line's text wherever the cursor is, the "
                    + "opposite "
                    + "end from `A`",
                at(line, line.indexOf('=')), word + " " + line, "I" + word + " <esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add `" + s[2] + "` as a new line after `" + s[1] + "`",
                "`o` opens a new, empty line below the cursor's line and starts typing on it, all "
                    + "in "
                    + "one key",
                s[0] + "\n|" + s[1] + "\n" + s[3], String.join("\n", s), "o" + s[2] + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add `" + s[0] + "` as a new line at the top",
                "`O`, the capital, opens a new line above the cursor's line instead of below it",
                "|" + s[1] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "O" + s[0] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String first = assignment(r, n[0]);
            String second = assignment(r, n[1]);
            return Task.edit("add `;` to the end of the second line",
                "The `;` belongs at the end of the line below: `j` moves down a line, and `A` "
                    + "types at "
                    + "the end of whatever line the cursor is on",
                "|" + first + "\n" + second, first + "\n" + second + ";", "jA;<esc>");
        },
        r -> {
            String comment = "// " + pick(r, "setup", "inputs", "defaults", "helpers");
            String line = assignment(r, pick(r, NAME));
            return Task.edit("add the comment `" + comment + "` above",
                "A new line above the current one is exactly what `O` makes, ready to type into",
                "|" + line, comment + "\n" + line, "O" + comment + "<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("add a line above and a line below",
                "`O` opens a line above and `o` one below. After adding the first, the cursor is "
                    + "on "
                    + "the new line, so `j` takes you back to the middle one",
                "|" + s[1], s[0] + "\n" + s[1] + "\n" + s[2],
                "O" + s[0] + "<esc>jo" + s[2] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String func = pick(r, FUNC);
            return Task.edit("make it `const " + name + " = " + func + "();`",
                "Both ends of the line need text: `I` types at its start and `A` at its end, from "
                    + "wherever the cursor is",
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
                "`X`, the capital, deletes the character before the cursor, like backspace, where "
                    + "`x` "
                    + "deletes the one under it. The cursor is just past the extra letter",
                at("the " + typo + rest, 4 + i + 1), "the " + word + rest, "X");
        },
        r -> {
            int n = 2 + r.nextInt(3);
            String name = pick(r, NAME);
            String mark = pick(r, "!", "?", "_");
            int value = 1 + r.nextInt(9);
            return Task.edit("remove the `" + mark.repeat(n) + "`",
                "`X` deletes the character to the left of the cursor, and a number before it "
                    + "deletes "
                    + "that many in one go",
                name + mark.repeat(n) + "| = " + value, name + " = " + value, n + "X");
        },
        r -> {
            String[] it = several(r, ITEM, 2);
            return Task.edit("replace the `&` with `and`",
                "`s` deletes the character under the cursor and starts typing, so one character "
                    + "can "
                    + "become several. `r` couldn't: it swaps one character for one",
                it[0] + " |& " + it[1], it[0] + " and " + it[1], "sand<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            String typo = "" + s[1].charAt(1) + s[1].charAt(0) + s[1].substring(2);
            return Task.edit("rewrite the middle line as `" + s[1] + "`",
                "`S` empties the whole line and starts typing, for when a line is quicker to type "
                    + "again "
                    + "than to fix letter by letter",
                s[0] + "\n|" + typo + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2],
                "S" + s[1] + "<esc>");
        },
        r -> {
            String first = "the " + pick(r, ADJ);
            String second = pick(r, NOUN) + " " + pick(r, VERB);
            return Task.edit("join the two lines into one",
                "`J` pulls the line below up onto the end of this one, with a space between them",
                "|" + first + "\n" + second, first + " " + second, "J");
        },
        r -> {
            String[] it = several(r, ITEM, 3);
            return Task.edit("make the three lines one",
                "`J` joins only the next line onto this one, so gathering three lines takes it "
                    + "more "
                    + "than once",
                "|" + String.join("\n", it), String.join(" ", it), "JJ");
        },
        r -> {
            String name = pick(r, PEOPLE);
            String rest = " " + pick(r, VERB) + " home";
            String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return Task.edit("capitalize `" + name + "`",
                "`~` flips the letter under the cursor between lower and upper case, then steps "
                    + "right",
                "|" + name + rest, upper + rest, "~");
        },
        r -> {
            String word = pick(r, "todo", "note", "fixme", "bug");
            String rest = ": " + pick(r, FUNC) + " the " + pick(r, ITEM);
            return Task.edit("make `" + word + "` all capitals",
                "`~` flips one letter and steps right, so a number before it flips that many "
                    + "letters "
                    + "in a row. Count the letters",
                "|" + word + rest, word.toUpperCase() + rest, word.length() + "~");
        });

    private static final List<Function<Random, Task>> DELETE = List.of(
        r -> {
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("delete the extra word",
                "`d` deletes as far as the motion after it moves. `w` moves to the next word, so "
                    + "`d` "
                    + "with `w` deletes the word and its space",
                "the |" + pick(r, EXTRA) + " " + rest, "the " + rest, "dw");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            List<String> lines = new ArrayList<>(List.of(assignment(r, n[0]),
                    assignment(r, n[1])));
            String goal = String.join("\n", lines);
            lines.add(r.nextInt(3), "|" + pick(r, JUNK));
            return Task.edit("delete the junk line",
                "Pressing an operator twice works on the whole line, so `d` twice deletes the "
                    + "line wherever the cursor is in it",
                String.join("\n", lines), goal, "dd");
        },
        r -> {
            String line = assignment(r, pick(r, NAME)) + ";";
            return Task.edit("delete the comment",
                "`D` deletes from the cursor to the end of the line, the same as `d` with `$`",
                line + "| // " + pick(r, "temporary", "fix later", "old value", "remove"), line,
                "D");
        },
        r -> {
            String call = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("delete the label before the call",
                "`d` works with any motion. `0` is the motion to the start of the line, so the two "
                    + "together delete everything before the cursor",
                pick(r, "debug", "todo", "note", "temp") + ": |" + call, call, "d0");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String keep = pick(r, FUNC) + "(" + n[0] + ", " + n[1];
            return Task.edit("keep only the first two arguments",
                "`t)` is the motion that stops just before the next `)`. After `d` it deletes up "
                    + "to "
                    + "there and leaves the bracket",
                keep + "|, " + n[2] + ", " + n[3] + ")", keep + ")", "dt)");
        },
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both junk lines",
                "`j` moves down a line. As the motion after `d` it deletes whole lines: this one "
                    + "and "
                    + "the one below",
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
                "`gg` moves to the first line. After `d` it deletes every line from here up to the "
                    + "top",
                start + keep, keep, "dgg");
        },
        r -> {
            String header = pick(r, NAME) + " report";
            StringBuilder start = new StringBuilder(header);
            for (int i = 0, n = 2 + r.nextInt(3); i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("junk ").append(i + 1);
            }
            return Task.edit("delete everything below the first line",
                "`G` moves to the last line. After `d` it deletes every line from here to the end",
                start.toString(), header, "dG");
        });

    private static final List<Function<Random, Task>> CHANGE = List.of(
        r -> {
            String[] adj = several(r, ADJ, 3);
            String rest = " " + adj[2] + " " + pick(r, NOUN);
            return Task.edit("make `" + adj[1] + "` say `" + adj[0] + "`",
                "`c` deletes what a motion covers and starts typing. With `w` that's the rest of "
                    + "the "
                    + "word; unlike `d` with `w`, the space after it stays",
                "the |" + adj[1] + rest, "the " + adj[0] + rest, "cw" + adj[0] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("make it return `" + n[2] + "`",
                "`C` deletes from the cursor to the end of the line and starts typing there",
                "return |" + n[0] + " + " + n[1] + ";", "return " + n[2] + ";",
                "C" + n[2] + ";<esc>");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            String typo = "" + s[1].charAt(1) + s[1].charAt(0) + s[1].substring(2);
            return Task.edit("fix `" + typo + "`",
                "Pressing `c` twice changes the whole line: it is emptied and you type it again",
                s[0] + "\n|" + typo + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2],
                "cc" + s[1] + "<esc>");
        },
        r -> {
            String[] c = several(r, new String[] {"red", "blue", "gold", "teal", "pink", "gray"},
                    2);
            String property = pick(r, "color", "border", "fill");
            return Task.edit("make the value `" + c[1] + "`",
                "`t;` stops just before the `;`, so after `c` it replaces everything up to it and "
                    + "keeps "
                    + "the rest of the line",
                property + ": |" + c[0] + "; /* keep */", property + ": " + c[1] + "; /* keep */",
                "ct;" + c[1] + "<esc>");
        },
        r -> {
            String func = pick(r, FUNC);
            String[] n = several(r, NAME, 2);
            return Task.edit("make the argument `" + n[1] + "`",
                "`t)` stops just before the bracket, so with `c` it replaces the argument and "
                    + "keeps "
                    + "the bracket",
                func + "(|" + n[0] + ")", func + "(" + n[1] + ")", "ct)" + n[1] + "<esc>");
        },
        r -> {
            String[] labels = several(r, new String[] {"old", "draft", "temp", "todo", "new",
                "final"}, 2);
            String value = pick(r, ITEM);
            return Task.edit("change the label to `" + labels[1] + ":`",
                "`0` is the motion to the start of the line, so with `c` it replaces everything "
                    + "before "
                    + "the cursor",
                labels[0] + ": |" + value, labels[1] + ": " + value,
                "c0" + labels[1] + ": <esc>");
        },
        r -> {
            String name = pick(r, NAME);
            int to = 100 + r.nextInt(900);
            return Task.edit("change the number to `" + to + "`",
                "Reach the number first: `$` goes to the end of the line and `b` back to the "
                    + "start of "
                    + "the word there. Then `c` with `w` replaces it",
                "|const " + name + " = " + (10 + r.nextInt(90)) + ";",
                "const " + name + " = " + to + ";", "$bcw" + to + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String[] prep = several(r, PREP, 2);
            String middle = " " + pick(r, NOUN) + " " + pick(r, VERB) + " ";
            return Task.edit("two words are wrong: change both",
                "Each wrong word needs reaching, then changing. `w`, `$` and `b` get you to a "
                    + "word's "
                    + "first letter; `c` with `w` replaces it from there",
                "|the " + adj[0] + " " + adj[1] + middle + prep[0],
                "the " + adj[0] + " " + adj[2] + middle + prep[1],
                "2wcw" + adj[2] + "<esc>$bcw" + prep[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> PUT = List.of(
        r -> {
            String line = pick(r, FUNC) + "(" + pick(r, NAME) + ")";
            return Task.edit("duplicate the line",
                "`y` twice copies (yanks) the whole line, and `p` puts a copied line on a new line "
                    + "below",
                "|" + line, line + "\n" + line, "yyp");
        },
        r -> {
            String word = pick(r, "very", "so", "too", "far");
            String rest = " " + pick(r, ADJ);
            return Task.edit("say `" + word + "` twice",
                "`y` with `w` copies the word and its space. `P` puts a copy before the cursor, "
                    + "`p` "
                    + "after it",
                "|" + word + rest, word + " " + word + rest, "ywP");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("put the lines in order",
                "Deleting keeps a copy, so `d` twice cuts the line. The cursor then lands on the "
                    + "next "
                    + "line, and `p` puts the cut line below that",
                "|" + s[1] + "\n" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move `" + s[0] + "` to the top",
                "Cut the line, move to the line it belongs above, and `P` puts a cut line above "
                    + "the "
                    + "cursor's line",
                s[1] + "\n|" + s[0] + "\n" + s[2], s[0] + "\n" + s[1] + "\n" + s[2], "ddkP");
        },
        r -> {
            String word = pick(r, ADJ);
            int i = r.nextInt(word.length() - 1);
            String typo = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i + 2);
            String rest = " " + pick(r, NOUN);
            return Task.edit("swap the two letters",
                "`x` cuts a letter, keeping a copy, and the cursor moves onto the next letter. "
                    + "`p` puts "
                    + "the copy after that, so the two swap",
                at("the " + typo + rest, 4 + i), "the " + word + rest, "xp");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move `" + s[0] + "` to the top",
                "Cut the line, then `gg` goes to the first line, and `P` puts the cut line above "
                    + "it",
                s[1] + "\n" + s[2] + "\n|" + s[0], s[0] + "\n" + s[1] + "\n" + s[2], "ddggP");
        },
        r -> {
            String head = pick(r, NAME) + " list";
            String[] it = several(r, ITEM, 2);
            return Task.edit("copy the first line to the end",
                "Copy the line, go to the last line with `G`, and `p` puts the copy below it",
                "|" + head + "\n" + it[0] + "\n" + it[1],
                head + "\n" + it[0] + "\n" + it[1] + "\n" + head, "yyGp");
        },
        r -> {
            String word = pick(r, ITEM);
            int copies = 2 + r.nextInt(2);
            return Task.edit("make " + (copies + 1) + " rows of `" + word + "`",
                "`y` twice copies the line; each `p` puts another copy, or a number before `p` "
                    + "puts "
                    + "that many at once",
                "|" + word + "\nend", (word + "\n").repeat(copies + 1) + "end",
                copies == 2 ? "yypp" : "yy3p");
        });

    private static final List<Function<Random, Task>> COUNTS = List.of(
        r -> {
            int n = 2 + r.nextInt(2);
            String rest = pick(r, ADJ) + " " + pick(r, NOUN);
            return Task.edit("delete the " + n + " extra words",
                "A number between `d` and `w` stretches the motion over that many words, so one "
                    + "command deletes them all",
                "the |" + (pick(r, EXTRA) + " ").repeat(n) + rest, "the " + rest, "d" + n + "w");
        },
        r -> {
            int n = 2 + r.nextInt(3);
            StringBuilder start = new StringBuilder("keep");
            for (int i = 0; i < n; i++) {
                start.append(i == 0 ? "\n|" : "\n").append("drop ").append(i + 1);
            }
            return Task.edit("delete the " + n + " drop lines",
                "A number before `dd` deletes that many lines, starting with the cursor's",
                start + "\nkeep too", "keep\nkeep too", n + "dd");
        },
        r -> {
            String[] adj = several(r, ADJ, 3);
            String noun = " " + pick(r, NOUN);
            return Task.edit("replace both words with `" + adj[2] + "`",
                "A number between `c` and `w` covers that many words: they are all deleted and you "
                    + "type the one word that replaces them",
                "the |" + adj[0] + " " + adj[1] + noun, "the " + adj[2] + noun,
                "c2w" + adj[2] + "<esc>");
        },
        r -> {
            String[] adj = several(r, ADJ, 4);
            String noun = " " + pick(r, NOUN);
            return Task.edit("replace the three words with `" + adj[3] + "`",
                "Count the words to replace: a number between `c` and `w` covers them all at once",
                "the |" + adj[0] + " " + adj[1] + " " + adj[2] + noun, "the " + adj[3] + noun,
                "c3w" + adj[3] + "<esc>");
        },
        r -> {
            String word = pick(r, "ho", "la", "na", "ha");
            String end = pick(r, "end", "done", "stop");
            return Task.edit("double the `" + word + " " + word + "`",
                "A number before `w` makes `y` copy that many words, and `P` puts a copy before "
                    + "the "
                    + "cursor",
                "|" + word + " " + word + " " + end, (word + " ").repeat(4) + end, "y2wP");
        },
        r -> {
            String[] s = SEQUENCE[r.nextInt(SEQUENCE.length)];
            return Task.edit("move the first two lines to the bottom",
                "A number before `dd` cuts that many lines. Move to the line they belong after, "
                    + "and `p` "
                    + "puts them below it",
                "|" + s[2] + "\n" + s[3] + "\n" + s[0] + "\n" + s[1], String.join("\n", s),
                "2ddjp");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String two = assignment(r, n[0]) + "\n" + assignment(r, n[1]);
            return Task.edit("copy the first two lines to the end",
                "A number before `yy` copies that many lines. `G` goes to the end and `p` puts "
                    + "them "
                    + "below",
                "|" + two + "\n---", two + "\n---\n" + two, "2yyGp");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String verb = pick(r, VERB);
            return Task.edit("delete the two words before `" + verb + "`",
                "`b` moves back a word, and with a number that many words. After `d` it deletes "
                    + "back "
                    + "over them",
                "the " + adj[0] + " " + adj[1] + " " + pick(r, NOUN) + " |" + verb,
                "the " + adj[0] + " " + verb, "d2b");
        });

    private static final List<Function<Random, Task>> OBJECTS = List.of(
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = " " + pick(r, NOUN);
            return Task.edit("make the word `" + adj[1] + "`",
                "`iw` is the whole word the cursor is in, wherever in it the cursor sits, so `c` "
                    + "with "
                    + "it replaces the word without moving to its start",
                at("the " + adj[0] + noun, 4 + 1 + r.nextInt(adj[0].length() - 1)),
                "the " + adj[1] + noun, "ciw" + adj[1] + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            String before = "the " + pick(r, EXTRA) + " ";
            String noun = pick(r, NOUN);
            return Task.edit("delete the word `" + word + "`",
                "`aw` is the word the cursor is in plus a space, so `d` with it removes the word "
                    + "without leaving two spaces behind",
                at(before + word + " " + noun, before.length() + 1 + r.nextInt(word.length() - 1)),
                before + noun, "daw");
        },
        r -> {
            String func = pick(r, "say", "print", "log", "show");
            String[] words = several(r, ITEM, 3);
            String old = words[0] + " " + words[1];
            return Task.edit("make the quoted text `" + words[2] + "`",
                "`i\"` is everything between the quotes around the cursor, so `c` with it "
                    + "replaces the "
                    + "text and keeps the quote marks",
                at(func + "(\"" + old + "\")", func.length() + 2 + 1 + r.nextInt(old.length() - 1)),
                func + "(\"" + words[2] + "\")", "ci\"" + words[2] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String[] words = several(r, ITEM, 2);
            return Task.edit("empty the quotes",
                "`i\"` is what lies between the quotes, so `d` with it empties them and keeps the "
                    + "quote "
                    + "marks",
                name + " = \"|" + words[0] + " " + words[1] + "\";", name + " = \"\";", "di\"");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String func = pick(r, FUNC);
            return Task.edit("empty the brackets",
                "`i(` is everything between the round brackets around the cursor, so `d` with it "
                    + "empties them in one go",
                func + "(" + n[0] + ", |" + n[1] + ", " + n[2] + ")", func + "()", "di(");
        },
        r -> {
            String flag = pick(r, "ready", "done", "valid", "empty");
            return Task.edit("make the condition `" + flag + "`",
                "`i(` is everything between the brackets around the cursor, so `c` with it "
                    + "replaces "
                    + "the whole condition at once",
                "if (" + pick(r, NAME) + " |" + pick(r, ">", "<", "==") + " "
                        + (2 + r.nextInt(98)) + ") {",
                "if (" + flag + ") {", "ci(" + flag + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            String[] f = several(r, FUNC, 2);
            String args = n[0] + ", " + n[1];
            return Task.edit("give `" + f[1] + "()` the same arguments",
                "`y` with `i(` copies what's inside the brackets. Moving down lands the cursor on "
                    + "the "
                    + "empty brackets' `)`, and `P` puts the copy before it",
                f[0] + "(|" + args + ")\n" + f[1] + "()", f[0] + "(" + args + ")\n" + f[1] + "("
                    + args + ")", "yi(jP");
        },
        r -> {
            String name = pick(r, NAME);
            String[] words = several(r, ITEM, 2);
            return Task.edit("make the quoted text `" + words[1] + "`",
                "`i\"` also works with the cursor before the quotes on the line: it takes the next "
                    + "pair, so `c` with it replaces their contents from here",
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
                "`a\"` is the quoted text with its quote marks and the space after, so `d` with it "
                    + "removes the whole thing cleanly",
                at(verb + " \"" + item + "\" " + rest,
                    verb.length() + 2 + r.nextInt(item.length())),
                verb + " " + rest, "da\"");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String func = pick(r, FUNC);
            return Task.edit("delete the brackets and their contents",
                "`a(` is the brackets themselves and everything in them, where `i(` is only the "
                    + "inside",
                func + "(" + n[0] + ", |" + n[1] + ") + " + n[2], func + " + " + n[2], "da(");
        },
        r -> {
            String list = pick(r, "items", "rows", "cells", "keys");
            int to = r.nextInt(10);
            return Task.edit("make the index `" + to + "`",
                "`i[` is everything between square brackets, so `c` with it replaces the index",
                list + "[|" + pick(r, NAME) + " + 1]", list + "[" + to + "]",
                "ci[" + to + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String list = pick(r, "items", "rows", "cells", "keys");
            return Task.edit("remove the `[i]`",
                "`a[` is the square brackets and what's in them, so `d` with it removes the lot",
                name + " = " + list + "[|i];", name + " = " + list + ";", "da[");
        },
        r -> {
            String name = pick(r, NAME);
            String func = pick(r, FUNC);
            return Task.edit("empty the braces",
                "`i{` is everything between curly braces, so `d` with it empties them",
                "if (" + name + ") { |" + func + "(); }", "if (" + name + ") {}", "di{");
        },
        r -> {
            String[] f = several(r, FUNC, 2);
            return Task.edit("make the body `return 1;`",
                "`i{` is everything inside the braces, spaces included, so with `c` you type the "
                    + "body "
                    + "again with its spaces",
                f[0] + "() { |" + f[1] + "(); }", f[0] + "() { return 1; }",
                "ci{ return 1; <esc>");
        },
        r -> {
            String[] p = several(r, PATHS, 2);
            String verb = pick(r, "copy", "open", "edit", "move");
            String rest = pick(r, "now", "later", "first");
            int inside = 1 + r.nextInt(p[0].length() - 1);
            return Task.edit("replace `" + p[0] + "` with `" + p[1] + "`",
                "`iW` is a whole WORD: everything between spaces, slashes and dots included, where "
                    + "`iw` stops at them. `c` with it replaces the path",
                at(verb + " " + p[0] + " " + rest, verb.length() + 1 + inside),
                verb + " " + p[1] + " " + rest, "ciW" + p[1] + "<esc>");
        },
        r -> {
            String name = pick(r, NAME);
            String[] it = several(r, ITEM, 2);
            return Task.edit("make the quoted text `" + it[1] + "`",
                "`i'` is everything between single quotes, so `c` with it replaces the text",
                name + " = '|" + it[0] + "';", name + " = '" + it[1] + "';",
                "ci'" + it[1] + "<esc>");
        });

    private static final List<Function<Random, Task>> REPEAT = List.of(
        r -> {
            int n = 3 + r.nextInt(2);
            String last = pick(r, "yes", "go", "done", "stop");
            return Task.edit("delete every word but `" + last + "`",
                "`.` repeats your last change. Delete one word the usual way, and each `.` deletes "
                    + "another the same way",
                "|" + (pick(r, "no", "um", "so", "ha") + " ").repeat(n) + last, last,
                "dw" + ".".repeat(n - 1));
        },
        r -> {
            String[] n = several(r, NAME, 3);
            String[] lines = {assignment(r, n[0]), assignment(r, n[1]), assignment(r, n[2])};
            return Task.edit("end every line with `;`",
                "Make the change once; `.` repeats it, typing included, so move down with `j` and "
                    + "repeat it on each line",
                "|" + String.join("\n", lines), String.join(";\n", lines) + ";", "A;<esc>j.j.");
        },
        r -> {
            String[] keep = several(r, ITEM, 3);
            String drop = pick(r, "drop", "junk", "skip");
            return Task.edit("delete every `" + drop + "` line",
                "Delete one line; once the cursor is on the next one to go, `.` repeats the delete "
                    + "there",
                keep[0] + "\n|" + drop + "\n" + keep[1] + "\n" + drop + "\n" + keep[2],
                String.join("\n", keep), "ddj.");
        },
        r -> {
            String[] items = several(r, ITEM, 3);
            String bullet = pick(r, "-", "*", ">");
            return Task.edit("start every line with `" + bullet + " `",
                "Add the bullet once with `I`. `.` repeats the whole insertion, so on each other "
                    + "line "
                    + "it's one key",
                "|" + String.join("\n", items),
                bullet + " " + String.join("\n" + bullet + " ", items),
                "I" + bullet + " <esc>j.j.");
        },
        r -> {
            String[] words = several(r, new String[] {"wow", "nice", "great", "fine", "yes",
                "cool"}, 3);
            return Task.edit("remove both `!`",
                "Find and delete the first `!`. Then `;` repeats the find and `.` repeats the "
                    + "delete, "
                    + "so the second costs two keys",
                "|" + words[0] + "! " + words[1] + "! " + words[2],
                words[0] + " " + words[1] + " " + words[2], "f!x;.");
        },
        r -> {
            String[] letters = several(r, "a b c d e g h k m n p s".split(" "), 4);
            String gap = pick(r, "-", "_", "+");
            return Task.edit("turn each `" + gap + "` into a space",
                "Fix one gap with `f` and `r`. After that `;` finds the next gap and `.` repeats "
                    + "the "
                    + "replacement",
                "|" + String.join(gap, letters), String.join(" ", letters),
                "f" + gap + "r ;.;.");
        },
        r -> {
            String[] n = several(r, NAME, 2);
            return Task.edit("rename both `" + n[0] + "` to `" + n[1] + "`",
                "Rename the first with `c` and `w`. `.` repeats that rename, so get onto the "
                    + "second "
                    + "name's first letter and press it",
                "|" + n[0] + " = 1\n" + n[0] + " = 2", n[1] + " = 1\n" + n[1] + " = 2",
                "cw" + n[1] + "<esc>jb.");
        },
        r -> {
            String[] n = several(r, NAME, 3);
            return Task.edit("rename every `" + n[0] + "` to `" + n[1] + "`",
                "Change one with `c` and `w`, then go to the first letter of each of the others "
                    + "and "
                    + "repeat it with `.`",
                "|" + n[0] + "(" + n[0] + ", " + n[2] + ", " + n[0] + ")",
                n[1] + "(" + n[1] + ", " + n[2] + ", " + n[1] + ")",
                "cw" + n[1] + "<esc>ww.$b.");
        });

    private static final List<Function<Random, Task>> VISUAL = List.of(
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both drop lines",
                "`V` selects whole lines; moving down with `j` adds the next line to the "
                    + "selection, "
                    + "and `d` deletes whatever is selected",
                keep[0] + "\n|drop\ndrop too\n" + keep[1], keep[0] + "\n" + keep[1], "Vjd");
        },
        r -> {
            String[] keep = several(r, ITEM, 2);
            return Task.edit("delete both drop lines",
                "`V` selects whole lines, and moving up with `k` adds the line above; `d` then "
                    + "deletes "
                    + "both",
                keep[0] + "\ndrop\n|drop too\n" + keep[1], keep[0] + "\n" + keep[1], "Vkd");
        },
        r -> {
            String[] adj = several(r, ADJ, 2);
            String noun = pick(r, NOUN);
            return Task.edit("change `" + adj[0] + "` to `" + adj[1] + "`",
                "`v` starts selecting at the cursor, a motion like `e` stretches the selection, "
                    + "and "
                    + "`c` replaces what is selected",
                "the |" + adj[0] + " " + noun, "the " + adj[1] + " " + noun,
                "vec" + adj[1] + "<esc>");
        },
        r -> {
            String[] n = several(r, NAME, 4);
            String func = pick(r, "max", "min", "abs", "sum");
            return Task.edit("make the argument `" + n[3] + "`",
                "`v` starts selecting, `t)` stretches the selection to just before the bracket, "
                    + "and `c` "
                    + "replaces it",
                func + "(|" + n[0] + " + " + n[1] + " * " + n[2] + ")", func + "(" + n[3] + ")",
                "vt)c" + n[3] + "<esc>");
        },
        r -> {
            String word = pick(r, ADJ);
            String noun = pick(r, NOUN);
            return Task.edit("delete the word `" + word + "`",
                "In visual mode a text object selects itself: `aw` selects the word with its "
                    + "space, "
                    + "ready for `d`",
                at("the " + word + " " + noun, 4 + 1 + r.nextInt(word.length() - 1)),
                "the " + noun, "vawd");
        },
        r -> {
            String[] items = several(r, ITEM, 2);
            String two = items[0] + "\n" + items[1];
            return Task.edit("copy the first two lines to the end",
                "Select both lines with `V`, copy them with `y`, then go to the end and `p` puts "
                    + "them "
                    + "below",
                "|" + two + "\n---", two + "\n---\n" + two, "VjyGp");
        },
        r -> {
            String fresh = pick(r, ITEM);
            return Task.edit("replace both old lines with `" + fresh + "`",
                "Select both lines with `V`; `c` replaces everything selected with what you type",
                "start\n|old line 1\nold line 2\nend", "start\n" + fresh + "\nend",
                "Vjc" + fresh + "<esc>");
        },
        r -> {
            String title = pick(r, NAME) + " list";
            String[] items = several(r, ITEM, 2 + r.nextInt(3));
            return Task.edit("delete everything below the title",
                "`V` selects whole lines and `G` stretches the selection to the last line, so `d` "
                    + "deletes everything from here down",
                title + "\n|" + String.join("\n", items), title, "VGd");
        });

    // ---- searching ----

    /** The walk-through: fixed targets on one text, each search teaching something new. */
    private static final List<Task> SEARCH_GUIDED = List.of(
        Task.motion(REACH, "`/` starts a search: type what you're looking for and press "
                + "`enter`, and the cursor jumps to where it next appears. The target is `sort`",
            PIPELINE, 0, 0, 4, 7, 6, "/sort<enter>"),
        Task.motion(REACH, "A search doesn't need the whole word, just enough letters that "
                + "nothing before the target matches. The target is `notify`",
            PIPELINE, 4, 7, 8, 8, 4, "/no<enter>"),
        Task.motion(REACH, "A search looks forward from the cursor and carries on from the top "
                + "at the end. If it stops at the wrong match, `n` goes on to the next one",
            PIPELINE, 8, 8, 1, 8, 7, "/load<enter>n"),
        Task.motion(REACH, "`N` goes back to the previous match of your last search, the "
                + "opposite of `n`",
            PIPELINE, 1, 8, 0, 8, 1, "N"),
        Task.motion(REACH, "`golf` appears twice. A search stops at the first match after the "
                + "cursor, and `n` moves on to the next",
            PIPELINE, 0, 8, 7, 25, 7, "/golf<enter>n"),
        Task.motion(REACH, "Search for `merge` with as few letters as make it the first match "
                + "ahead of the cursor",
            PIPELINE, 7, 25, 2, 10, 4, "/me<enter>"),
        Task.motion(REACH, "Search for `close`: which of its first letters appear nowhere else "
                + "on the way?",
            PIPELINE, 2, 10, 9, 9, 4, "/cl<enter>"),
        Task.motion(REACH, "From the last line, a search wraps round to the top. The target is "
                + "`alpha`",
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
            String hint = "Search for `" + word + "` with `/` and a few of its letters; `n` and "
                    + "`N` step between matches if the first isn't it";
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
                for (int c = lines[r].indexOf(pattern); c >= 0;
                     c = lines[r].indexOf(pattern, c + 1)) {
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
            "Ten tasks from random lessons, no hints."),

        new Lesson("weak", "weak spots", Lesson.Kind.WEAK_SPOTS, List.of(), null, null, null,
            "Ten tasks from your weakest lessons, no hints."));

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

    /**
     * Ten practice tasks, each from a lesson picked at random out of those given, never the same
     * one twice running.
     */
    static List<Task> randomMix(Random r, List<Lesson> from) {
        List<Task> tasks = new ArrayList<>();
        Lesson last = null;
        while (tasks.size() < MIX_TASKS) {
            Lesson lesson = from.get(r.nextInt(from.size()));
            if (lesson != last || from.size() == 1) {
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
            return randomMix(r, LESSONS);
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
                tasks.add(Task.motion(REACH, explain(path, row, col), text,
                        row, col, goal[0], goal[1], path.cost, path.keys));
                used.add(goal[0] * 1000 + goal[1]);
                row = goal[0];
                col = goal[1];
                want = path.want;
            }
            return tasks;
        };
    }

    /** A way to a position: its keys, the same keys split into moves, and where it ends. */
    private record Path(int cost, String keys, String steps, int row, int col, int want) {
    }

    /** What each move does, for hints that explain rather than give the answer away. */
    private static final java.util.Map<String, String> MOVES = java.util.Map.ofEntries(
        java.util.Map.entry("h", "`h` steps one character left"),
        java.util.Map.entry("l", "`l` steps one character right"),
        java.util.Map.entry("j", "`j` goes down a line"),
        java.util.Map.entry("k", "`k` goes up a line"),
        java.util.Map.entry("w", "`w` jumps to the start of the next word"),
        java.util.Map.entry("b", "`b` jumps back to the start of a word"),
        java.util.Map.entry("e", "`e` jumps to the end of a word"),
        java.util.Map.entry("0", "`0` goes to the very first column"),
        java.util.Map.entry("^", "`^` goes to the first character that isn't a space"),
        java.util.Map.entry("$", "`$` goes to the end of the line"),
        java.util.Map.entry("gg", "`gg` goes to the first line"),
        java.util.Map.entry("G", "`G` goes to the last line"),
        java.util.Map.entry("W", "`W` jumps to the next WORD, punctuation and all"),
        java.util.Map.entry("B", "`B` jumps back a WORD"),
        java.util.Map.entry("E", "`E` jumps to the end of a WORD"),
        java.util.Map.entry("%", "`%` jumps from a bracket to the one that matches it"),
        java.util.Map.entry(";", "`;` repeats your last `f` or `t`"),
        java.util.Map.entry(",", "`,` repeats it in the other direction"),
        java.util.Map.entry("f", "`f` and a character jump onto the next copy of it on the line"),
        java.util.Map.entry("t", "`t` and a character stop just before it"),
        java.util.Map.entry("F", "`F` is `f` going left"),
        java.util.Map.entry("T", "`T` is `t` going left"),
        java.util.Map.entry("count", "a number in front of a move repeats it that many times"),
        java.util.Map.entry("line", "a number and `G` jump straight to that line; the line "
                + "numbers are down the left"));

    /**
     * A guided hint for a movement target: where it is from the cursor, and what the moves
     * that get there do. Not which ones in what order, or how many: that's for you to work out.
     */
    private static String explain(Path path, int row, int col) {
        int down = path.row - row;
        String where = down == 0 ? "on this line, to the " + (path.col > col ? "right" : "left")
                : Math.abs(down) + (Math.abs(down) == 1 ? " line " : " lines ")
                        + (down > 0 ? "down" : "up");
        java.util.Set<String> said = new java.util.LinkedHashSet<>();
        for (String step : path.steps.split(" ")) {
            String kind = step.matches("\\d+G") ? "line" : step.matches("\\d+.+") ? "count"
                    : step.length() == 2 && "fFtT".indexOf(step.charAt(0)) >= 0
                            ? step.substring(0, 1) : step;
            if (MOVES.containsKey(kind)) {
                said.add(MOVES.get(kind));
            }
            // A counted move needs the move itself explained too.
            String move = step.replaceFirst("^\\d+", "");
            if (kind.equals("count") && MOVES.containsKey(move)) {
                said.add(MOVES.get(move));
            }
        }
        return "The highlight is " + where + ". Useful here: " + String.join("; ", said);
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
        queue.add(new Path(0, "", "", row, col, want));
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
                    queue.add(new Path(cost, at.keys + move,
                            at.steps.isEmpty() ? move : at.steps + " " + move, vim.row(),
                            vim.col(), vim.want()));
                }
            }
        }
        return best;
    }
}
