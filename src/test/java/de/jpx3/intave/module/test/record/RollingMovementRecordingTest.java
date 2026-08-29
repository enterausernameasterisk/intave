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

package de.jpx3.intave.module.test.record;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.share.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

final class RollingMovementRecordingTest {
	private final MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();

	@BeforeEach
	void setServerVersion() {
		de.jpx3.intave.adapter.MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
	}

	@Test
	void rotatesBeforeTheFrameAfterTheLimit() {
		RollingMovementRecording rolling = rolling(3, 1, 3);
		insert(rolling, 0);
		insert(rolling, 1);
		insert(rolling, 2);

		assertEquals(3, rolling.activeFrameCount());
		assertEquals(3, rolling.framesSinceReset());

		insert(rolling, 3);

		assertEquals(2, rolling.activeFrameCount());
		assertEquals(1, rolling.framesSinceReset());
		assertEquals(new Position(2, 64, 0), rolling.activeRecording().frames().get(0).moveTo());
		assertEquals(new Position(3, 64, 0), rolling.activeRecording().frames().get(1).moveTo());
	}

	@Test
	void snapshotResetsWithOverlapAndAppliesMovementFrameCooldown() {
		RollingMovementRecording rolling = rolling(3, 1, 3);
		insert(rolling, 0);
		insert(rolling, 1);

		MovementRecording first = rolling.snapshotAndReset();

		assertNotNull(first);
		assertEquals(2, first.frameCount());
		assertEquals(1, rolling.activeFrameCount());
		assertEquals(3, rolling.cooldownFramesRemaining());
		insert(rolling, 2);
		assertNull(rolling.snapshotAndReset());
		insert(rolling, 3);
		assertNull(rolling.snapshotAndReset());
		insert(rolling, 4);

		MovementRecording second = rolling.snapshotAndReset();

		assertNotNull(second);
		assertEquals(4, second.frameCount());
		assertEquals(new Position(1, 64, 0), second.frames().get(0).moveTo());
	}

	@Test
	void pendingVelocityIsClippedIntoBothSidesOfAReset() {
		RollingMovementRecording rolling = rolling(100, 1, 0);
		insert(rolling, 0);
		MovementRecording.VelocityToken velocity = rolling.applyToActive(
			recording -> recording.beginVelocity(new Motion(1, 2, 3))
		);
		insert(rolling, 1);

		MovementRecording first = rolling.snapshotAndReset();

		assertNotNull(first);
		assertEquals(1, first.actions().size());
		assertEquals(
			TickRange.betweenExclusive(1, 2),
			((ReceiveVelocity) first.actions().get(0)).tickRange()
		);

		insert(rolling, 2);
		rolling.acceptOnActive(recording -> recording.completeVelocity(velocity));
		insert(rolling, 3);
		MovementRecording second = rolling.snapshotAndReset();

		assertNotNull(second);
		assertEquals(1, second.actions().size());
		ReceiveVelocity rebased = (ReceiveVelocity) second.actions().get(0);
		assertEquals(TickRange.betweenExclusive(0, 3), rebased.tickRange());
		assertEquals(new Motion(1, 2, 3), rebased.motion());
	}

	@Test
	void resetDropsFramesCooldownAndVelocityHistory() {
		RollingMovementRecording rolling = rolling(100, 20, 100);
		insert(rolling, 0);
		MovementRecording.VelocityToken staleVelocity = rolling.applyToActive(
			recording -> recording.beginVelocity(new Motion(1, 0, 0))
		);
		assertNotNull(rolling.snapshotAndReset());

		rolling.reset();
		MovementRecording.VelocityToken currentVelocity = rolling.applyToActive(
			recording -> recording.beginVelocity(new Motion(2, 0, 0))
		);
		rolling.acceptOnActive(recording -> recording.completeVelocity(staleVelocity));
		insert(rolling, 1);
		rolling.acceptOnActive(recording -> recording.completeVelocity(currentVelocity));
		MovementRecording current = rolling.snapshotAndReset();

		assertNotNull(current);
		assertEquals(1, current.actions().size());
		assertEquals(
			new Motion(2, 0, 0),
			((ReceiveVelocity) current.actions().get(0)).motion()
		);
		assertEquals(1, rolling.activeFrameCount());
		assertEquals(0, rolling.framesSinceReset());
		assertEquals(100, rolling.cooldownFramesRemaining());
		assertFalse(rolling.needsPositionSeed());
		assertFalse(rolling.needsRotationSeed());
		assertTrue(rolling.uploadOnCooldown());
	}

	private RollingMovementRecording rolling(int limit, int overlap, int cooldown) {
		return new RollingMovementRecording(
			47, MinecraftVersions.VER1_21_4, limit, overlap, cooldown
		);
	}

	private void insert(RollingMovementRecording rolling, double x) {
		rolling.insertFrame(
			BoundingBox.empty(),
			Input.none(),
			new Position(x, 64, 0),
			Rotation.zero(),
			blockCache,
			Collections.emptyMap(),
			false,
			null
		);
	}
}
