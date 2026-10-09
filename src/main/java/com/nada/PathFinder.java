package com.nada;
import java.util.*;

public class PathFinder {

    private final PersonStore store;

    public PathFinder(PersonStore store) {
        this.store = store;
    }

    public List<Integer> findShortestPath(int from, int to, int maxHops) {

        Queue<List<Integer>> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(List.of(from));
        visited.add(from);

        while (!queue.isEmpty()) {
            List<Integer> path = queue.poll();

            int current = path.get(path.size() - 1);
            if (current == to) return path;

            int hops = path.size() - 1;
            if (hops == maxHops) continue;
            
            for (int contactId : store.getContactIds(current)) {
                if (!visited.contains(contactId)) {
                    visited.add(contactId);
                    List<Integer> newPath = new ArrayList<>(path);
                    newPath.add(contactId);
                    queue.add(newPath);
                }
            }
        }

        return List.of();
    }
}
