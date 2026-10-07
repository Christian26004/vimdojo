package vimdojo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.Collectors;
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
    private final StatusBar statusBar = new StatusBar(this);
    private final EraseDialog eraseDialog = new EraseDialog(this);
    private final Guide guide;
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
    /** The efficiency, in percent, a lesson needs before the next one opens. */
    static final double PASS = 50;
    /** What :q does; replaceable so tests don't end the JVM. */
    Runnable quit = () -> System.exit(0);
    private String card = "challenge";

    public App() {
        Theme.select(settings.theme);
        JPanel body = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.current().bg());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(20, 28, 0, 28));
        top.add(new Brand(() -> startLesson(settings.lesson)), BorderLayout.WEST);
        body.add(statusBar, BorderLayout.SOUTH);

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
        stack.add(eraseDialog);
        stack.add(docsOverlay);
        stack.add(replayOverlay);
        stack.add(screen);
        body.add(stack, BorderLayout.CENTER);

        // The tour lies over everything, the status bar included.
        guide = new Guide(body, tour(), () -> endTour(true), () -> endTour(false));
        root = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }
        };
        root.setLayout(new OverlayLayout(root));
        root.setPreferredSize(new Dimension(1180, 720));
        root.add(guide);
        root.add(body);

        startLesson(settings.lesson);
        // Shown once, to someone who has never used the app.
        if (!settings.finishedGuide && history.all().isEmpty()) {
            guide.start();
        }
    }

    /** The stops of the first-run tour, each pointing at part of the window. */
    private List<Guide.Step> tour() {
        return List.of(
            new Guide.Step(this::showLesson, List::of,
                    "Welcome to vimdojo. This short tour shows you around before your first "
                            + "lesson."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(challengeView, challengeView.introKeys())),
                    "Each lesson starts on a card like this one, listing the keys it teaches."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(challengeView, challengeView.introDemo())),
                    "The demonstration solves the lesson's tasks one key at a time. "
                            + "Then it's your turn, scored on keystrokes and time."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(statusBar, statusBar.modeArea())),
                    "This block shows Vim's current mode, like Vim's own status line."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(statusBar, statusBar.hintsArea())),
                    "Next to it are the keys that work on the screen you're on."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(statusBar, statusBar.linksArea())),
                    "These open the other screens. `gt` steps through them too, and `:` "
                            + "starts a command, as in Vim."),
            new Guide.Step(this::showLessons, () -> page("lessons"),
                    "Lessons lists all " + Lessons.LESSONS.size() + " lessons and two mixes "
                            + "for practice. Pick one with `j` `k` and `enter`."),
            new Guide.Step(this::showStats, () -> page("stats"),
                    "Stats keeps your best efficiency and time for each lesson, and a calendar "
                            + "of the days you practiced."),
            new Guide.Step(this::showSettings, () -> page("settings"),
                    "Settings holds the theme, the keyboard layout, and a way to erase your "
                            + "progress."),
            new Guide.Step(() -> {
                if (!docsOpen()) {
                    toggleDocs();
                }
            }, () -> List.of(exactly(docsView), spot(statusBar, statusBar.linkArea("docs"))),
                    "The docs list every key and command, each with an example. Open them any "
                            + "time with `:docs`."),
            new Guide.Step(this::showLesson,
                    () -> List.of(spot(challengeView, challengeView.introKeys()),
                            spot(challengeView, challengeView.introDemo())),
                    "That's the tour. `:tour` shows it again. Your first lesson is ready."));
    }

    /** A part of a component with a little room around it, in the window's coordinates. */
    private Rectangle spot(JComponent in, Rectangle part) {
        // Only what can be seen: in a short window the lesson card runs off the bottom.
        Rectangle seen = part.intersection(in.getVisibleRect());
        if (!in.isShowing() || seen.isEmpty()) {
            return new Rectangle();
        }
        Rectangle r = SwingUtilities.convertRectangle(in, seen, root);
        r.grow(8, 8);
        return r;
    }

    /** All of a component, with no room around it, so nothing next to it shows through. */
    private Rectangle exactly(JComponent in) {
        return in.isShowing() ? SwingUtilities.convertRectangle(in,
                new Rectangle(0, 0, in.getWidth(), in.getHeight()), root) : new Rectangle();
    }

    /** A screen and its link in the status bar. */
    private List<Rectangle> page(String name) {
        return List.of(exactly(deck), spot(statusBar, statusBar.linkArea(name)));
    }

    boolean guideOpen() {
        return guide.active();
    }

    /** Which step of the tour is showing, counting from zero. */
    int guideStep() {
        return guide.step();
    }

    /** Closes the tour for good. Seeing it through starts the first lesson. */
    private void endTour(boolean begin) {
        settings.finishedGuide = true;
        settings.save();
        showLesson();
        if (begin) {
            challengeView.start();
        }
        root.repaint();
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
        startLesson(index, false);
    }

    /**
     * Opens a lesson on its introduction card with fresh tasks. A lesson never finished before
     * runs guided, as does any lesson when asked; otherwise it is practice. The mixes draw
     * their tasks from the other lessons. A locked lesson opens too, to look at, but can't be
     * begun.
     */
    void startLesson(int index, boolean guide) {
        index = Math.max(0, Math.min(index, Lessons.ALL.size() - 1));
        prepare(index, guide);
        show("challenge", challengeView);
    }

    /** Makes the run for a lesson without changing screen. */
    private void prepare(int index, boolean guide) {
        settings.lesson = index;
        settings.save();
        Lesson lesson = Lessons.ALL.get(settings.lesson);
        boolean guided = !lesson.isMix() && (guide || history.runs(lesson.id()) == 0);
        List<Task> tasks = switch (lesson.kind()) {
            case RANDOM_MIX -> Lessons.randomMix(random,
                    Lessons.LESSONS.subList(0, frontier() + 1));
            case WEAK_SPOTS -> Lessons.weakSpots(random, history.recentEfficiency());
            case LESSON -> (guided ? lesson.guided() : lesson.practice()).apply(random);
        };
        run = new Run(lesson, tasks, guided);
        challengeView.begin();
    }

    /**
     * The furthest lesson open to you: one past the furthest you have passed, by scoring at
     * least {@link #PASS}% on it. Every lesson up to it is open; the ones after it are locked.
     */
    int frontier() {
        int frontier = 0;
        for (int i = 0; i < Lessons.LESSONS.size(); i++) {
            if (history.bestEfficiency(Lessons.LESSONS.get(i).id()).orElse(0) >= PASS) {
                frontier = Math.max(frontier, i + 1);
            }
        }
        return Math.min(frontier, Lessons.LESSONS.size() - 1);
    }

    /** Lessons up to the frontier are open; the mixes open once there are two to mix. */
    boolean unlocked(int index) {
        return Lessons.ALL.get(index).isMix() ? frontier() >= 1 : index <= frontier();
    }

    /** Whether the lesson on screen can't be played yet. */
    boolean locked() {
        return !unlocked(settings.lesson);
    }

    /** Called instead of beginning a locked lesson: says what it takes. */
    void refuseLocked() {
        message = "requires " + Math.round(PASS) + "% or more on "
                + (Lessons.ALL.get(settings.lesson).isMix() ? "lesson 1" : "previous lesson");
        refresh();
    }

    /** Erases every result, which locks the lessons again. */
    void eraseProgress() {
        history.clear();
        if (!unlocked(settings.lesson)) {
            prepare(0, false);
        }
        message = "Progress erased";
        refresh();
    }

    /** Asks for the data folder's location before erasing; see {@link EraseDialog}. */
    void askToErase() {
        eraseDialog.open();
    }

    boolean eraseOpen() {
        return eraseDialog.isVisible();
    }

    /** What has been typed into the erase dialog so far. */
    String eraseTyped() {
        return eraseDialog.typed();
    }

    /**
     * The introduction card's text for a lesson without keys of its own. Weak spots also lists
     * the lessons it will favour.
     */
    String introNote() {
        Lesson lesson = run.lesson();
        if (lesson.kind() != Lesson.Kind.WEAK_SPOTS) {
            return lesson.note();
        }
        Map<String, Double> efficiency = history.recentEfficiency();
        if (efficiency.isEmpty()) {
            return "Ten tasks from random lessons until you have some results.";
        }
        String weakest = efficiency.entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).limit(3)
                .map(e -> Lessons.byId(e.getKey()).title() + " " + Math.round(e.getValue()) + "%")
                .collect(Collectors.joining(", "));
        return lesson.note() + " Right now: " + weakest + ".";
    }

    void finishRun() {
        if (!card.equals("challenge")) {
            return;
        }
        long now = System.currentTimeMillis();
        Attempt attempt = run.attempt(now);
        OptionalDouble efficiency = history.bestEfficiency(attempt.lesson());
        OptionalDouble seconds = history.bestSeconds(attempt.lesson());
        history.add(attempt, run.results(now));
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
        eraseDialog.setVisible(false);
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
        if (guide.active()) {
            // The tour has the keyboard to itself.
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> guide.next();
                    case KeyEvent.VK_ESCAPE -> guide.skip();
                    default -> {
                    }
                }
            }
            return true;
        }
        if (eraseDialog.isVisible()) {
            // The dialog has the keyboard to itself: typing goes into its field.
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> eraseDialog.confirm();
                    case KeyEvent.VK_ESCAPE -> eraseDialog.close();
                    case KeyEvent.VK_BACK_SPACE -> eraseDialog.backspace();
                    default -> {
                    }
                }
            } else if (e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() >= 32
                    && e.getKeyChar() != 127 && !e.isControlDown() && !e.isMetaDown()) {
                eraseDialog.type(e.getKeyChar());
            }
            return true;
        }
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
            case "tour" -> guide.start();
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
            case "guided", "guide" -> {
                if (Lessons.ALL.get(settings.lesson).isMix()) {
                    message = "The mixes have no guided version: pick a lesson first";
                } else {
                    startLesson(settings.lesson, true);
                }
            }
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
