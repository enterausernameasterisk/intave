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

package de.jpx3.intave.user.meta;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ConnectionMetadataKeepAliveTest {
  @Test
  void preservesRepeatedIdentifiers() {
    ConnectionMetadata connection = new ConnectionMetadata(null);

    connection.addPendingKeepAlive(42L, 100L);
    connection.addPendingKeepAlive(42L, 200L);

    assertEquals(100L, connection.pollPendingKeepAlive(42L));
    assertEquals(200L, connection.pollPendingKeepAlive(42L));
    assertNull(connection.pollPendingKeepAlive(42L));
    assertEquals(Collections.emptySet(), connection.pendingKeepAliveIdentifiers());
  }

  @Test
  void discardsOnlyExpiredResponses() {
    ConnectionMetadata connection = new ConnectionMetadata(null);
    connection.addPendingKeepAlive(1L, 99L);
    connection.addPendingKeepAlive(1L, 100L);
    connection.addPendingKeepAlive(2L, 101L);

    connection.discardPendingKeepAlivesBefore(100L);

    assertEquals(100L, connection.pollPendingKeepAlive(1L));
    assertEquals(101L, connection.pollPendingKeepAlive(2L));
  }
}
