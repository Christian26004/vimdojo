package vimdojo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs key sequences through both the built-in Vim and a real Neovim, and reports any difference
 * in the resulting text or cursor. Needs nvim on the PATH; run with ./test.sh compare
 */
public final class VimCompare {
    private record Case(String text, int row, int col, String keys) {
    }

    private static final String SAMPLE = "the quick brown fox\n  jumps over (the lazy) dog\n\n"
            + "say(\"hello there\", name) + \"x\"\nlast line here; done.";

    private static final String[] KEYS = {
        "w", "3w", "9w", "b", "3b", "e", "3e", "W", "2W", "B", "E", "0", "^", "$", "j", "k", "3j",
        "2k", "gg", "G", "2G", "4G", "fo", "2fo", "to", "Fo", "To", "fo;", "fo;;", "fo;,", "to;",
        "To;", "Fo,", "%", "h", "3h", "l", "30l", "$j", "$jj", "$k", "jjj", "4lj", "0jk",
        "dw", "d2w", "2dw", "d9w", "de", "d2e", "db", "d2b", "d$", "D", "d0", "d^", "dd", "2dd",
        "9dd", "dj", "dk", "dG", "dgg", "dl", "dh", "x", "3x", "40x", "X", "3X", "rZ", "3rZ",
        "cwX<esc>", "c2wX<esc>", "ceX<esc>", "cbX<esc>", "ccX<esc>", "CX<esc>", "sX<esc>",
        "SX<esc>", "c$X<esc>", "iX<esc>", "aX<esc>", "IX<esc>", "AX<esc>", "oX<esc>", "OX<esc>",
        "yyp", "yyP", "ywP", "ywp", "yep", "y$p", "y0P", "ddp", "ddP", "xp", "yy3p", "2yyjp",
        "yjP", "dwwP", "J", "3J", "~", "3~", "diw", "daw", "ciwX<esc>", "cawX<esc>", "yiwP",
        "di(", "da(", "ci(X<esc>", "di\"", "da\"", "ci\"X<esc>", "dib", "di[", "d%", "dfo",
        "dto", "dFo", "dTo", "dfo;", "ct)X<esc>", "vd", "vld", "vjd", "vkd", "Vd", "Vjd", "Vkd",
        "ved", "vbd", "viwd", "vawd", "vi(d", "va\"d", "v$d", "vjyP", "vey$p", "VyjP", "vecX<esc>",
        "VcX<esc>", "vwod", "/the<enter>", "/the<enter>n", "/the<enter>nn", "/the<enter>N",
        "/lazy<enter>", "/e<enter>nnn", "/o<enter>N", "iab<bs>c<esc>", "A<bs><bs>!<esc>",
        "i<enter>x<esc>", "I<bs>y<esc>", "dw.", "x..", "A!<esc>j.", "cwX<esc>w.", "dd.",
        "rZl.", "iab<esc>j.", "oX<esc>.", "ywPw.", "3x.", "d2w.", "fodt ", "wdw", "wwcwZ<esc>b.",
        "eas<esc>", "$bdw", "ggdG", "Gdgg", "jdd", "jjdd", "GddP", "dawdaw", "2Gdd", "wD", "$x",
        "$a!<esc>", "0i <esc>^", "A  <esc>^", "jI-<esc>", "jjiX<esc>", "jjdd", "jjx", "jjA.<esc>",
        "jjp", "yyjjp", "jjyyp", "ywjjP", "jjdw", "jjcwX<esc>", "jjw", "jjb", "jje", "wwwwww",
        "bbbbb", "eeeeeeee", "GG", "ggw", "Gb", "G$b", "G$e", "ggb", "G$w", "tx", "fq", "Fq",
    };

    /**
     * Sequences where the two are known to disagree in some starting positions, all outside what
     * the lessons use: text objects and dw on an empty line, backspacing over indentation, J on
     * the last line, and visual-mode motions that fail at the very end of the buffer.
     */
    private static final java.util.Set<String> KNOWN = java.util.Set.of("I<bs>y<esc>", "jjdw",
            "VyjP", "vwod", "viwd", "vey$p", "ved", "vecX<esc>", "vawd", "dawdaw", "daw",
            "cawX<esc>", "3J");

