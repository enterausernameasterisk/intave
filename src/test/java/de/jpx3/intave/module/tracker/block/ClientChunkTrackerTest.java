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

package de.jpx3.intave.module.tracker.block;

import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static de.jpx3.intave.user.meta.ConnectionMetadata.ClientChunkState.*;
import static org.junit.jupiter.api.Assertions.*;

final class ClientChunkTrackerTest {
  @Test
  void exposesChunkOnlyAfterFeedback() {
    ConnectionMetadata connection = new ConnectionMetadata(null);

    Runnable confirmation = connection.pendingClientChunkLoad(-4, 7);

    assertEquals(PENDING, connection.clientChunkState(-4, 7));
    assertFalse(connection.hasClientChunk(-4, 7));
    assertEquals(Collections.emptySet(), connection.clientChunks());

    confirmation.run();

    long chunkKey = ConnectionMetadata.clientChunkKey(-4, 7);
    assertEquals(LOADED, connection.clientChunkState(-4, 7));
    assertTrue(connection.hasClientChunk(-4, 7));
    assertEquals(Collections.singleton(chunkKey), connection.clientChunks());
    assertEquals(-4, ConnectionMetadata.clientChunkX(chunkKey));
    assertEquals(7, ConnectionMetadata.clientChunkZ(chunkKey));
  }

  @Test
  void ignoresConfirmationAfterUnload() {
    ConnectionMetadata connection = new ConnectionMetadata(null);
    Runnable confirmation = connection.pendingClientChunkLoad(2, 3);

    connection.unloadClientChunk(2, 3);
    confirmation.run();

    assertEquals(UNLOADED, connection.clientChunkState(2, 3));
    assertFalse(connection.hasClientChunk(2, 3));
  }

  @Test
  void ignoresSupersededConfirmation() {
    ConnectionMetadata connection = new ConnectionMetadata(null);
    Runnable oldConfirmation = connection.pendingClientChunkLoad(2, 3);
    Runnable currentConfirmation = connection.pendingClientChunkLoad(2, 3);

    oldConfirmation.run();
    assertEquals(PENDING, connection.clientChunkState(2, 3));

    currentConfirmation.run();
    assertEquals(LOADED, connection.clientChunkState(2, 3));
  }

  @Test
  void clearingChunksInvalidatesPendingConfirmations() {
    ConnectionMetadata connection = new ConnectionMetadata(null);
    Runnable confirmation = connection.pendingClientChunkLoad(2, 3);

    connection.clearClientChunks();
    confirmation.run();

    assertEquals(UNLOADED, connection.clientChunkState(2, 3));
    assertEquals(Collections.emptySet(), connection.clientChunks());
  }
}
