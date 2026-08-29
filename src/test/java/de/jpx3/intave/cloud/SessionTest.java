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

package de.jpx3.intave.cloud;

import ac.intave.cloud.protocol.ProtocolSpecification;
import ac.intave.cloud.protocol.packets.ServerboundViolation;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static ac.intave.cloud.protocol.Direction.SERVERBOUND;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionTest {
  @Test
  void doesNotPreparePacketRejectedByNegotiation() {
    Session session = new Session(null);
    negotiateServerboundPackets(session.protocol(), Collections.singletonList("HELLO"));
    ServerboundViolation violation = violation();

    session.sendPacket(violation);

    assertFalse(violation.hasIdempotencyToken());
  }

  @Test
  void preparesPacketAcceptedByNegotiation() {
    Session session = new Session(null);
    negotiateServerboundPackets(session.protocol(), Collections.singletonList("VIOLATION"));
    ServerboundViolation violation = violation();

    session.sendPacket(violation);

    assertTrue(violation.hasIdempotencyToken());
  }

  private static void negotiateServerboundPackets(
    ProtocolSpecification protocol,
    java.util.List<String> packetNames
  ) {
    protocol.overrideAvailablePackets(SERVERBOUND, new java.util.HashSet<>(packetNames));
    protocol.overridePacketIds(SERVERBOUND, packetNames);
  }

  private static ServerboundViolation violation() {
    return new ServerboundViolation(
      1,
      "physics",
      "thresholds",
      "message",
      "details",
      1,
      1
    );
  }
}
