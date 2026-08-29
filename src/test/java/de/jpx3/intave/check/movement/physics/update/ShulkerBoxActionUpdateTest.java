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

import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

final class ShulkerBoxActionUpdateTest {
	private static final BlockPosition POSITION = new BlockPosition(4, 5, 6);

	@Test
	void actionCreatesBranchLocalAnimationState() {
		MockSimulationEnvironment root = new MockSimulationEnvironment();
		SimulationEnvironment branch = root.mutableView();
		ShulkerBoxActionUpdate update = update(true, 1);

		update.applyTo(branch);

		assertTrue(root.shulkerBoxes().isEmpty());
		assertEquals(0.0F, branch.shulkerBoxes().get(POSITION).progress(), 0.0F);

		branch.commitTo(root);
		assertEquals(branch.shulkerBoxes(), root.shulkerBoxes());
	}

	@Test
	void closeActionPreservesTheBranchesCurrentProgress() {
		MockSimulationEnvironment environment = new MockSimulationEnvironment();
		ShulkerBox partiallyOpen = ShulkerBox.opening(Direction.UP).tick().tick();
		environment.setShulkerBoxes(Collections.singletonMap(POSITION, partiallyOpen));

		update(false, 2).applyTo(environment);

		ShulkerBox closing = environment.shulkerBoxes().get(POSITION);
		assertFalse(closing.opening());
		assertEquals(partiallyOpen.progress(), closing.progress(), 0.0F);
	}

	private static ShulkerBoxActionUpdate update(boolean opening, long sequence) {
		return new ShulkerBoxActionUpdate(
			POSITION,
			Direction.UP,
			opening,
			new CausalConstraint(0, 0, sequence)
		);
	}
}
