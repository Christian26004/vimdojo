package vimdojo;

/**
 * The table behind the Dvorak typing option, which works like Vim's {@code :set keymap=dvorak}:
 * on a keyboard the system treats as QWERTY, text you type comes out as the character in the
 * same position on a Dvorak keyboard.
 */
final class Layout {
    static final String QWERTY = "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./"
            + "~!@#$%^&*()_+QWERTYUIOP{}|ASDFGHJKL:\"ZXCVBNM<>?";
    static final String DVORAK = "`1234567890[]',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwvz"
            + "~!@#$%^&*(){}\"<>PYFGCRL?+|AOEUIDHTNS_:QJKXBMWVZ";

    private Layout() {
    }

    /** The Dvorak character for the key that types {@code c} on QWERTY. */
    static char dvorak(char c) {
        int at = QWERTY.indexOf(c);
        return at < 0 ? c : DVORAK.charAt(at);
    }
}
