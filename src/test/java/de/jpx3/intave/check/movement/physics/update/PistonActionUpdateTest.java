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

package de.jpx3.intave.check.movement.physics.update;

import de.jpx3.intave.block.tick.piston.PistonSlimeMovement;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.share.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PistonActionUpdateTest {
	@Test
	void actionStartsAfterTheCurrentPlayerMovement() {
		MockSimulationEnvironment environment = new MockSimulationEnvironment();
		environment.setPosition(0.5, 2.51, 0.5);
		environment.setLastPosition(0.5, 1.0, 0.5);
		environment.setVerifiedLastPosition(new Position(0.5, 1.0, 0.5), "test seed");
		environment.setBoundingBox(BoundingBox.fromBounds(0.2, 1.0, 0.2, 0.8, 2.8, 0.8));
		environment.setBaseMotion(new Motion(0.0, -0.01215, 0.0));

		PistonActionUpdate update = update(Direction.UP, new BlockPosition(0, 0, 0), 1);
		update.applyTo(environment);

		assertEquals(new Position(0.5, 1.0, 0.5), environment.verifiedLastPosition());
		assertEquals(new Motion(0.0, -0.01215, 0.0), environment.mutableBaseMotionCopy());
		assertEquals(1, environment.pistonSlimeMovements().size());
		assertEquals(0, environment.pistonSlimeMovements().get(0).startTick());
	}

	@Test
	void multipleActionsInOneTickAreAppendedInCausalOrder() {
		MockSimulationEnvironment environment = new MockSimulationEnvironment();
		PistonActionUpdate first = update(Direction.EAST, new BlockPosition(1, 2, 3), 1);
		PistonActionUpdate second = update(Direction.UP, new BlockPosition(4, 5, 6), 2);

		first.applyTo(environment);
		second.applyTo(environment);

		List<PistonSlimeMovement> movements = environment.pistonSlimeMovements();
		assertEquals(2, movements.size());
		assertEquals(Direction.EAST, movements.get(0).direction());
		assertEquals(Direction.UP, movements.get(1).direction());
		assertEquals(0, movements.get(0).startTick());
		assertEquals(0, movements.get(1).startTick());
	}

	private static PistonActionUpdate update(
		Direction direction,
		BlockPosition source,
		long sequence
	) {
		return new PistonActionUpdate(
			direction,
			Collections.singletonList(source),
			new CausalConstraint(0, 0, sequence)
		);
	}
}
