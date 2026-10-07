package vimdojo;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * A lesson introduces a few keys, then drills them. The first time through it is guided: its
 * tasks come in teaching order and each says which keys to use. After that it is practice:
 * tasks drawn at random from the same pool, saying only what to do. {@code one} makes a single
 * practice task, for the mixed runs to draw on.
 *
 * <p>The mixes have no keys or tasks of their own: the app builds their runs from the other
 * lessons (see {@link Lessons#randomMix(Random, List)} and {@link Lessons#weakSpots}), and
 * their note explains them on the introduction card.
 */
record Lesson(String id, String title, Kind kind, List<Key> keys,
              Function<Random, List<Task>> guided, Function<Random, List<Task>> practice,
              Function<Random, Task> one, String note) {

    enum Kind { LESSON, RANDOM_MIX, WEAK_SPOTS }

    boolean isMix() {
        return kind != Kind.LESSON;
    }

    record Key(String key, String does) {
    }
}
