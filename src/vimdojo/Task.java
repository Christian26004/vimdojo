package vimdojo;

/**
 * One step of a lesson. A motion task is finished when the cursor reaches the goal position; an
 * edit task (one with goal text) when the buffer matches it. Par is the keystrokes a fluent
 * solution needs.
 *
 * <p>Every task can be shown two ways. The prompt says only what to do ("delete the extra
 * word"); the hint also says how, with which keys ("delete the extra word with `dw`"). A first,
 * guided run of a lesson shows the hints; practice shows the prompts and leaves the keys to you.
 * The lesson is the id of the lesson whose keys the task drills, so a mixed run can show the
 * right ones.
 */
record Task(String lesson, String prompt, String hint, String start, int row, int col,
            String goal, int goalRow, int goalCol, int par, String solution) {

    static Task motion(String prompt, String hint, String text, int row, int col, int goalRow,
                       int goalCol, int par, String solution) {
        return new Task("", prompt, hint, text, row, col, null, goalRow, goalCol, par, solution);
    }

    /** The start text carries a {@code |} just before the character the cursor begins on. */
    static Task edit(String prompt, String hint, String startWithCursor, String goal,
                     String solution) {
        int at = startWithCursor.indexOf('|');
        String before = startWithCursor.substring(0, at);
        int row = (int) before.chars().filter(c -> c == '\n').count();
        int col = at - (before.lastIndexOf('\n') + 1);
        String start = before + startWithCursor.substring(at + 1);
        return new Task("", prompt, hint, start, row, col, goal, -1, -1,
                Keys.parse(solution).length(), solution);
    }

    /** The same task, filed under a lesson. */
    Task in(String lessonId) {
        return new Task(lessonId, prompt, hint, start, row, col, goal, goalRow, goalCol, par,
                solution);
    }

    /** The same task, showing its hint as the prompt. */
    Task guided() {
        return new Task(lesson, hint, hint, start, row, col, goal, goalRow, goalCol, par,
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
