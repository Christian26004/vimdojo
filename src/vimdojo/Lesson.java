package vimdojo;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * A lesson introduces a few keys, then drills them over a series of tasks. A lesson with no keys
 * of its own, like the review, explains itself with a note instead.
 */
record Lesson(String id, String title, List<Key> keys, Function<Random, List<Task>> tasks,
              String note) {

    Lesson(String id, String title, List<Key> keys, Function<Random, List<Task>> tasks) {
        this(id, title, keys, tasks, null);
    }

    record Key(String key, String does) {
    }
}
