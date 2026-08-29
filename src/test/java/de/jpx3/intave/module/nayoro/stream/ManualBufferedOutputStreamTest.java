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

package de.jpx3.intave.module.nayoro.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualBufferedOutputStreamTest {
  @Test
  void closeFlushesTheTailBeforeClosingTheDestination() throws IOException {
    RecordingOutputStream destination = new RecordingOutputStream();
    ManualBufferedOutputStream stream = new ManualBufferedOutputStream(destination, 8);

    stream.write(new byte[]{1, 2, 3});
    stream.close();

    assertArrayEquals(new byte[]{1, 2, 3}, destination.toByteArray());
    assertEquals(1, destination.closeCount);
  }

  @Test
  void emptyAndRepeatedCloseOnlyCloseTheDestinationOnce() throws IOException {
    RecordingOutputStream destination = new RecordingOutputStream();
    ManualBufferedOutputStream stream = new ManualBufferedOutputStream(destination, 8);

    stream.close();
    stream.close();

    assertEquals(0, destination.size());
    assertEquals(1, destination.closeCount);
  }

  private static final class RecordingOutputStream extends ByteArrayOutputStream {
    private int closeCount;

    @Override
    public void close() throws IOException {
      closeCount++;
      super.close();
    }
  }
}
