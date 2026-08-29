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

package de.jpx3.intave.module.nayoro.sink;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.EventSink;
import ac.intave.samples.serial.JsonWriter;
import de.jpx3.intave.module.nayoro.Environment;

import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CurrentRecordEventSink extends EventSink {
  private final long start = System.currentTimeMillis();
  private long lastEventAt = start;
  private final List<ByteArrayEntry> packetsPending = new ArrayList<>();
  private long bytesPending;
  private final long maxPendingBytes;
  private final long maxAge;

  public CurrentRecordEventSink(long maxPendingBytes, long maxAge, Environment environment) {
    this.maxPendingBytes = maxPendingBytes;
    this.maxAge = maxAge;
  }

  public void saveTo(OutputStream out) {
    for (ByteArrayEntry packet : packetsPending) {
      try {
        out.write(packet.data());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    packetsPending.clear();
    bytesPending = 0;
  }

  @Override
  public synchronized void visitAny(Event event) {
    try {
      long now = System.currentTimeMillis();
      event.withOffset(Math.max(0, now - lastEventAt));
      lastEventAt = now;
      StringWriter encoded = new StringWriter();
      JsonWriter writer = new JsonWriter(encoded);
      writer.visitAny(event);
      writer.flush();
      byte[] data = encoded.toString().getBytes(StandardCharsets.UTF_8);
      bytesPending += data.length;
      packetsPending.add(new ByteArrayEntry(data));
      while (bytesPending > maxPendingBytes && !packetsPending.isEmpty()) {
        bytesPending -= packetsPending.remove(0).data().length;
      }
      while (!packetsPending.isEmpty() &&
        System.currentTimeMillis() - packetsPending.get(0).timestamp() > maxAge) {
        bytesPending -= packetsPending.remove(0).data().length;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static class ByteArrayEntry {
    private final long time;
    private final byte[] data;

    public ByteArrayEntry(byte[] data) {
      this.time = System.currentTimeMillis();
      this.data = data;
    }

    public long time() {
      return time;
    }

    public long timestamp() {
      return time;
    }

    public byte[] data() {
      return data;
    }
  }

  @Override
  public String name() {
    return "CRES";
  }
}
