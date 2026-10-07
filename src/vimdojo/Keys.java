package vimdojo;

/** Converts readable key notation such as {@code cwfox<esc>} into the characters Vim receives. */
final class Keys {
    private static final String[][] NAMED = {
        {"<esc>", String.valueOf(Vim.ESC)},
        {"<enter>", String.valueOf(Vim.ENTER)},
        {"<bs>", String.valueOf(Vim.BACKSPACE)},
        {"<c-r>", String.valueOf(Vim.CTRL_R)},
    };

    private Keys() {
    }

    /** The reverse of {@link #parse}: raw keys back into readable notation. */
    static String notation(String keys) {
        String notation = keys;
        for (String[] named : NAMED) {
            notation = notation.replace(named[1], named[0]);
        }
        return notation;
    }

    static String parse(String notation) {
        String keys = notation;
        for (String[] named : NAMED) {
            keys = keys.replace(named[0], named[1]);
        }
        return keys;
    }
}
