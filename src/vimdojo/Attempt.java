package vimdojo;

import java.util.Locale;

/** A finished run of a lesson. One of these is one line of the history file. */
record Attempt(long timestamp, String lesson, double seconds, int keys, int par, int tasks) {

    /**
     * Par as a percentage of the keys used: 100 means exactly par, less means wasted keys, and
     * more than 100 means the run beat par.
     */
    double efficiency() {
        return keys == 0 ? 0 : 100.0 * par / keys;
    }

    String toLine() {
        return String.format(Locale.ROOT, "%d\t%s\t%.2f\t%d\t%d\t%d", timestamp, lesson, seconds,
                keys, par, tasks);
    }

    static Attempt fromLine(String line) {
        String[] f = line.split("\t");
        if (f.length != 6) {
            throw new IllegalArgumentException("expected 6 fields, got " + f.length);
        }
        return new Attempt(Long.parseLong(f[0]), f[1], Double.parseDouble(f[2]),
                Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]));
    }
}
