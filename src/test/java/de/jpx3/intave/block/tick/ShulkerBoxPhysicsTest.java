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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.share.*;
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

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class ShulkerBoxPhysicsTest {
	@BeforeAll
	static void setUpVersion() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER26_1_1);
	}

	@Test
	void openingPushMovesThePlayerAfterTheirOwnTick() {
		User user = createUserWithEmptyBlockCache();
		MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
		Position initialPosition = new Position(0.5D, 1.05D, 0.5D);
		environment.setPosition(initialPosition);
		environment.setShulkerBoxes(Collections.singletonMap(
			new BlockPosition(0, 0, 0), ShulkerBox.opening(Direction.UP)
		));

		Motion inputMotion = new Motion(0.2D, -0.08D, 0.3D);
		Motion outputMotion = ShulkerBoxPhysics.applyAfterPlayerTick(
			user, environment, initialPosition, inputMotion
		);

		assertEquals(inputMotion, outputMotion);
		assertEquals(1.05D + (double) 0.1F + 0.01D, environment.positionY(), 0.0D);
		assertEquals(0.1F, environment.shulkerBoxAt(0, 0, 0).progress(), 0.0F);
	}

	@Test
	void animationUsesTheClientsFloatProgression() {
		ShulkerBox state = ShulkerBox.opening(Direction.UP);

		state = state.tick();
		assertEquals(0.1F, state.progress(), 0.0F);

		for (int i = 1; i < 10; i++) {
			state = state.tick();
		}
		assertEquals(1.0F, state.progress(), 0.0F);
		assertFalse(state.shouldTick());

		state = state.close().tick();
		assertEquals(0.9F, state.progress(), 0.0F);
	}

	@Test
	void modernOpeningSweepUsesTheProgressDeltaAndClientEpsilon() {
		BoundingBox area = ShulkerBoxPhysics.movementArea(
			new BlockPosition(10, 20, 30), Direction.EAST, 0.0F, 0.1F
		);

		assertEquals(11.0D, area.minX, 0.0D);
		assertEquals(11.0D + (double) 0.1F, area.maxX, 0.0D);
		assertEquals(
			new Motion((double) 0.1F + 0.01D, 0.0D, 0.0D),
			ShulkerBoxPhysics.requestedMovement(Direction.EAST, area)
		);
	}

	@Test
	void contactAtTheOuterSweepBoundaryIsNotAnIntersection() {
		BoundingBox area = ShulkerBoxPhysics.movementArea(
			new BlockPosition(0, 0, 0), Direction.UP, 0.0F, 0.1F
		);
		BoundingBox touching = BoundingBox.fromBounds(
			0.2D, area.maxY, 0.2D,
			0.8D, area.maxY + 1.8D, 0.8D
		);

		assertFalse(area.intersectsWith(touching));
	}

	@Test
	void modernClientsPushOnlyWhileOpening() {
		assertTrue(ShulkerBoxPhysics.modernMovesCollidedEntities(
			VER_1_17, ShulkerBox.opening(Direction.NORTH)
		));
		assertFalse(ShulkerBoxPhysics.modernMovesCollidedEntities(
			VER_1_17, ShulkerBox.closing(Direction.NORTH)
		));
		assertFalse(ShulkerBoxPhysics.modernMovesCollidedEntities(
			VER_1_17 - 1, ShulkerBox.opening(Direction.NORTH)
		));
	}

	@Test
	void legacyClientsPushWithTheCurrentLidWhileOpeningAndClosing() {
		ShulkerBox opening = ShulkerBox.opening(Direction.EAST);
		ShulkerBox closing = ShulkerBox.closing(Direction.EAST);
		assertTrue(ShulkerBoxPhysics.legacyMovesCollidedEntities(
			VER_1_16, opening, opening.tick()
		));
		assertTrue(ShulkerBoxPhysics.legacyMovesCollidedEntities(
			VER_1_16, closing, closing.tick()
		));

		BoundingBox area = ShulkerBoxPhysics.legacyMovementArea(
			new BlockPosition(0, 0, 0), Direction.EAST, 0.1F
		);
		BoundingBox player = BoundingBox.fromBounds(
			1.02D, 0.0D, 0.2D,
			1.62D, 1.8D, 0.8D
		);
		assertEquals(
			new Motion((double) (0.5F * 0.1F) - 0.02D + 0.01D, 0.0D, 0.0D),
			ShulkerBoxPhysics.legacyRequestedMovement(Direction.EAST, area, player)
		);
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
						return new Location(world, 0.5D, 1.05D, 0.5D);
					case "getUniqueId":
						return playerId;
					default:
						return null;
				}
			}
		);
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		return UserFactory.createTestUserFor(player, (user, key) -> {
			if ("protocolVersion".equals(key)) {
				return VER_1_21_11;
			}
			return "blockCache".equals(key) ? blockCache : null;
		});
	}
}
