package vimdojo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A small Vim: enough of normal, insert, visual and search mode to practise the core keys.
 * Feed it one key at a time with {@link #key(char)}; it has no UI of its own.
 */
final class Vim {
    enum Mode { NORMAL, INSERT, VISUAL, VISUAL_LINE, SEARCH }

    static final char ESC = 27;
    static final char ENTER = '\n';
    static final char BACKSPACE = '\b';
    static final char CTRL_R = 18;

    private static final int INCOMPLETE = 0;
    private static final int DONE = 1;
    private static final int INVALID = 2;
    private static final int END_OF_LINE = Integer.MAX_VALUE;

    /** Where a motion lands. Linewise motions act on whole lines when used with an operator. */
    private record Target(int row, int col, boolean linewise, boolean inclusive) {
    }

    /** Text an operator acts on: from (r1, c1) up to but not including (r2, c2). */
    private record Range(int r1, int c1, int r2, int c2, boolean linewise) {
    }

    private record Snapshot(List<String> lines, int row, int col) {
    }

    private static final Target NEED_MORE = new Target(-1, -1, false, false);

    private final List<String> lines = new ArrayList<>();
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder search = new StringBuilder();
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private Mode mode = Mode.NORMAL;
    private int row;
    private int col;
    // The column j and k try to return to after passing through shorter lines.
    private int want;
    private int anchorRow;
    private int anchorCol;
    private String register;
    private boolean registerLinewise;
    private String lastSearch = "";
    private char findKind;
    private char findChar;
    // Bookkeeping for undo and for repeating with the dot command.
    private Snapshot before;
    private StringBuilder recording;
    private boolean repeatable;
    private String lastChange = "";
    private String replay;
    private boolean failed;
    // True while the cursor sits on indentation that was added automatically and not typed after.
    private boolean autoIndented;

    Vim(String text, int row, int col) {
        lines.addAll(Arrays.asList(text.split("\n", -1)));
        place(row, col, col);
    }

    List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    String text() {
        return String.join("\n", lines);
    }

    int row() {
        return row;
    }

    int col() {
        return col;
    }

    int want() {
        return want;
    }

    Mode mode() {
        return mode;
    }

    /** True if the last key completed a command that could not be carried out. */
    boolean failed() {
        return failed;
    }

    /** Keys typed so far of a command that isn't complete yet, such as the d of dw. */
    String pending() {
        return pending.toString();
    }

    String searchText() {
        return search.toString();
    }

    void place(int row, int col, int want) {
        this.row = row;
        this.col = col;
        this.want = want;
    }

    boolean selected(int r, int c) {
        if (mode == Mode.VISUAL_LINE) {
            return r >= Math.min(row, anchorRow) && r <= Math.max(row, anchorRow);
        }
        if (mode != Mode.VISUAL) {
            return false;
        }
        Range s = selection();
        return (r > s.r1 || (r == s.r1 && c >= s.c1)) && (r < s.r2 || (r == s.r2 && c < s.c2));
    }

    void key(char c) {
        failed = false;
        switch (mode) {
            case INSERT -> insertKey(c);
            case SEARCH -> searchKey(c);
            default -> commandKey(c);
        }
    }

    // ---- insert and search modes ----

    private void insertKey(char c) {
        recording.append(c);
        String line = line();
        if (c == ESC) {
            mode = Mode.NORMAL;
            if (autoIndented && line.isBlank()) {
                // Indentation nobody typed after is taken away again.
                lines.set(row, "");
                col = 0;
            }
            autoIndented = false;
            col = Math.max(0, col - 1);
            want = col;
            commit(recording.toString(), repeatable);
        } else if (c == BACKSPACE) {
            if (col > 0) {
                lines.set(row, line.substring(0, col - 1) + line.substring(col));
                col--;
            } else if (row > 0) {
                lines.remove(row);
                row--;
                col = line().length();
                lines.set(row, line() + line);
            }
        } else if (c == ENTER) {
            // The new line starts with the same indentation as this one.
            String indent = indent(line.substring(0, col));
            String head = line.substring(0, col);
            lines.set(row, autoIndented && head.isBlank() ? "" : head);
            lines.add(row + 1, indent + line.substring(col).stripLeading());
            row++;
            col = indent.length();
            autoIndented = true;
        } else if (c >= 32) {
            lines.set(row, line.substring(0, col) + c + line.substring(col));
            col++;
            autoIndented = false;
        }
    }

    private void searchKey(char c) {
        if (c == ESC || (c == BACKSPACE && search.isEmpty())) {
            mode = Mode.NORMAL;
        } else if (c == BACKSPACE) {
            search.setLength(search.length() - 1);
        } else if (c == ENTER) {
            if (!search.isEmpty()) {
                lastSearch = search.toString();
            }
            mode = Mode.NORMAL;
            Target t = search(true, 1);
            if (t != null) {
                moveTo(t, 'n');
            }
            failed = t == null;
        } else if (c >= 32) {
            search.append(c);
        }
    }

    // ---- normal and visual modes ----

    private void commandKey(char c) {
        boolean visual = mode != Mode.NORMAL;
        if (c == ESC) {
            pending.setLength(0);
            mode = Mode.NORMAL;
            clamp();
            return;
        }
        pending.append(c);
        String keys = pending.toString();
        int outcome = visual ? visual(keys) : normal(keys);
        if (outcome == INCOMPLETE) {
            return;
        }
        failed = outcome == INVALID;
        pending.setLength(0);
        if (before != null) {
            if (mode == Mode.INSERT) {
                recording = new StringBuilder(keys);
                repeatable = !visual;
            } else {
                commit(keys, !visual);
            }
        }
        if (mode == Mode.NORMAL) {
            clamp();
        }
        if (replay != null) {
            String keysToReplay = replay;
            replay = null;
            for (char k : keysToReplay.toCharArray()) {
                key(k);
            }
        }
    }

    private int normal(String p) {
        int i = 0;
        int count = 0;
        while (i < p.length() && isCountDigit(p.charAt(i), count)) {
            count = Math.min(9999, count * 10 + (p.charAt(i++) - '0'));
        }
        if (i == p.length()) {
            return INCOMPLETE;
        }
        boolean counted = count > 0;
        int n = counted ? count : 1;
        char k = p.charAt(i);
        String line = line();
        switch (k) {
            case 'd', 'c', 'y':
                return operator(k, p, i + 1, n, counted);
            case 'x':
                return operator('d', "l", 0, n, counted);
            case 'X':
                return operator('d', "h", 0, n, counted);
            case 'D':
                return operator('d', "$", 0, n, counted);
            case 'C':
                return operator('c', "$", 0, n, counted);
            case 's':
                return operator('c', "l", 0, n, counted);
            case 'S':
                return operator('c', "c", 0, n, counted);
            case 'r': {
                if (i + 1 == p.length()) {
                    return INCOMPLETE;
                }
                char ch = p.charAt(i + 1);
                if (ch < 32 || col + n > line.length()) {
                    return INVALID;
                }
                begin();
                lines.set(row, line.substring(0, col) + String.valueOf(ch).repeat(n)
                        + line.substring(col + n));
                col += n - 1;
                want = col;
                return DONE;
            }
            case 'i', 'a', 'I', 'A':
                begin();
                col = switch (k) {
                    case 'a' -> Math.min(line.length(), col + 1);
                    case 'I' -> firstNonBlank(row);
                    case 'A' -> line.length();
                    default -> col;
                };
                mode = Mode.INSERT;
                return DONE;
            case 'o', 'O': {
                begin();
                String indent = indent(line);
                row += k == 'o' ? 1 : 0;
                lines.add(row, indent);
                col = indent.length();
                autoIndented = true;
                mode = Mode.INSERT;
                return DONE;
            }
            case 'p', 'P':
                return put(n, k == 'p');
            case 'u':
                return travel(undo, redo, n);
            case CTRL_R:
                return travel(redo, undo, n);
            case '.':
                if (lastChange.isEmpty()) {
                    return INVALID;
                }
                replay = lastChange;
                return DONE;
            case 'J': {
                if (row == lines.size() - 1) {
                    return INVALID;
                }
                begin();
                for (int j = 0; j < Math.max(n, 2) - 1 && row < lines.size() - 1; j++) {
                    String left = line();
                    String right = lines.remove(row + 1).stripLeading();
                    boolean space = !left.isEmpty() && !left.endsWith(" ") && !right.isEmpty()
                            && !right.startsWith(")");
                    lines.set(row, left + (space ? " " : "") + right);
                    col = left.length();
                }
                want = col;
                return DONE;
            }
            case '~': {
                if (line.isEmpty()) {
                    return INVALID;
                }
                begin();
                int end = Math.min(line.length(), col + n);
                StringBuilder flipped = new StringBuilder(line);
                for (int j = col; j < end; j++) {
                    char ch = line.charAt(j);
                    flipped.setCharAt(j, Character.isUpperCase(ch) ? Character.toLowerCase(ch)
                            : Character.toUpperCase(ch));
                }
                lines.set(row, flipped.toString());
                col = end;
                want = col;
                return DONE;
            }
            case 'v', 'V':
                mode = k == 'v' ? Mode.VISUAL : Mode.VISUAL_LINE;
                anchorRow = row;
                anchorCol = col;
                return DONE;
            case '/':
                mode = Mode.SEARCH;
                search.setLength(0);
                return DONE;
            default: {
                Target t = motion(p, i, n, counted, (char) 0);
                if (t == NEED_MORE) {
                    return INCOMPLETE;
                }
                if (t == null) {
                    return INVALID;
                }
                moveTo(t, k);
                return DONE;
            }
        }
    }

    private int visual(String p) {
        int i = 0;
        int count = 0;
        while (i < p.length() && isCountDigit(p.charAt(i), count)) {
            count = Math.min(9999, count * 10 + (p.charAt(i++) - '0'));
        }
        if (i == p.length()) {
            return INCOMPLETE;
        }
        char k = p.charAt(i);
        switch (k) {
            case 'v', 'V': {
                Mode asked = k == 'v' ? Mode.VISUAL : Mode.VISUAL_LINE;
                mode = mode == asked ? Mode.NORMAL : asked;
                return DONE;
            }
            case 'd', 'x', 'y', 'c', 's': {
                Range selection = selection();
                mode = Mode.NORMAL;
                return apply(k == 'x' ? 'd' : k == 's' ? 'c' : k, selection);
            }
            case 'o': {
                int r = row;
                int c = col;
                place(anchorRow, anchorCol, anchorCol);
                anchorRow = r;
                anchorCol = c;
                return DONE;
            }
            case 'i', 'a': {
                if (i + 1 == p.length()) {
                    return INCOMPLETE;
                }
                Range object = textObject(k == 'a', p.charAt(i + 1));
                if (object == null || (object.r1 == object.r2 && object.c1 == object.c2)) {
                    return INVALID;
                }
                anchorRow = object.r1;
                anchorCol = object.c1;
                place(object.r2, object.c2 - 1, object.c2 - 1);
                return DONE;
            }
            default: {
                Target t = motion(p, i, Math.max(1, count), count > 0, (char) 0);
                if (t == NEED_MORE) {
                    return INCOMPLETE;
                }
                if (t == null) {
                    return INVALID;
                }
                moveTo(t, k);
                return DONE;
            }
        }
    }

    private static boolean isCountDigit(char c, int countSoFar) {
        return c >= '0' && c <= '9' && (c != '0' || countSoFar > 0);
    }

    // ---- operators ----

    private int operator(char op, String p, int i, int outerCount, boolean outerCounted) {
        int count = 0;
        while (i < p.length() && isCountDigit(p.charAt(i), count)) {
            count = Math.min(9999, count * 10 + (p.charAt(i++) - '0'));
        }
        if (i == p.length()) {
            return INCOMPLETE;
        }
        int n = outerCount * Math.max(1, count);
        boolean counted = outerCounted || count > 0;
        char k = p.charAt(i);
        Range range;
        if (k == op) {
            // dd, cc, yy: whole lines. A count can't be satisfied at all from the last line.
            range = n > 1 && row == lines.size() - 1 ? null
                    : new Range(row, 0, Math.min(lines.size() - 1, row + n - 1), 0, true);
        } else if (k == 'i' || k == 'a') {
            if (i + 1 == p.length()) {
                return INCOMPLETE;
            }
            range = textObject(k == 'a', p.charAt(i + 1));
        } else if (op == 'c' && (k == 'w' || k == 'W') && col < line().length()
                && wordClass(line().charAt(col), k == 'W') != 0) {
            range = changeWord(n, k == 'W');
        } else {
            Target t = motion(p, i, n, counted, op);
            if (t == NEED_MORE) {
                return INCOMPLETE;
            }
            range = t == null ? null : toRange(t);
            if (t == null && op == 'c' && k == 'l') {
                // s on an empty line has nothing to delete but still starts inserting.
                range = new Range(row, col, row, col, false);
            }
        }
        return range == null ? INVALID : apply(op, range);
    }

    /** cw stops at the end of the word instead of swallowing the space after it. */
    private Range changeWord(int n, boolean big) {
        int r = row;
        int c = col;
        String line = line();
        int k = wordClass(line.charAt(c), big);
        while (c + 1 < line.length() && wordClass(line.charAt(c + 1), big) == k) {
            c++;
        }
        for (int j = 1; j < n; j++) {
            int[] end = wordEnd(r, c, big);
            r = end[0];
            c = end[1];
        }
        return new Range(row, col, r, Math.min(lines.get(r).length(), c + 1), false);
    }

    private Range toRange(Target t) {
        if (t.linewise) {
            return new Range(Math.min(row, t.row), 0, Math.max(row, t.row), 0, true);
        }
        boolean forward = t.row > row || (t.row == row && t.col >= col);
        int r1 = forward ? row : t.row;
        int c1 = forward ? col : t.col;
        int r2 = forward ? t.row : row;
        int c2 = forward ? t.col : col;
        if (t.inclusive) {
            c2++;
        } else if (r2 > r1 && c2 == 0) {
            // Vim's rule for a motion that stops at the very start of a later line: it takes
            // whole lines if it began in the indent, otherwise it stops at the previous line's end.
            if (c1 <= firstNonBlank(r1)) {
                return new Range(r1, 0, r2 - 1, 0, true);
            }
            r2--;
            c2 = lines.get(r2).length();
        }
        return new Range(r1, c1, r2, Math.min(lines.get(r2).length(), c2), false);
    }

    private Range selection() {
        if (mode == Mode.VISUAL_LINE) {
            return new Range(Math.min(row, anchorRow), 0, Math.max(row, anchorRow), 0, true);
        }
        boolean forward = row > anchorRow || (row == anchorRow && col >= anchorCol);
        int r1 = forward ? anchorRow : row;
        int c1 = forward ? anchorCol : col;
        int r2 = forward ? row : anchorRow;
        int c2 = (forward ? col : anchorCol) + 1;
        if (c2 > lines.get(r2).length() && r2 < lines.size() - 1) {
            // Selecting an empty line, or past the last character, takes the line break too.
            return new Range(r1, c1, r2 + 1, 0, false);
        }
        return new Range(r1, c1, r2, Math.min(lines.get(r2).length(), c2), false);
    }

    private int apply(char op, Range range) {
        boolean empty = !range.linewise && range.r1 == range.r2 && range.c1 >= range.c2;
        if (empty && op != 'c') {
            return INVALID;
        }
        String text;
        if (range.linewise) {
            text = String.join("\n", lines.subList(range.r1, range.r2 + 1));
        } else if (range.r1 == range.r2) {
            text = lines.get(range.r1).substring(range.c1, range.c2);
        } else {
            StringBuilder b = new StringBuilder(lines.get(range.r1).substring(range.c1));
            for (int r = range.r1 + 1; r < range.r2; r++) {
                b.append('\n').append(lines.get(r));
            }
            text = b.append('\n').append(lines.get(range.r2), 0, range.c2).toString();
        }
        if (!empty) {
            register = text;
            registerLinewise = range.linewise;
        }
        if (op == 'y') {
            row = range.r1;
            if (!range.linewise) {
                col = range.c1;
                want = col;
            }
            return DONE;
        }
        begin();
        if (range.linewise) {
            String indent = indent(lines.get(range.r1));
            lines.subList(range.r1, range.r2 + 1).clear();
            if (op == 'c') {
                lines.add(range.r1, indent);
                place(range.r1, indent.length(), indent.length());
                autoIndented = true;
            } else {
                if (lines.isEmpty()) {
                    lines.add("");
                }
                row = Math.min(range.r1, lines.size() - 1);
                col = Math.min(want, Math.max(0, line().length() - 1));
                want = col;
            }
        } else {
            String joined = lines.get(range.r1).substring(0, range.c1)
                    + lines.get(range.r2).substring(range.c2);
            lines.subList(range.r1 + 1, range.r2 + 1).clear();
            lines.set(range.r1, joined);
            place(range.r1, range.c1, range.c1);
        }
        if (op == 'c') {
            mode = Mode.INSERT;
        }
        return DONE;
    }

    private int put(int n, boolean after) {
        if (register == null) {
            return INVALID;
        }
        begin();
        if (registerLinewise) {
            int at = after ? row + 1 : row;
            for (int j = 0; j < n; j++) {
                lines.addAll(at, Arrays.asList(register.split("\n", -1)));
            }
            row = at;
            col = firstNonBlank(row);
        } else {
            String line = line();
            String text = register.repeat(n);
            int at = after && !line.isEmpty() ? col + 1 : col;
            String[] parts = (line.substring(0, at) + text + line.substring(at)).split("\n", -1);
            lines.remove(row);
            lines.addAll(row, Arrays.asList(parts));
            // After a one-line put the cursor rests on the last character put.
            col = parts.length == 1 ? at + text.length() - 1 : at;
        }
        want = col;
        return DONE;
    }

    // ---- undo, redo, dot ----

    /** Call before modifying the buffer; remembers the state the change started from. */
    private void begin() {
        if (before == null) {
            before = new Snapshot(new ArrayList<>(lines), row, col);
        }
    }

    private void commit(String keys, boolean repeatableChange) {
        if (!before.lines.equals(lines)) {
            undo.push(before);
            redo.clear();
        }
        if (repeatableChange) {
            lastChange = keys;
        }
        before = null;
        recording = null;
    }

    private int travel(Deque<Snapshot> from, Deque<Snapshot> to, int n) {
        if (from.isEmpty()) {
            return INVALID;
        }
        for (int j = 0; j < n && !from.isEmpty(); j++) {
            to.push(new Snapshot(new ArrayList<>(lines), row, col));
            Snapshot s = from.pop();
            lines.clear();
            lines.addAll(s.lines);
            place(s.row, s.col, s.col);
        }
        return DONE;
    }

    // ---- motions ----

    private void moveTo(Target t, char key) {
        row = t.row;
        col = t.col;
        if (key == '$') {
            want = END_OF_LINE;
        } else if (!t.linewise) {
            want = col;
        }
    }

    private Target motion(String p, int i, int n, boolean counted, char op) {
        char k = p.charAt(i);
        String line = line();
        int last = lines.size() - 1;
        switch (k) {
            case 'h':
                return col == 0 ? null : new Target(row, Math.max(0, col - n), false, false);
            case 'l': {
                // An operator may reach one past the last character, so dl can delete it.
                int max = op != 0 || mode != Mode.NORMAL ? line.length() : line.length() - 1;
                return col >= max ? null : new Target(row, Math.min(max, col + n), false, false);
            }
            case 'j', 'k': {
                int r = k == 'j' ? Math.min(last, row + n) : Math.max(0, row - n);
                return r == row ? null : lineTarget(r);
            }
            case '0':
                return new Target(row, 0, false, false);
            case '^':
                return new Target(row, firstNonBlank(row), false, false);
            case '$': {
                int r = Math.min(last, row + n - 1);
                // In visual mode $ reaches past the last character, selecting the line break.
                int end = lines.get(r).length() - (mode == Mode.NORMAL ? 1 : 0);
                return new Target(r, Math.max(0, end), false, true);
            }
            case 'w', 'W': {
                int r = row;
                int c = col;
                for (int j = 0; j < n; j++) {
                    int[] next = wordForward(r, c, k == 'W');
                    // dw on the last word of a line stops at the line's end, it doesn't join.
                    if (op != 0 && j == n - 1 && next[0] != r && !lines.get(r).isEmpty()) {
                        next = new int[] {r, lines.get(r).length()};
                    }
                    r = next[0];
                    c = next[1];
                }
                if (op == 0) {
                    c = Math.min(c, Math.max(0, lines.get(r).length() - 1));
                    if (r == row && c == col) {
                        return null;
                    }
                }
                return new Target(r, c, false, false);
            }
            case 'b', 'B': {
                int r = row;
                int c = col;
                for (int j = 0; j < n; j++) {
                    int[] previous = wordBack(r, c, k == 'B');
                    r = previous[0];
                    c = previous[1];
                }
                return r == row && c == col ? null : new Target(r, c, false, false);
            }
            case 'e', 'E': {
                int r = row;
                int c = col;
                for (int j = 0; j < n; j++) {
                    int[] end = wordEnd(r, c, k == 'E');
                    r = end[0];
                    c = end[1];
                }
                return r == row && c == col && op == 0 ? null : new Target(r, c, false, true);
            }
            case 'g': {
                if (i + 1 == p.length()) {
                    return NEED_MORE;
                }
                if (p.charAt(i + 1) != 'g') {
                    return null;
                }
                return lineTarget(counted ? Math.min(last, n - 1) : 0);
            }
            case 'G':
                return lineTarget(counted ? Math.min(last, n - 1) : last);
            case 'f', 'F', 't', 'T': {
                if (i + 1 == p.length()) {
                    return NEED_MORE;
                }
                findKind = k;
                findChar = p.charAt(i + 1);
                return find(findKind, findChar, n, false);
            }
            case ';':
                return findKind == 0 ? null : find(findKind, findChar, n, true);
            case ',': {
                if (findKind == 0) {
                    return null;
                }
                char reversed = Character.isUpperCase(findKind) ? Character.toLowerCase(findKind)
                        : Character.toUpperCase(findKind);
                return find(reversed, findChar, n, true);
            }
            case '%': {
                int c = col;
                while (c < line.length() && "()[]{}".indexOf(line.charAt(c)) < 0) {
                    c++;
                }
                if (c == line.length()) {
                    return null;
                }
                int match = matchingBracket(offset(row, c));
                if (match < 0) {
                    return null;
                }
                int[] pos = position(match);
                return new Target(pos[0], pos[1], false, true);
            }
            case 'n', 'N':
                return search(k == 'n', n);
            default:
                return null;
        }
    }

    /**
     * A whole-line jump. Like Neovim, these keep the cursor's column where the line is long
     * enough, rather than moving to the first character as classic Vim does.
     */
    private Target lineTarget(int r) {
        return new Target(r, Math.min(want, Math.max(0, lines.get(r).length() - 1)), true, false);
    }

    private Target find(char kind, char ch, int n, boolean repeat) {
        String line = line();
        boolean forward = kind == 'f' || kind == 't';
        boolean till = kind == 't' || kind == 'T';
        int at = col;
        // Repeating t would otherwise find the character it is already sitting next to.
        if (till && repeat) {
            at += forward ? 1 : -1;
        }
        for (int j = 0; j < n; j++) {
            at = forward ? line.indexOf(ch, at + 1) : at <= 0 ? -1 : line.lastIndexOf(ch, at - 1);
            if (at < 0) {
                return null;
            }
        }
        if (till) {
            at += forward ? -1 : 1;
        }
        return new Target(row, at, false, forward);
    }

    private Target search(boolean forward, int n) {
        if (lastSearch.isEmpty()) {
            return null;
        }
        String flat = text();
        int at = offset(row, col);
        for (int j = 0; j < n; j++) {
            int found = forward ? flat.indexOf(lastSearch, at + 1)
                    : at == 0 ? -1 : flat.lastIndexOf(lastSearch, at - 1);
            if (found < 0) {
                // Wrap around the end of the buffer.
                found = forward ? flat.indexOf(lastSearch) : flat.lastIndexOf(lastSearch);
            }
            if (found < 0) {
                return null;
            }
            at = found;
        }
        int[] pos = position(at);
        return new Target(pos[0], pos[1], false, false);
    }

    /** 0 for blanks, 1 for word characters, 2 for punctuation; a WORD is anything non-blank. */
    private static int wordClass(char c, boolean big) {
        if (c == ' ' || c == '\t') {
            return 0;
        }
        return big || Character.isLetterOrDigit(c) || c == '_' ? 1 : 2;
    }

    /** Start of the next word. May return one past the end of the last line. */
    private int[] wordForward(int r, int c, boolean big) {
        String line = lines.get(r);
        if (c < line.length()) {
            int k = wordClass(line.charAt(c), big);
            while (k != 0 && c < line.length() && wordClass(line.charAt(c), big) == k) {
                c++;
            }
        }
        while (true) {
            line = lines.get(r);
            while (c < line.length() && wordClass(line.charAt(c), big) == 0) {
                c++;
            }
            if (c < line.length() || r == lines.size() - 1) {
                return new int[] {r, c};
            }
            r++;
            c = 0;
            // An empty line counts as a word.
            if (lines.get(r).isEmpty()) {
                return new int[] {r, 0};
            }
        }
    }

    private int[] wordBack(int r, int c, boolean big) {
        while (true) {
            if (c == 0) {
                if (r == 0) {
                    return new int[] {0, 0};
                }
                r--;
                c = lines.get(r).length();
                if (c == 0) {
                    return new int[] {r, 0};
                }
            }
            c--;
            if (wordClass(lines.get(r).charAt(c), big) != 0) {
                break;
            }
        }
        String line = lines.get(r);
        int k = wordClass(line.charAt(c), big);
        while (c > 0 && wordClass(line.charAt(c - 1), big) == k) {
            c--;
        }
        return new int[] {r, c};
    }

    private int[] wordEnd(int r, int c, boolean big) {
        c++;
        while (true) {
            String line = lines.get(r);
            while (c < line.length() && wordClass(line.charAt(c), big) == 0) {
                c++;
            }
            if (c < line.length()) {
                break;
            }
            if (r == lines.size() - 1) {
                return new int[] {r, Math.max(0, line.length() - 1)};
            }
            r++;
            c = 0;
        }
        String line = lines.get(r);
        int k = wordClass(line.charAt(c), big);
        while (c + 1 < line.length() && wordClass(line.charAt(c + 1), big) == k) {
            c++;
        }
        return new int[] {r, c};
    }

    // ---- text objects ----

    private Range textObject(boolean around, char kind) {
        return switch (kind) {
            case 'w', 'W' -> wordObject(around, kind == 'W');
            case '"', '\'', '`' -> quoteObject(around, kind);
            case '(', ')', 'b' -> bracketObject(around, '(', ')');
            case '[', ']' -> bracketObject(around, '[', ']');
            case '{', '}', 'B' -> bracketObject(around, '{', '}');
            case '<', '>' -> bracketObject(around, '<', '>');
            default -> null;
        };
    }

    private Range wordObject(boolean around, boolean big) {
        String line = line();
        if (line.isEmpty()) {
            return new Range(row, 0, row, 0, false);
        }
        int k = wordClass(line.charAt(col), big);
        int start = col;
        int end = col;
        while (start > 0 && wordClass(line.charAt(start - 1), big) == k) {
            start--;
        }
        while (end + 1 < line.length() && wordClass(line.charAt(end + 1), big) == k) {
            end++;
        }
        if (around && k != 0) {
            // "A word" takes the blanks after it, or the blanks before it when there are none.
            int trailing = end;
            while (trailing + 1 < line.length() && wordClass(line.charAt(trailing + 1), big) == 0) {
                trailing++;
            }
            if (trailing > end) {
                end = trailing;
            } else {
                while (start > 0 && wordClass(line.charAt(start - 1), big) == 0) {
                    start--;
                }
            }
        } else if (around && end + 1 < line.length()) {
            int next = wordClass(line.charAt(end + 1), big);
            end++;
            while (end + 1 < line.length() && wordClass(line.charAt(end + 1), big) == next) {
                end++;
            }
        }
        return new Range(row, start, row, end + 1, false);
    }

    private Range quoteObject(boolean around, char quote) {
        String line = line();
        int open = -1;
        int close = -1;
        if (col < line.length() && line.charAt(col) == quote) {
            // On a quote: pair the quotes up from the start of the line to see which one this is.
            int count = 0;
            for (int j = 0; j < col; j++) {
                if (line.charAt(j) == quote) {
                    count++;
                }
            }
            open = count % 2 == 0 ? col : line.lastIndexOf(quote, col - 1);
            close = count % 2 == 0 ? line.indexOf(quote, col + 1) : col;
        } else {
            open = col == 0 ? -1 : line.lastIndexOf(quote, col - 1);
            if (open < 0) {
                // Not inside quotes: use the first quoted string further along the line.
                open = line.indexOf(quote, col);
            }
            close = open < 0 ? -1 : line.indexOf(quote, Math.max(open, col) + 1);
            if (open >= 0 && open < col && close < 0) {
                open = -1;
            }
        }
        if (open < 0 || close < 0) {
            return null;
        }
        if (!around) {
            return new Range(row, open + 1, row, close, false);
        }
        int end = close;
        while (end + 1 < line.length() && line.charAt(end + 1) == ' ') {
            end++;
        }
        if (end == close) {
            while (open > 0 && line.charAt(open - 1) == ' ') {
                open--;
            }
        }
        return new Range(row, open, row, end + 1, false);
    }

    private Range bracketObject(boolean around, char open, char close) {
        String flat = text();
        if (flat.isEmpty()) {
            return null;
        }
        int at = Math.min(offset(row, col), flat.length() - 1);
        int start = -1;
        int depth = 0;
        for (int j = at; j >= 0; j--) {
            char ch = flat.charAt(j);
            if (ch == close && j != at) {
                depth++;
            } else if (ch == open) {
                if (depth == 0) {
                    start = j;
                    break;
                }
                depth--;
            }
        }
        if (start < 0) {
            // Not inside a pair: use the next one further on, as Neovim does.
            start = flat.indexOf(open, at);
        }
        int end = start < 0 ? -1 : matchingBracket(start);
        if (end < 0) {
            return null;
        }
        int[] a = position(around ? start : start + 1);
        int[] b = position(around ? end + 1 : end);
        return new Range(a[0], a[1], b[0], b[1], false);
    }

    /** Offset of the bracket matching the one at the given offset, or -1. */
    private int matchingBracket(int at) {
        String flat = text();
        String pairs = "()[]{}<>";
        int kind = pairs.indexOf(flat.charAt(at));
        boolean opening = kind % 2 == 0;
        char self = pairs.charAt(kind);
        char other = pairs.charAt(opening ? kind + 1 : kind - 1);
        int depth = 0;
        for (int j = at + (opening ? 1 : -1); j >= 0 && j < flat.length(); j += opening ? 1 : -1) {
            char ch = flat.charAt(j);
            if (ch == self) {
                depth++;
            } else if (ch == other) {
                if (depth == 0) {
                    return j;
                }
                depth--;
            }
        }
        return -1;
    }

    // ---- helpers ----

    private String line() {
        return lines.get(row);
    }

    private static String indent(String line) {
        return line.substring(0, line.length() - line.stripLeading().length());
    }

    private int firstNonBlank(int r) {
        String line = lines.get(r);
        int c = 0;
        while (c < line.length() - 1 && wordClass(line.charAt(c), true) == 0) {
            c++;
        }
        return c;
    }

    private void clamp() {
        row = Math.max(0, Math.min(row, lines.size() - 1));
        col = Math.max(0, Math.min(col, line().length() - 1));
    }

    private int offset(int r, int c) {
        int offset = c;
        for (int j = 0; j < r; j++) {
            offset += lines.get(j).length() + 1;
        }
        return offset;
    }

    private int[] position(int offset) {
        int r = 0;
        while (offset > lines.get(r).length()) {
            offset -= lines.get(r).length() + 1;
            r++;
        }
        return new int[] {r, offset};
    }
}
