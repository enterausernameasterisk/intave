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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.AttackEvent;
import ac.intave.samples.event.Event;
import ac.intave.samples.event.HeaderEvent;
import ac.intave.samples.event.PlayerMoveEvent;
import ac.intave.samples.serial.JsonReader;
import ac.intave.samples.serial.JsonWriter;
import ac.intave.samples.share.Classifier;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class NayoroRecordingTest {
  @Test
  void dependencyJsonSerializerRoundTripsNayoroEvents() throws Exception {
    UUID recordingId = UUID.randomUUID();
    HeaderEvent header = new HeaderEvent(
      recordingId, "test", Classifier.LEGIT, 1_786_320_000_000L
    );
    AttackEvent attack = new AttackEvent(8, 13);
    attack.withOffset(20);
    PlayerMoveEvent movement = PlayerMoveEvent.create(
      1, -1,
      new Position(12.5, 64, -3.25), new Rotation(90, 10),
      true, true, false, false, false, true, false, true
    );
    movement.withOffset(50);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (JsonWriter writer = new JsonWriter(output)) {
      header.accept(writer);
      attack.accept(writer);
      movement.accept(writer);
    }

    byte[] recording = output.toByteArray();
    assertTrue(recording.length > 4);
    assertEquals(0x28, recording[0] & 0xff);
    assertEquals(0xb5, recording[1] & 0xff);
    assertEquals(0x2f, recording[2] & 0xff);
    assertEquals(0xfd, recording[3] & 0xff);

    try (JsonReader reader = new JsonReader(new ByteArrayInputStream(recording))) {
      HeaderEvent decodedHeader = assertInstanceOf(HeaderEvent.class, reader.nextEvent());
      assertEquals(recordingId, decodedHeader.id());
      assertEquals("test", decodedHeader.licenseName());
      assertEquals(Classifier.LEGIT, decodedHeader.classifier());

      AttackEvent decodedAttack = assertInstanceOf(AttackEvent.class, reader.nextEvent());
      assertEquals(8, decodedAttack.source());
      assertEquals(13, decodedAttack.target());
      assertEquals(20, decodedAttack.offset());

      Event decodedMovement = reader.nextEvent();
      assertEquals(movement, decodedMovement);
      assertEquals(50, decodedMovement.offset());
      assertNull(reader.nextEvent());
    }
  }
}
