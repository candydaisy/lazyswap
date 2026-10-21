package dev.lazyswap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleTest {
    @Test
    void parsesAllForms() {
        for (String text : List.of("1,5 -> 2,3", "1,5 2,3", "[1,5] -> [2,3]", "[1, 5]->[2,3]")) {
            LazySwap.Rule r = LazySwap.parseRule(text.replace(", ", ","));
            assertEquals(List.of(0, 4), r.from, text);
            assertEquals(List.of(1, 2), r.to, text);
        }
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> LazySwap.parseRule("1"));
        assertThrows(IllegalArgumentException.class, () -> LazySwap.parseRule("0 -> 2"));
        assertThrows(IllegalArgumentException.class, () -> LazySwap.parseRule("1 -> x"));
        assertThrows(IllegalArgumentException.class, () -> LazySwap.parseRule("1 -> 2 -> 3"));
    }

    @Test
    void cyclesTargetsPerHit() {
        LazySwap.Rule r = LazySwap.parseRule("4 -> 5,4");
        assertEquals(List.of(4, 3, 4, 3), List.of(r.nextTarget(), r.nextTarget(), r.nextTarget(), r.nextTarget()));
    }
}
