package com.nada;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathFinderTest {

    @Test
    void findsShortestPathWithinThreeHops() {
        PersonStore store = new PersonStore();

        store.createPerson("Asha");
        store.createPerson("Rohan");
        store.createPerson("Diya");
        store.createPerson("Kabir");

        store.connect(1, 2);
        store.connect(2, 3);
        store.connect(3, 4);

        PathFinder finder = new PathFinder(store);

        List<Integer> path =
                finder.findShortestPath(1, 4, 3);

        assertEquals(List.of(1, 2, 3, 4), path);
    }

    @Test
    void rejectsPathLongerThanThreeHops() {
        PersonStore store = new PersonStore();

        for (int i = 1; i <= 5; i++) {
            store.createPerson("Person " + i);
        }

        store.connect(1, 2);
        store.connect(2, 3);
        store.connect(3, 4);
        store.connect(4, 5);

        PathFinder finder = new PathFinder(store);

        List<Integer> path =
                finder.findShortestPath(1, 5, 3);

        assertTrue(path.isEmpty());
    }

    @Test
    void samePersonHasZeroHops() {
        PersonStore store = new PersonStore();
        store.createPerson("Asha");

        PathFinder finder = new PathFinder(store);

        assertEquals(
                List.of(1),
                finder.findShortestPath(1, 1, 3)
        );
    }
}
