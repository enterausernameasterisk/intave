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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class HistoryWindowTest {
  @Test
  void testHistoryWindow() {
    HistoryWindow<Integer> historyWindow = new HistoryWindow<>(10);
    for (int i = 0; i <= 40; i++) {
      historyWindow.add(i);
    }
    for (int i = 0; i < 10; i++) {
      assertEquals(40 - i, historyWindow.back(i));
    }
  }

  @Test
  void publishesWritesToConcurrentReaders() throws Exception {
    int capacity = 25;
    int writes = 20_000;
    HistoryWindow<Integer> historyWindow = new HistoryWindow<>(capacity);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch writerDone = new CountDownLatch(1);
    List<Future<?>> readers = new ArrayList<>();

    try {
      Future<?> writer = executor.submit(() -> {
        await(start);
        try {
          for (int i = 0; i < writes; i++) {
            historyWindow.add(i);
          }
        } finally {
          writerDone.countDown();
        }
      });
      for (int i = 0; i < 3; i++) {
        readers.add(executor.submit(() -> {
          await(start);
          while (writerDone.getCount() != 0) {
            if (!historyWindow.isEmpty()) {
              assertNotNull(historyWindow.back(0));
            }
          }
        }));
      }

      start.countDown();
      writer.get(10, TimeUnit.SECONDS);
      for (Future<?> reader : readers) {
        reader.get(10, TimeUnit.SECONDS);
      }

      assertEquals(capacity, historyWindow.size());
      assertEquals(writes - 1, historyWindow.back(0));
    } finally {
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
