package vimdojo;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

/** A lesson introduces a few keys, then drills them over a series of tasks. */
record Lesson(String id, String title, List<Key> keys, Function<Random, List<Task>> tasks) {

    record Key(String key, String does) {
    }
}
