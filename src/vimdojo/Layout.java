package vimdojo;

/**
 * The table behind the Dvorak option: on a keyboard the system treats as QWERTY, every key
 * typed is read as the character in the same position on a Dvorak keyboard, commands included,
 * as if the system were set to Dvorak.
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

    /** The other way: the QWERTY key a Dvorak character sits on. */
    static char qwerty(char c) {
        int at = DVORAK.indexOf(c);
        return at < 0 ? c : QWERTY.charAt(at);
    }
}
