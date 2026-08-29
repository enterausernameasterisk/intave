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

package de.jpx3.intave.block.physics;

import org.junit.jupiter.api.Test;

import static org.bukkit.Material.MOVING_PISTON;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class BlockPropertiesTest {
	@Test
	void movingPistonDoesNotSuffocate() {
		assertFalse(BlockProperties.of(MOVING_PISTON).suffocates());
	}
}
