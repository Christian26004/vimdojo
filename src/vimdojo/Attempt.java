package vimdojo;

import java.util.Locale;

/**
 * A finished run of a lesson. One of these is one line of the history file. A guided run, where
 * every task said which keys to use, is kept apart: it doesn't count as a best or toward
 * unlocking the next lesson.
 */
record Attempt(long timestamp, String lesson, double seconds, int keys, int par, int tasks,
               boolean guided) {

    /** A practice run, or a mix. */
    Attempt(long timestamp, String lesson, double seconds, int keys, int par, int tasks) {
        this(timestamp, lesson, seconds, keys, par, tasks, false);
    }

    /**
     * Par as a percentage of the keys used: 100 means exactly par, less means wasted keys, and
     * more than 100 means the run beat par.
     */
    double efficiency() {
        return keys == 0 ? 0 : 100.0 * par / keys;
    }

    String toLine() {
        return String.format(Locale.ROOT, "%d\t%s\t%.2f\t%d\t%d\t%d\t%s", timestamp, lesson,
                seconds, keys, par, tasks, guided ? "guided" : "practice");
    }

    /** Reads a line; lines from before guided runs existed have six fields and are practice. */
    static Attempt fromLine(String line) {
        String[] f = line.split("\t");
        if (f.length != 6 && f.length != 7) {
            throw new IllegalArgumentException("expected 6 or 7 fields, got " + f.length);
        }
        return new Attempt(Long.parseLong(f[0]), f[1], Double.parseDouble(f[2]),
                Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                f.length == 7 && f[6].equals("guided"));
    }
}
