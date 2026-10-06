package vimdojo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.OptionalDouble;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
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
    private final JPanel root;
    private Run run;
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

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(18, 30, 10, 30));
        header.add(new Chip("vimdojo", 26f, () -> true, () -> startLesson(settings.lesson)),
                BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        nav.setOpaque(false);
        nav.add(new Chip("lessons", 15f, () -> card.equals("lessons"), this::showLessons));
        nav.add(new Chip("stats", 15f, () -> card.equals("stats"), this::showStats));
        nav.add(new Chip(() -> "theme: " + Theme.current().name(), 15f, () -> false,
                this::nextTheme));
        header.add(nav, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        deck.setOpaque(false);
        deck.add(challengeView, "challenge");
        deck.add(resultView, "result");
        deck.add(lessonsView, "lessons");
        deck.add(statsView, "stats");
        root.add(deck, BorderLayout.CENTER);

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
        cards.show(deck, name);
        focus.requestFocusInWindow();
        root.repaint();
    }

    private void nextTheme() {
        settings.theme = Theme.next().name();
        settings.save();
        root.repaint();
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "vimdojo");
        System.setProperty("apple.awt.application.appearance", "system");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("vimdojo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            App app = new App();
            frame.setContentPane(app.root());
            frame.pack();
            frame.setMinimumSize(new Dimension(820, 560));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            app.challengeView.requestFocusInWindow();
        });
    }
}
