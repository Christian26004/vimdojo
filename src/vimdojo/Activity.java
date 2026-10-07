package vimdojo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lessons finished per calendar day, and the streaks that follow from them. */
record Activity(Map<LocalDate, Integer> perDay, LocalDate today, int currentStreak,
                int longestStreak) {

    static Activity of(List<Attempt> attempts, LocalDate today, ZoneId zone) {
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        for (Attempt a : attempts) {
            perDay.merge(Instant.ofEpochMilli(a.timestamp()).atZone(zone).toLocalDate(), 1,
                    Integer::sum);
        }
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : perDay.keySet()) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        // A streak survives until a whole day is missed, so one that reaches yesterday still
        // counts: there is the rest of today to extend it.
        int current = 0;
        LocalDate day = perDay.containsKey(today) ? today : today.minusDays(1);
        while (perDay.containsKey(day)) {
            current++;
            day = day.minusDays(1);
        }
        return new Activity(perDay, today, current, longest);
    }

    int on(LocalDate day) {
        return perDay.getOrDefault(day, 0);
    }

    boolean practicedToday() {
        return on(today) > 0;
    }

    /** 0 for a day with nothing, up to 4 for a very busy one; picks the shade in the grid. */
    static int level(int lessons) {
        return lessons == 0 ? 0 : lessons == 1 ? 1 : lessons <= 3 ? 2 : lessons <= 6 ? 3 : 4;
    }

    /** One line that says where the streak stands and what would move it forward. */
    String nudge() {
        String days = currentStreak + (currentStreak == 1 ? " day" : " days");
        if (practicedToday()) {
            return currentStreak > 1 ? "Done for today. " + days + " in a row."
                    : "Done for today. Come back tomorrow to start a streak.";
        }
        return currentStreak > 0 ? "Finish a lesson today to keep your " + days + " in a row."
                : "Finish a lesson today to start a streak.";
    }
}
