/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public final class HistoryWindow<T> implements Set<T> {
  private final Object monitor = new Object();

  private final int capacity;
  private final T[] elements;
  private int pos;
  private long fullSize;

  public HistoryWindow(int capacity) {
    this.capacity = capacity;
    this.elements = (T[]) new Object[capacity];
  }

  @Override
  public int size() {
    synchronized (monitor) {
      return Math.min(Math.abs((int) fullSize), capacity);
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (monitor) {
      return fullSize == 0;
    }
  }

  @Override
  public boolean contains(Object o) {
    synchronized (monitor) {
      for (T element : elements) {
        if (element.equals(o)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    // iterate from pos backwards to pos + 1
    return new Iterator<T>() {
      private int index = pos;
      private int remaining = size();

      @Override
      public boolean hasNext() {
        return remaining > 0;
      }

      @Override
      public T next() {
        if (remaining <= 0) {
          throw new IndexOutOfBoundsException();
        }
        remaining--;
        return elements[index = ((index - 1) % capacity)];
      }
    };
  }

  public T back(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("Length must be greater than or equal to 0");
    }
    synchronized (monitor) {
      if (length > Math.min(Math.abs((int) fullSize), capacity)) {
        throw new IllegalArgumentException("Can not go back more than the size of the history");
      }
      int index = (pos - length) % capacity;
      while (index < 0) {
        index += capacity;
      }
      return elements[index];
    }
  }

  @Override
  public @NotNull Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull <T1> T1[] toArray(@NotNull T1[] a) {
    throw new UnsupportedOperationException();
  }

  public boolean add(T element) {
    synchronized (monitor) {
      fullSize++;
      elements[pos = ((pos + 1) % capacity)] = element;
      return true;
    }
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    synchronized (monitor) {
      for (T element : c) {
        fullSize++;
        elements[pos = ((pos + 1) % capacity)] = element;
      }
      return true;
    }
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    synchronized (monitor) {
      for (int i = 0; i < capacity; i++) {
        elements[i] = null;
      }
      pos = 0;
      fullSize = 0;
    }
  }

  @Override
  public boolean equals(Object o) {
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