    public static void main(String[] args) throws Exception {
        List<Case> cases = new ArrayList<>();
        int[][] starts = {{0, 0}, {0, 6}, {0, 18}, {1, 0}, {1, 9}, {1, 14}, {2, 0}, {3, 7}, {3, 24},
                {4, 0}, {4, 20}};
        for (String keys : KEYS) {
            for (int[] start : starts) {
                cases.add(new Case(SAMPLE, start[0], start[1], keys));
            }
        }
        // Several runs of every lesson, since the tasks are different each time.
        for (Lesson lesson : Lessons.ALL) {
            for (int seed = 1; seed <= 12; seed++) {
                for (Task task : lesson.tasks().apply(new Random(seed))) {
                    cases.add(new Case(task.start(), task.row(), task.col(), task.solution()));
                }
            }
        }
        Path dir = Files.createTempDirectory("vimdojo-compare");
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger known = new AtomicInteger();
        java.util.Set<String> seen = new java.util.HashSet<>();
        AtomicInteger id = new AtomicInteger();
        cases.parallelStream().forEach(c -> {
            try {
                String mine = mine(c);
                String real = real(c, dir, id.incrementAndGet());
                // Undo leaves the cursor in a different place; only compare the text for it.
                if (c.keys.contains("u") && c.keys.length() > 1) {
                    mine = mine.substring(mine.indexOf('\n'));
                    real = real.substring(real.indexOf('\n'));
                }
                if (!mine.equals(real) && KNOWN.contains(c.keys)) {
                    known.incrementAndGet();
                } else if (!mine.equals(real)) {
                    failures.incrementAndGet();
                    synchronized (seen) {
                        boolean first = seen.add(c.keys);
                        System.out.println("MISMATCH " + c.keys + " at " + c.row + "," + c.col
                                + (c.text.equals(SAMPLE) ? "" : " (lesson task)"));
                        if (first || !c.text.equals(SAMPLE)) {
                            System.out.println(diff(c, mine, real));
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(c.keys, e);
            }
        });
        System.out.println(cases.size() + " cases compared with nvim: " + failures.get()
                + " unexpected mismatches, " + known.get() + " known edge-case differences");
        if (failures.get() > 0) {
            System.exit(1);
        }
    }

    /** Only the cursor line and the lines that differ. */
    private static String diff(Case c, String mine, String real) {
        String[] a = mine.split("\n", -1);
        String[] b = real.split("\n", -1);
        StringBuilder out = new StringBuilder("  cursor mine " + a[0] + " nvim " + b[0]);
        if (a.length != b.length) {
            out.append("  lines mine ").append(a.length - 1).append(" nvim ").append(b.length - 1);
        }
        for (int i = 1; i < Math.min(a.length, b.length); i++) {
            if (!a[i].equals(b[i])) {
                out.append("\n    mine [").append(a[i]).append("]\n    nvim [").append(b[i])
                        .append("]");
                break;
            }
        }
        return out.toString();
    }

    private static String mine(Case c) {
        Vim vim = new Vim(c.text, c.row, c.col);
        for (char k : Keys.parse(c.keys).toCharArray()) {
            vim.key(k);
            // :normal abandons the rest of the keys when a command fails.
            if (vim.failed()) {
                break;
            }
        }
        // :normal leaves insert mode when the keys run out.
        if (vim.mode() != Vim.Mode.NORMAL) {
            vim.key(Vim.ESC);
        }
        return (vim.row() + 1) + " " + (vim.col() + 1) + "\n" + vim.text();
    }

    private static String real(Case c, Path dir, int id) throws Exception {
        Path buffer = dir.resolve("buffer" + id + ".txt");
        Path script = dir.resolve("script" + id + ".vim");
        Path out = dir.resolve("out" + id + ".txt");
        Files.writeString(buffer, c.text + "\n");
        String keys = c.keys.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("<esc>", "\\<Esc>").replace("<enter>", "\\<CR>")
                .replace("<bs>", "\\<BS>").replace("<c-r>", "\\<C-r>");
        Files.writeString(script, "call cursor(" + (c.row + 1) + ", " + (c.col + 1) + ")\n"
                + "exe \"normal! " + keys + "\"\n"
                + "call writefile([line('.') . ' ' . col('.')] + getline(1, '$'), '" + out + "')\n"
                + "qa!\n");
        Process process = new ProcessBuilder("nvim", "--headless", "-u", "NONE", "-i", "NONE",
                "-n", buffer.toString(), "-S", script.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        process.waitFor();
        return String.join("\n", Files.readAllLines(out));
    }
}
