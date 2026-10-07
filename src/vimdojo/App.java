package vimdojo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

/** Wires the screens together and owns the current lesson run. */
public final class App {
    private final Settings settings = Settings.load();
    private final History history = History.load();
    private final Random random = new Random();
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final ChallengeView challengeView = new ChallengeView(this);
    private final ResultView resultView = new ResultView(this);
    private final LessonsView lessonsView = new LessonsView(this);
    private final StatsView statsView = new StatsView(this);
    private final SettingsView settingsView = new SettingsView(this);
    private final DocsView docsView = new DocsView(this);
    private final JComponent docsOverlay = docsView.overlay();
    private final ReplayView replayView = new ReplayView(this);
    private final JComponent replayOverlay = replayView.overlay();
    // The screen underneath the docs, which gets the keyboard back when they close.
    private JComponent focused = challengeView;
    // Vim-style command line: non-null while a ":" command is being typed.
    private StringBuilder command;
    // ':' for a command, '/' for a search of the docs.
    private char prompt = ':';
    private String message;
    private boolean awaitingG;
    private final JPanel root;
    private Run run;
    /** What :q does; replaceable so tests don't end the JVM. */
    Runnable quit = () -> System.exit(0);
    private String card = "challenge";

    public App() {
        Theme.select(settings.theme);
        root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.current().bg());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setPreferredSize(new Dimension(1180, 720));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(20, 28, 0, 28));
        top.add(new Brand(() -> startLesson(settings.lesson)), BorderLayout.WEST);
        root.add(new StatusBar(this), BorderLayout.SOUTH);

        deck.setOpaque(false);
        deck.add(challengeView, "challenge");
        deck.add(resultView, "result");
        deck.add(lessonsView, "lessons");
        deck.add(statsView, "stats");
        deck.add(settingsView, "settings");
        // The strip holding the logo sits in the same layer as the screens, so the docs and
        // replays cover it too, and a click on it reaches them rather than the logo.
        JPanel screen = new JPanel(new BorderLayout());
        screen.setOpaque(false);
        screen.add(top, BorderLayout.NORTH);
        screen.add(deck, BorderLayout.CENTER);
        // The docs lie over whichever screen is showing, so the two share one space.
        JPanel stack = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }
        };
        stack.setLayout(new OverlayLayout(stack));
        stack.setOpaque(false);
        stack.add(docsOverlay);
        stack.add(replayOverlay);
        stack.add(screen);
        root.add(stack, BorderLayout.CENTER);

        startLesson(settings.lesson);
    }

    public JComponent root() {
        return root;
    }

    Run run() {
        return run;
    }

    History history() {
        return history;
    }

    String card() {
        return card;
    }

    boolean inIntro() {
        return challengeView.inIntro();
    }

    /** Repaints everything, status bar included; call after Vim's state changes. */
    void refresh() {
        root.repaint();
    }

    int lessonIndex() {
        return settings.lesson;
    }

    void startLesson(int index) {
        settings.lesson = Math.max(0, Math.min(index, Lessons.ALL.size() - 1));
        settings.save();
        run = new Run(Lessons.ALL.get(settings.lesson), random);
        challengeView.begin();
        show("challenge", challengeView);
    }

    void finishRun() {
        if (!card.equals("challenge")) {
            return;
        }
        Attempt attempt = run.attempt(System.currentTimeMillis());
        OptionalDouble efficiency = history.bestEfficiency(attempt.lesson());
        OptionalDouble seconds = history.bestSeconds(attempt.lesson());
        history.add(attempt);
        resultView.show(run, attempt, efficiency, seconds);
        show("result", resultView);
    }

    void showSettings() {
        settingsView.open();
        show("settings", settingsView);
    }

    /** Back to the lesson in progress, without restarting it. */
    void showLesson() {
        if (run.finished()) {
            startLesson(settings.lesson);
        } else {
            show("challenge", challengeView);
        }
    }

    void showLessons() {
        lessonsView.open(settings.lesson);
        show("lessons", lessonsView);
    }

    void showStats() {
        statsView.reset();
        show("stats", statsView);
    }

    private void show(String name, JComponent focus) {
        card = name;
        focused = focus;
        docsOverlay.setVisible(false);
        replayOverlay.setVisible(false);
        cards.show(deck, name);
        focus.requestFocusInWindow();
        root.repaint();
    }

    boolean docsOpen() {
        return docsOverlay.isVisible();
    }

    /** Opens the reference over the current screen, or closes it if it is already open. */
    void toggleDocs() {
        if (docsOpen()) {
            closeDocs();
        } else {
            docsOverlay.setVisible(true);
            docsView.open();
            docsView.requestFocusInWindow();
            root.repaint();
        }
    }

    void closeDocs() {
        docsOverlay.setVisible(false);
        (replayOpen() ? replayView : focused).requestFocusInWindow();
        root.repaint();
    }

    boolean replayOpen() {
        return replayOverlay.isVisible();
    }

    /** Which task the replay is showing, counting from zero. */
    int replayTask() {
        return replayView.task();
    }

    /** Opens a small replay of one task of the run just finished, over the results screen. */
    void replay(int task) {
        if (!card.equals("result")) {
            return;
        }
        replayOverlay.setVisible(true);
        replayView.show(run, task);
        replayView.requestFocusInWindow();
        root.revalidate();
        root.repaint();
    }

    void closeReplay() {
        replayOverlay.setVisible(false);
        focused.requestFocusInWindow();
        root.repaint();
    }

    void setTheme(String name) {
        Theme.select(name);
        settings.theme = Theme.current().name();
        settings.save();
        root.repaint();
    }

    boolean dvorak() {
        return settings.dvorak;
    }

    void setDvorak(boolean on) {
        settings.dvorak = on;
        settings.save();
        root.repaint();
    }

    /** The command being typed after ":", or null. */
    char prompt() {
        return prompt;
    }

    /** The docs' current search and match count for the status bar; empty if none. */
    String docsStatus() {
        return docsView.status();
    }

    String command() {
        return command == null ? null : command.toString();
    }

    /** An error from the last command, shown until the next key. */
    String message() {
        return message;
    }

    /**
     * Keys that work on every screen, checked before the screen itself sees them: ":" opens the
     * command line, gt and gT cycle the screens, gg and G jump within a list. Returns true if
     * the key was used here.
     */
    boolean globalKey(KeyEvent e) {
        if (command != null) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> command = null;
                    case KeyEvent.VK_ENTER -> {
                        String typed = command.toString().strip();
                        command = null;
                        if (prompt == '/') {
                            if (!docsView.search(typed)) {
                                message = "E486: Pattern not found: " + typed;
                            }
                        } else {
                            execute(typed);
                        }
                    }
                    case KeyEvent.VK_BACK_SPACE -> {
                        if (command.isEmpty()) {
                            command = null;
                        } else {
                            command.setLength(command.length() - 1);
                        }
                    }
                    default -> {
                    }
                }
            } else if (e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() >= 32
                    && e.getKeyChar() != 127 && !e.isControlDown() && !e.isMetaDown()) {
                command.append(e.getKeyChar());
            }
            if (prompt == '/') {
                // Matches light up as the search is typed, and go back if it is abandoned.
                docsView.preview(command == null ? null : command.toString());
            }
            refresh();
            return true;
        }
        char c = e.getKeyChar();
        if (e.getID() != KeyEvent.KEY_TYPED || c < 32 || c == 127 || e.isControlDown()
                || e.isMetaDown()) {
            return false;
        }
        if (message != null) {
            message = null;
            refresh();
        }
        if (card.equals("challenge") && !challengeView.inIntro() && !docsOpen()) {
            // Mid-task the keys belong to Vim, except where Vim itself would hand them over.
            Vim vim = run.vim();
            if (vim.mode() == Vim.Mode.INSERT || vim.mode() == Vim.Mode.SEARCH) {
                return false;
            }
            if (c == ':' && vim.pending().isEmpty()) {
                openCommandLine(':');
                return true;
            }
            if (vim.pending().equals("g") && (c == 't' || c == 'T')) {
                vim.key(Vim.ESC);
                cycleScreen(c == 't' ? 1 : -1);
                return true;
            }
            return false;
        }
        if (awaitingG) {
            awaitingG = false;
            if (c == 't' || c == 'T') {
                cycleScreen(c == 't' ? 1 : -1);
            } else if (c == 'g') {
                jump(true);
            }
            return true;
        }
        switch (c) {
            case ':' -> openCommandLine(':');
            case '/' -> {
                // Searching is for the docs; elsewhere the key is left to the screen.
                if (!docsOpen()) {
                    return false;
                }
                openCommandLine('/');
            }
            case 'g' -> awaitingG = true;
            case 'G' -> jump(false);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void openCommandLine(char kind) {
        prompt = kind;
        command = new StringBuilder();
        refresh();
    }

    private void cycleScreen(int step) {
        List<String> screens = List.of("challenge", "lessons", "stats", "settings");
        int at = Math.max(0, screens.indexOf(card));
        switch (screens.get((at + step + screens.size()) % screens.size())) {
            case "lessons" -> showLessons();
            case "stats" -> showStats();
            case "settings" -> showSettings();
            default -> showLesson();
        }
    }

    private void jump(boolean top) {
        if (docsOpen()) {
            docsView.jump(top);
            return;
        }
        switch (card) {
            case "lessons" -> lessonsView.jump(top);
            case "stats" -> statsView.jump(top);
            case "settings" -> settingsView.jump(top);
            default -> {
            }
        }
    }

    /** Runs a ":" command. The error messages borrow Vim's own wording and numbers. */
    void execute(String typed) {
        message = null;
        if (typed.isEmpty()) {
            return;
        }
        String[] parts = typed.split("\\s+", 2);
        String argument = parts.length > 1 ? parts[1] : "";
        if (parts[0].matches("\\d{1,6}")) {
            int number = Integer.parseInt(parts[0]);
            if (number >= 1 && number <= Lessons.ALL.size()) {
                startLesson(number - 1);
            } else {
                message = "E16: Invalid range: there are " + Lessons.ALL.size() + " lessons";
            }
            return;
        }
        switch (parts[0]) {
            case "docs", "doc" -> toggleDocs();
            case "lessons", "ls" -> showLessons();
            case "stats" -> showStats();
            case "settings", "help", "h" -> showSettings();
            case "set" -> {
                // Vim's own way of switching this on and off.
                String option = argument.replace(" ", "");
                if (option.isEmpty()) {
                    showSettings();
                } else if (option.equals("keymap=dvorak")) {
                    execute("dvorak");
                } else if (option.equals("keymap=")) {
                    execute("qwerty");
                } else {
                    message = "E518: Unknown option: " + argument;
                }
            }
            case "lesson", "l", "ready" -> showLesson();
            case "restart", "e", "e!" -> startLesson(settings.lesson);
            case "next", "n" -> {
                if (settings.lesson == Lessons.ALL.size() - 1) {
                    message = "E165: Cannot go beyond last lesson";
                } else {
                    startLesson(settings.lesson + 1);
                }
            }
            case "prev", "previous", "p", "N" -> {
                if (settings.lesson == 0) {
                    message = "E164: Cannot go before first lesson";
                } else {
                    startLesson(settings.lesson - 1);
                }
            }
            case "colorscheme", "colo", "color", "theme" -> {
                if (argument.isEmpty()) {
                    setTheme(Theme.next().name());
                } else if (Arrays.stream(Theme.ALL).anyMatch(t -> t.name().equals(argument))) {
                    setTheme(argument);
                } else {
                    message = "E185: Cannot find color scheme '" + argument + "'";
                }
            }
            case "dvorak" -> {
                setDvorak(true);
                message = "Keyboard layout: dvorak. :qwerty switches back.";
            }
            case "qwerty" -> {
                setDvorak(false);
                message = "Keyboard layout: qwerty.";
            }
            case "q", "q!", "quit", "qa", "wq", "x" -> {
                // As in a manual page, quitting the docs only closes the docs; the same goes
                // for a replay.
                if (docsOpen()) {
                    closeDocs();
                } else if (replayOpen()) {
                    closeReplay();
                } else {
                    quit.run();
                }
            }
            default -> message = "E492: Not an editor command: " + typed;
        }
        refresh();
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "vimdojo");
        System.setProperty("apple.awt.application.appearance", "system");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("vimdojo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            App app = new App();
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(app::globalKey);
            frame.setContentPane(app.root());
            frame.pack();
            frame.setMinimumSize(new Dimension(820, 560));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            app.challengeView.requestFocusInWindow();
        });
    }
}
