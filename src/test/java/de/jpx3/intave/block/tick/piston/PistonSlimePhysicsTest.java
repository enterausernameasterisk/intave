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

package de.jpx3.intave.block.tick.piston;

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PistonSlimePhysicsTest {
	@Test
	void slimeOverwritesOnlyTheMovementAxisOnItsFirstTick() {
		PistonSlimeMovement movement = movement(Direction.UP, new BlockPosition(0, 0, 0), 10);
		BoundingBox player = BoundingBox.fromBounds(0.2, 1.1, 0.2, 0.8, 2.9, 0.8);

		Motion result = movement.apply(10, player, new Motion(0.25, -0.08, -0.3));

		assertEquals(new Motion(0.25, 1.0, -0.3), result);
	}

	@Test
	void movingSlimeAffectsThePlayerOnBothHalfBlockSteps() {
		PistonSlimeMovement movement = movement(Direction.UP, new BlockPosition(0, 0, 0), 20);
		BoundingBox player = BoundingBox.fromBounds(0.2, 1.75, 0.2, 0.8, 3.55, 0.8);

		assertEquals(new Motion(0.1, 1.0, 0.2), movement.apply(21, player, new Motion(0.1, 0.3, 0.2)));
		assertEquals(new Motion(0.1, 0.3, 0.2), movement.apply(22, player, new Motion(0.1, 0.3, 0.2)));
	}

	@Test
	void upwardPistonPushAddsClientCollisionEpsilon() {
		PistonSlimeMovement movement = movement(Direction.UP, new BlockPosition(0, 0, 0), 20);
		BoundingBox player = BoundingBox.fromBounds(0.2, 1.0, 0.2, 0.8, 2.8, 0.8);

		assertEquals(0.51, movement.pushDistance(
			new BlockPosition(0, 0, 0), 20, player, true
		), 0.0);
	}

	@Test
	void pushAtOuterSweepBoundaryIsStrict() {
		PistonSlimeMovement movement = movement(Direction.EAST, new BlockPosition(0, 0, 0), 0);
		BoundingBox player = BoundingBox.fromBounds(1.5, 0.0, 0.0, 2.1, 1.8, 0.6);

		assertEquals(0.0, movement.pushDistance(
			new BlockPosition(0, 0, 0), 0, player, true
		), 0.0);
	}

	@Test
	void strictBoundaryContactDoesNotCountAsIntersection() {
		PistonSlimeMovement movement = movement(Direction.EAST, new BlockPosition(0, 0, 0), 0);
		BoundingBox touchingPlayer = BoundingBox.fromBounds(1.5, 0.0, 0.0, 2.1, 1.8, 0.6);

		assertEquals(
			new Motion(0.2, 0.3, 0.4),
			movement.apply(0, touchingPlayer, new Motion(0.2, 0.3, 0.4))
		);
	}

	@Test
	void negativeZMovementWritesTheZAxis() {
		PistonSlimeMovement movement = movement(Direction.NORTH, new BlockPosition(0, 0, 1), 4);
		BoundingBox player = BoundingBox.fromBounds(0.2, 0.1, 0.6, 0.8, 1.8, 1.2);

		assertEquals(
			new Motion(0.2, 0.3, -1.0),
			movement.apply(4, player, new Motion(0.2, 0.3, 0.4))
		);
	}

	@Test
	void multipleActionsApplyInPacketOrder() {
		BoundingBox player = BoundingBox.fromBounds(0.75, 0.5, 0.5, 1.25, 1.8, 1.0);
		PistonSlimeMovement east = movement(Direction.EAST, new BlockPosition(0, 0, 0), 3);
		PistonSlimeMovement up = movement(Direction.UP, new BlockPosition(0, 0, 0), 3);
		PistonSlimeMovement west = movement(Direction.WEST, new BlockPosition(1, 0, 0), 3);

		Motion result = PistonSlimePhysics.apply(
			Arrays.asList(east, up, west), 3, player, new Motion(0.2, -0.4, 0.6)
		);

		assertEquals(new Motion(-1.0, 1.0, 0.6), result);
	}

	private static PistonSlimeMovement movement(
		Direction direction,
		BlockPosition source,
		long startTick
	) {
		return new PistonSlimeMovement(direction, Collections.singletonList(source), startTick);
	}
}
