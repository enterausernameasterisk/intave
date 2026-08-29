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

package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesyncWatchdogTest {

  @Test
  void rawPositionDisagreementIsStillDetectedOutsideTeleports() {
    DesyncWatchdog.PositionBundle positions = positions(
      new Position(1, 2, 3),
      new Position(1, 2, 3),
      new Position(100, 200, 300),
      false
    );

    assertTrue(positions.anyDesynced());
    assertTrue(positions.serverAndPrefilteredPendingPositionDesynced());
    assertTrue(positions.intaveAcceptedAndPrefilteredPendingPositionDesynced());
  }

  @Test
  void serverAndVerifiedDifferenceIsDetected() {
    DesyncWatchdog.PositionBundle positions = positions(
      new Position(0, 0, 0),
      new Position(0, 0, 4.0001),
      new Position(0, 0, 4.0001),
      false
    );

    assertTrue(positions.anyDesynced());
  }

  @Test
  void fourBlockDifferenceIsWithinTheAllowedBoundary() {
    DesyncWatchdog.PositionBundle positions = positions(
      new Position(0, 0, 0),
      new Position(0, 0, 4),
      new Position(0, 0, 4),
      false
    );

    assertFalse(positions.anyDesynced());
  }

  @Test
  void vehiclePositionDoesNotRequireResynchronization() {
    DesyncWatchdog.PositionBundle positions = positions(
      new Position(0, 0, 0),
      new Position(100, 100, 100),
      new Position(100, 100, 100),
      true
    );

    assertFalse(positions.anyDesynced());
  }

  @Test
  void teleportConfirmationWindowIsPending() {
    MovementMetadata movement = new MovementMetadata(null, null);

    movement.awaitTeleport = true;
    assertTrue(DesyncWatchdog.teleportPending(movement));

    movement.awaitTeleport = false;
    movement.awaitOutgoingTeleport = true;
    assertTrue(DesyncWatchdog.teleportPending(movement));

    movement.awaitOutgoingTeleport = false;
    assertFalse(DesyncWatchdog.teleportPending(movement));
  }

  private DesyncWatchdog.PositionBundle positions(
    Position server,
    Position verified,
    Position raw,
    boolean inVehicle
  ) {
    return new DesyncWatchdog.PositionBundle(server, verified, raw, inVehicle);
  }
}
