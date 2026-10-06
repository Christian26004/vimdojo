package vimdojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * The curriculum. Movement lessons generate fresh targets every run and work out par by searching
 * for the shortest key sequence; editing lessons are written by hand, each with a known solution.
 */
final class Lessons {
    private static final int DRILL_TASKS = 8;

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

    static final List<Lesson> ALL = List.of(
        new Lesson("hjkl", "moving around", List.of(
                key("h", "left"), key("j", "down"), key("k", "up"), key("l", "right")),
            drill("move to the highlighted character", List.of(FOX, TOOLS, RENDER),
                text -> BASIC, null, 2, 7)),

        new Lesson("words", "word by word", List.of(
                key("w", "start of the next word"), key("b", "back to the start of a word"),
                key("e", "end of the word")),
            drill("jump there by words", List.of(FOX, TOOLS, RENDER),
                text -> WORD, text -> BASIC, 1, 4)),

        new Lesson("line", "ends of the line", List.of(
                key("0", "start of the line"), key("^", "first character of the line"),
                key("$", "end of the line")),
            drill("get there using the ends of the line", List.of(FOX, TOOLS, RENDER),
                text -> LINE, text -> WORD, 1, 3)),

        new Lesson("jumps", "counts and big jumps", List.of(
                key("gg", "first line"), key("G", "last line"),
                key("7G", "line 7 - any number works"),
                key("3j", "a number repeats a motion: 3j, 4w, 2b")),
            drill("get there in as few keys as you can", List.of(PIPELINE),
                Lessons::jumpMoves, text -> LINE, 1, 4)),

        new Lesson("find", "find a character", List.of(
                key("fx", "onto the next x in the line"), key("tx", "just before the next x"),
                key("F T", "the same, backwards"), key(";", "repeat the find"),
                key(",", "repeat it the other way")),
            drill("find your way to the highlight", List.of(FOX, TOOLS, RENDER),
                Lessons::findMoves, text -> LINE, 2, 3)),

        new Lesson("chars", "fixing characters", List.of(
                key("x", "delete the character under the cursor"),
                key("rx", "replace it with x"), key("u", "undo - works everywhere")),
            fixed(
                Task.edit("delete the extra `u` with `x`",
                    "the q|uuick brown fox", "the quick brown fox", "x"),
                Task.edit("replace the wrong letter: `r` then the right one",
                    "the quick br|awn fox", "the quick brown fox", "ro"),
                Task.edit("find the doubled letter and delete one",
                    "|jumps ovver the lazy dog", "jumps over the lazy dog", "fvx"),
                Task.edit("fix the word `bax`",
                    "|fill my bax with jugs", "fill my box with jugs", "faro"),
                Task.edit("a count repeats `x`: remove all three with `3x`",
                    "remove |xxxthe noise", "remove the noise", "3x"),
                Task.edit("two typos: fix both",
                    "|the lazi dog sleepss", "the lazy dog sleeps", "firy$x"))),

        new Lesson("insert", "inserting text", List.of(
                key("i", "insert before the cursor"), key("a", "append after the cursor"),
                key("esc", "back to normal mode")),
            fixed(
                Task.edit("press `i`, type the missing `o`, then `esc`",
                    "the quick br|wn fox", "the quick brown fox", "io<esc>"),
                Task.edit("`a` appends after the cursor: add the `k`",
                    "the qui|c brown fox", "the quick brown fox", "ak<esc>"),
                Task.edit("add the missing word `quick`",
                    "|the brown fox", "the quick brown fox", "wiquick <esc>"),
                Task.edit("add a comma after `hello`",
                    "|hello world", "hello, world", "ea,<esc>"),
                Task.edit("put a `*` between price and count",
                    "|let total = price count;", "let total = price * count;", "4wi* <esc>"),
                Task.edit("two insertions: make it `x += 10`",
                    "|x = 1", "x += 10", "wi+<esc>$a0<esc>"))),

        new Lesson("open", "inserting at the edges", List.of(
                key("I", "insert at the start of the line"),
                key("A", "append at the end of the line"),
                key("o", "open a new line below"), key("O", "open a new line above")),
            fixed(
                Task.edit("`A` appends at the end of the line: add the `;`",
                    "|return total", "return total;", "A;<esc>"),
                Task.edit("`I` inserts at the start: add `let `",
                    "total |= 0;", "let total = 0;", "Ilet <esc>"),
                Task.edit("`o` opens a line below: add `three`",
                    "one\n|two\nfour", "one\ntwo\nthree\nfour", "othree<esc>"),
                Task.edit("`O` opens a line above: add `one`",
                    "|two\nthree", "one\ntwo\nthree", "Oone<esc>"),
                Task.edit("add a line above and a line below",
                    "|second", "first\nsecond\nthird", "Ofirst<esc>jothird<esc>"),
                Task.edit("add to both ends of the line",
                    "name |= input", "const name = input();", "Iconst <esc>A();<esc>"))),

        new Lesson("delete", "the delete operator", List.of(
                key("dw", "delete a word"), key("dd", "delete the line"),
                key("D", "delete to the end of the line"),
                key("d + motion", "delete wherever that motion goes: d0, dt), dG")),
            fixed(
                Task.edit("delete the extra word with `dw`",
                    "the |very quick fox", "the quick fox", "dw"),
                Task.edit("`dd` deletes the whole line",
                    "keep this\n|remove this\nkeep this too", "keep this\nkeep this too", "dd"),
                Task.edit("`D` deletes from the cursor to the end of the line",
                    "total = 0;| // temporary", "total = 0;", "D"),
                Task.edit("`d` takes any motion: `d0` deletes back to the line start",
                    "debug: |save(file)", "save(file)", "d0"),
                Task.edit("`dt)` deletes up to the bracket",
                    "sum(a, b|, c, d)", "sum(a, b)", "dt)"),
                Task.edit("`dG` deletes from this line to the end of the file",
                    "header\n|junk one\njunk two", "header", "dG"))),

        new Lesson("change", "the change operator", List.of(
                key("cw", "change a word: delete it and start typing"),
                key("cc", "change the whole line"),
                key("C", "change to the end of the line"),
                key("c + motion", "works like d, then leaves you in insert mode")),
            fixed(
                Task.edit("`cw` replaces a word: make it `quick`",
                    "the |slow brown fox", "the quick brown fox", "cwquick<esc>"),
                Task.edit("`C` changes the rest of the line",
                    "return |a + b;", "return total;", "Ctotal;<esc>"),
                Task.edit("`cc` rewrites the whole line",
                    "one\n|tow\nthree", "one\ntwo\nthree", "cctwo<esc>"),
                Task.edit("`ct;` changes up to the semicolon",
                    "color: |red; /* keep */", "color: blue; /* keep */", "ct;blue<esc>"),
                Task.edit("change the number to `250`",
                    "|const limit = 10;", "const limit = 250;", "$bcw250<esc>"),
                Task.edit("two words are wrong: change both",
                    "|the quick red fox jumps under", "the quick brown fox jumps over",
                    "2wcwbrown<esc>$bcwover<esc>"))),

        new Lesson("put", "copy and paste", List.of(
                key("yy", "yank (copy) the line"), key("yw", "yank a word"),
                key("p", "put after the cursor"), key("P", "put before the cursor"),
                key("dd p", "deleted text can be put back too")),
            fixed(
                Task.edit("duplicate the line: `yy` then `p`",
                    "|echo hello", "echo hello\necho hello", "yyp"),
                Task.edit("move the line down: `dd` then `p`",
                    "|second\nfirst\nthird", "first\nsecond\nthird", "ddp"),
                Task.edit("move `one` to the top: `P` puts above",
                    "two\n|one\nthree", "one\ntwo\nthree", "ddkP"),
                Task.edit("swap two letters with `xp`",
                    "the |uqick fox", "the quick fox", "xp"),
                Task.edit("copy a word: `yw`, then put it",
                    "|very good", "very very good", "ywP"),
                Task.edit("make three copies of the first line",
                    "|row\nend", "row\nrow\nrow\nend", "yypp"))),

        new Lesson("counts", "counts with operators", List.of(
                key("d2w", "delete two words"), key("3dd", "delete three lines"),
                key("c2w", "change two words"),
                key("operator + count + motion", "they all combine")),
            fixed(
                Task.edit("`d2w` deletes two words at once",
                    "the |very very quick fox", "the quick fox", "d2w"),
                Task.edit("`3dd` deletes three lines",
                    "keep\n|drop 1\ndrop 2\ndrop 3\nkeep too", "keep\nkeep too", "3dd"),
                Task.edit("`c2w` changes two words: make it `brown`",
                    "the |dark red fox", "the brown fox", "c2wbrown<esc>"),
                Task.edit("move two lines to the bottom: `2dd`, then put",
                    "|c\nd\na\nb", "a\nb\nc\nd", "2ddjp"),
                Task.edit("copy the first two lines to the end: `2yy`",
                    "|a = 1\nb = 2\n---", "a = 1\nb = 2\n---\na = 1\nb = 2", "2yyGp"),
                Task.edit("`d2b` deletes two words backwards",
                    "the quick brown fox |jumps", "the quick jumps", "d2b"))),

        new Lesson("objects", "text objects", List.of(
                key("iw", "inner word - the word the cursor is in"),
                key("aw", "a word, with its space"),
                key("i\"", "inside the quotes"), key("i(", "inside the brackets"),
                key("d c y", "use them after an operator: ciw, di(, ya\"")),
            fixed(
                Task.edit("`daw` deletes a word from anywhere inside it",
                    "the very qu|ick fox", "the very fox", "daw"),
                Task.edit("`ciw` changes the word under the cursor",
                    "the bro|ken fox", "the brown fox", "ciwbrown<esc>"),
                Task.edit("`ci\"` changes what is inside the quotes",
                    "say(\"hel|lo there\")", "say(\"goodbye\")", "ci\"goodbye<esc>"),
                Task.edit("`di(` empties the brackets",
                    "call(alpha, |beta, gamma)", "call()", "di("),
                Task.edit("`ci(` replaces the condition",
                    "if (x |> 10) {", "if (ready) {", "ci(ready<esc>"),
                Task.edit("`ci\"` even reaches the next quotes on the line",
                    "|name = \"old value\";", "name = \"new\";", "ci\"new<esc>"))),

        new Lesson("repeat", "repeat and undo", List.of(
                key(".", "repeat the last change"), key("u", "undo"),
                key("ctrl-r", "redo")),
            fixed(
                Task.edit("delete a word, then repeat with `.`",
                    "|no no no yes", "yes", "dw.."),
                Task.edit("append `;` to one line, then `j.` for the rest",
                    "|a = 1\nb = 2\nc = 3", "a = 1;\nb = 2;\nc = 3;", "A;<esc>j.j."),
                Task.edit("delete every `drop` line: `dd`, move, `.`",
                    "keep\n|drop\nkeep\ndrop\nkeep", "keep\nkeep\nkeep", "ddj."),
                Task.edit("make it a list: `I- `, then repeat on each line",
                    "|milk\neggs\nbread", "- milk\n- eggs\n- bread", "I- <esc>j.j."),
                Task.edit("turn each `-` into a space: `;` repeats the find, `.` the change",
                    "|a-b-c-d", "a b c d", "f-r ;.;."),
                Task.edit("rename every `foo` to `baz`",
                    "|foo(foo, bar, foo)", "baz(baz, bar, baz)", "cwbaz<esc>ww.$b."))),

        new Lesson("visual", "visual mode", List.of(
                key("v", "select characters"), key("V", "select whole lines"),
                key("motions", "grow the selection"),
                key("d y c", "act on it"), key("esc", "cancel")),
            fixed(
                Task.edit("select both lines with `V` and `j`, then `d`",
                    "keep\n|drop\ndrop too\nkeep", "keep\nkeep", "Vjd"),
                Task.edit("select up to the bracket with `vt)`, then `c`",
                    "max(|a + b * c)", "max(total)", "vt)ctotal<esc>"),
                Task.edit("`vaw` selects a word; delete it",
                    "the qu|ick fox", "the fox", "vawd"),
                Task.edit("copy two lines to the end: `Vjy`, then put",
                    "|one\ntwo\n---", "one\ntwo\n---\none\ntwo", "VjyGp"),
                Task.edit("replace both lines: select them, then `c`",
                    "start\n|old line 1\nold line 2\nend", "start\nnew\nend", "Vjcnew<esc>"),
                Task.edit("`VG` selects to the end of the file",
                    "title\n|a\nb\nc", "title", "VGd"))),

        new Lesson("search", "searching", List.of(
                key("/text", "then enter: jump to the next match"),
                key("n", "next match"), key("N", "previous match")),
            fixed(
                Task.motion("search for `sort`: type `/sort` then enter",
                    PIPELINE, 0, 0, 4, 7, 6, "/sort<enter>"),
                Task.motion("search for `notify` - a few letters are enough",
                    PIPELINE, 4, 7, 8, 8, 4, "/no<enter>"),
                Task.motion("reach the second `load`: search, then `n`",
                    PIPELINE, 8, 8, 1, 8, 7, "/load<enter>n"),
                Task.motion("`N` goes back to the previous match",
                    PIPELINE, 1, 8, 0, 8, 1, "N"),
                Task.motion("jump to `golf` inside the brackets on line 8",
                    PIPELINE, 0, 8, 7, 25, 7, "/golf<enter>n"),
                Task.motion("finish on `merge`",
                    PIPELINE, 7, 25, 2, 10, 4, "/me<enter>"))));

    private Lessons() {
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
