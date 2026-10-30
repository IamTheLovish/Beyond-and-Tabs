package com.solegendary.reignofnether.building.production;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A production queue that counts its changes: every change to ANY queue bumps one shared {@link #epoch()}, so a cache
 * of queue totals (QueuePopulation) knows at once when it is out of date, without walking every building to find out.
 * The queues are public lists changed from many places (placements, packets, match end, tests); this catches them all,
 * including changes through an iterator, a list iterator and a sub-list's add / remove / clear (ArrayList routes those
 * through the methods overridden here). One atomic increment per change; reads cost nothing.
 */
public final class ProductionQueueList<E> extends ArrayList<E> {
    private static final AtomicLong EPOCH = new AtomicLong();

    /** Changes to any production queue so far (both sides; only ever grows). */
    public static long epoch() {
        return EPOCH.get();
    }

    private static void changed() {
        EPOCH.incrementAndGet();
    }

    @Override
    public boolean add(E e) {
        changed();
        return super.add(e);
    }

    @Override
    public void add(int index, E element) {
        changed();
        super.add(index, element);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        changed();
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        changed();
        return super.addAll(index, c);
    }

    @Override
    public E set(int index, E element) {
        changed();
        return super.set(index, element);
    }

    @Override
    public E remove(int index) {
        changed();
        return super.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        changed();
        return super.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        changed();
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        changed();
        return super.retainAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        changed();
        return super.removeIf(filter);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        changed();
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        changed();
        super.replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super E> c) {
        changed();
        super.sort(c);
    }

    @Override
    public void clear() {
        changed();
        super.clear();
    }
}
