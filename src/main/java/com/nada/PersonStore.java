package com.nada;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PersonStore {
    private final Map<Integer, Person> people = new HashMap<>();
    private final Map<Integer, Set<Integer>> contacts = new HashMap<>();

    private int nextId = 1;

    public Person createPerson(String name) {
        Person person = new Person(nextId++, name);

        people.put(person.getId(), person);
        contacts.put(person.getId(), new HashSet<>());

        return person;
    }

    public Person getPerson(int id) {
        return people.get(id);
    }

    public boolean knowsPerson(int id) {
        return people.containsKey(id);
    }

    public boolean connect(int a, int b) {
        if (a == b || !knowsPerson(a) || !knowsPerson(b)) {
            return false;
        }

        contacts.get(a).add(b);
        contacts.get(b).add(a);

        return true;
    }

    public Set<Integer> getContactIds(int id) {
        return contacts.getOrDefault(id, Set.of());
    }
}
