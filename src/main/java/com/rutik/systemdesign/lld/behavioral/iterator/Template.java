package com.rutik.systemdesign.lld.behavioral.iterator; /**
 * Iterator Pattern - Template
 *
 * Intent: Provide a way to access the elements of an aggregate object sequentially
 * without exposing its underlying representation.
 *
 * Key participants:
 *  - Iterator         : Defines the interface for traversing elements (hasNext, next).
 *  - ConcreteIterator : Implements the Iterator interface; tracks the current position.
 *  - Aggregate        : Defines an interface for creating an Iterator object.
 *  - ConcreteAggregate: Implements the Aggregate interface; returns an instance of
 *                       the appropriate ConcreteIterator.
 *
 * When to use:
 *  - You need a standard way to traverse different types of collections.
 *  - You want to decouple traversal algorithms from collection implementations.
 *  - You need multiple simultaneous traversals of the same collection.
 */

// ─── Iterator Interface ───────────────────────────────────────────────────────

/**
 * Generic iterator interface (mirrors java.util.Iterator but defined here for clarity).
 */
interface Iterator<T> {
    /** Returns true if there are more elements to visit. */
    boolean hasNext();

    /** Returns the next element and advances the cursor. */
    T next();
}

// ─── Aggregate Interface ──────────────────────────────────────────────────────

/**
 * Any collection that can produce an iterator over its elements.
 */
interface Aggregate<T> {
    Iterator<T> createIterator();
}

// ─── ConcreteAggregate ────────────────────────────────────────────────────────

/**
 * A simple fixed-capacity collection backed by an array.
 */
class ConcreteAggregate<T> implements Aggregate<T> {
    private final Object[] items;
    private int            size = 0;

    public ConcreteAggregate(int capacity) {
        this.items = new Object[capacity];
    }

    public void add(T item) {
        if (size < items.length) {
            items[size++] = item;
        }
    }

    public int size() { return size; }

    @SuppressWarnings("unchecked")
    public T get(int index) { return (T) items[index]; }

    @Override
    public Iterator<T> createIterator() {
        return new ConcreteIterator<>(this);
    }
}

// ─── ConcreteIterator ─────────────────────────────────────────────────────────

/**
 * Traverses a ConcreteAggregate forward, one element at a time.
 */
class ConcreteIterator<T> implements Iterator<T> {
    private final ConcreteAggregate<T> aggregate;
    private int                        cursor = 0;

    public ConcreteIterator(ConcreteAggregate<T> aggregate) {
        this.aggregate = aggregate;
    }

    @Override
    public boolean hasNext() {
        return cursor < aggregate.size();
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new java.util.NoSuchElementException("Iterator exhausted.");
        }
        return aggregate.get(cursor++);
    }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        System.out.println("=== Iterator Pattern Template ===\n");

        ConcreteAggregate<String> collection = new ConcreteAggregate<>(5);
        collection.add("Alpha");
        collection.add("Beta");
        collection.add("Gamma");
        collection.add("Delta");

        System.out.println("Iterating over collection:");
        Iterator<String> iterator = collection.createIterator();
        while (iterator.hasNext()) {
            System.out.println("  " + iterator.next());
        }

        System.out.println("\nIndependent second traversal:");
        Iterator<String> iterator2 = collection.createIterator();
        while (iterator2.hasNext()) {
            System.out.println("  -> " + iterator2.next());
        }
    }
}
