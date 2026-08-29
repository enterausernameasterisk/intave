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

package de.jpx3.intave.block.tick;

import de.jpx3.intave.block.tick.piston.PistonSlimePhysics;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

/**
 * Simulates client block entities after the client has ticked its entities.
 */
public final class BlockTickEntities {
	private BlockTickEntities() {
	}

	public static Motion tick(
		User user,
		SimulationEnvironment environment,
		Position position,
		Motion motion
	) {
		Motion result = PistonSlimePhysics.applyAfterPlayerTick(
			user, environment, position, motion
		);
		return ShulkerBoxPhysics.applyAfterPlayerTick(
			user, environment, environment.position(), result
		);
	}
}
