package net.timafe.triptale.util;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Random list picks that avoid repeating the previous pick. */
final class RandomPick {

    private RandomPick() {
    }

    /**
     * A random element of {@code items} that differs from {@code previous} — unless it is the
     * only element. {@code previous} may be null (first pick).
     */
    static <T> Optional<T> from(List<T> items, RandomGenerator rnd, T previous) {
        if (items.isEmpty()) return Optional.empty();
        List<T> candidates = items.size() > 1 && previous != null
                ? items.stream().filter(i -> !i.equals(previous)).toList()
                : items;
        if (candidates.isEmpty()) candidates = items;
        return Optional.of(candidates.get(rnd.nextInt(candidates.size())));
    }
}
