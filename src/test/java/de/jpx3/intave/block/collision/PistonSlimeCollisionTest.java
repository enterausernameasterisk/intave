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

package de.jpx3.intave.block.collision;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.tick.piston.PistonSlimeMovement;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static de.jpx3.intave.share.Direction.Axis.Y_AXIS;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PistonSlimeCollisionTest {
	@BeforeAll
	static void setUpVersion() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
	}

	@Test
	void progressZeroKeepsTheMovedSlimeAtItsSourceForPlayerCollision() {
		User user = createUserWithEmptyBlockCache();
		MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
		environment.setPistonSlimeMovements(Collections.singletonList(
			new PistonSlimeMovement(
				Direction.UP,
				Collections.singletonList(new BlockPosition(0, 0, 0)),
				0
			)
		));
		BoundingBox player = BoundingBox.fromBounds(0.2, 1.0, 0.2, 0.8, 2.8, 0.8);
		BlockShape collision = Collision.shape(
			user, environment, player.expand(0.0, -0.2, 0.0)
		);

		assertEquals(0.0, collision.allowedOffset(Y_AXIS, player, -0.2), 0.0);
	}

	private static User createUserWithEmptyBlockCache() {
		World world = FakeWorldFactory.createWorld(
			(methodName, arguments) -> "isChunkLoaded".equals(methodName) ? true : null
		);
		UUID playerId = UUID.randomUUID();
		Player player = FakePlayerFactory.createPlayer(
			(methodName, arguments) -> {
				switch (methodName) {
					case "getWorld":
						return world;
					case "getLocation":
						return new Location(world, 0.5, 1.0, 0.5);
					case "getUniqueId":
						return playerId;
					default:
						return null;
				}
			}
		);
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		return UserFactory.createTestUserFor(player, (user, key) ->
			"blockCache".equals(key) ? blockCache : null
		);
	}
}
