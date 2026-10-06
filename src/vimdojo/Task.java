package vimdojo;

/**
 * One step of a lesson. A motion task is finished when the cursor reaches the goal position; an
 * edit task (one with goal text) when the buffer matches it. Par is the keystrokes a fluent
 * solution needs.
 */
record Task(String prompt, String start, int row, int col, String goal, int goalRow, int goalCol,
            int par, String solution) {

    static Task motion(String prompt, String text, int row, int col, int goalRow, int goalCol,
                       int par, String solution) {
        return new Task(prompt, text, row, col, null, goalRow, goalCol, par, solution);
    }

    /** The start text carries a {@code |} just before the character the cursor begins on. */
    static Task edit(String prompt, String startWithCursor, String goal, String solution) {
        int at = startWithCursor.indexOf('|');
        String before = startWithCursor.substring(0, at);
        int row = (int) before.chars().filter(c -> c == '\n').count();
        int col = at - (before.lastIndexOf('\n') + 1);
        String start = before + startWithCursor.substring(at + 1);
        return new Task(prompt, start, row, col, goal, -1, -1, Keys.parse(solution).length(),
                solution);
    }

    boolean isMotion() {
        return goal == null;
    }

    boolean reached(Vim vim) {
        if (vim.mode() != Vim.Mode.NORMAL || !vim.pending().isEmpty()) {
            return false;
        }
        return isMotion() ? vim.row() == goalRow && vim.col() == goalCol : vim.text().equals(goal);
    }
}
