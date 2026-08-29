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
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class MovementRecordingWindowTest {
	@Test
	void tailSeedsStateAndRebasesActions() {
		BlockPosition firstBlock = new BlockPosition(0, 64, 0);
		BlockPosition secondBlock = new BlockPosition(1, 64, 0);
		List<MoveFrame> frames = new ArrayList<>();
		frames.add(frame(
			new Position(0, 64, 0), new Rotation(10, 20),
			blocks(firstBlock, MaterialVariantStore.of(Material.STONE, 0))
		));
		Map<BlockPosition, MaterialVariantStore> secondDelta =
			blocks(firstBlock, MaterialVariantStore.air());
		secondDelta.put(secondBlock, MaterialVariantStore.of(Material.STONE, 0));
		frames.add(frame(null, null, secondDelta));
		frames.add(frame(new Position(2, 64, 0), null, Collections.emptyMap()));
		frames.add(frame(
			null, new Rotation(40, 50),
			blocks(secondBlock, MaterialVariantStore.air())
		));

		List<Action> actions = new ArrayList<>();
		actions.add(new ReceiveVelocity(
			new Motion(1, 2, 3), TickRange.betweenExclusive(1, 4)
		));
		actions.add(new ReceiveVelocity(
			new Motion(4, 5, 6), TickRange.betweenExclusive(0, 1)
		));
		MovementRecording source = recording(frames, actions);

		MovementRecording tail = MovementRecordingWindow.tail(source, 2);

		assertNotEquals(source.internalId(), tail.internalId());
		assertEquals(source.clientProtocolVersion(), tail.clientProtocolVersion());
		assertEquals(source.serverVersion(), tail.serverVersion());
		assertEquals(2, tail.frameCount());
		MoveFrame first = tail.frames().get(0);
		assertEquals(new Position(2, 64, 0), first.moveTo());
		assertEquals(new Rotation(10, 20), first.rotateTo());
		assertEquals(MaterialVariantStore.air(), first.blocks().get(firstBlock));
		assertEquals(
			MaterialVariantStore.of(Material.STONE, 0),
			first.blocks().get(secondBlock)
		);
		assertEquals(new Rotation(40, 50), tail.frames().get(1).rotateTo());
		assertEquals(MaterialVariantStore.air(), tail.frames().get(1).blocks().get(secondBlock));

		assertEquals(1, tail.actions().size());
		ReceiveVelocity velocity = (ReceiveVelocity) tail.actions().get(0);
		assertEquals(new Motion(1, 2, 3), velocity.motion());
		assertEquals(TickRange.betweenExclusive(0, 2), velocity.tickRange());
	}

	@Test
	void compressedWindowRoundTrips() throws IOException {
		MovementRecording source = recording(Collections.singletonList(frame(
			new Position(1, 2, 3), new Rotation(4, 5), Collections.emptyMap()
		)), Collections.emptyList());
		MovementRecording window = MovementRecordingWindow.tail(source, 20);
		Resource resource = Resources.memoryResource();
		resource.write(compressedBytes(window));

		MovementRecording decoded = MovementRecording.loadFrom(resource);

		assertEquals(window, decoded);
		assertTrue(decoded.actions().isEmpty());
	}

	@Test
	void tailCarriesMovementFrameState() {
		MovementFrameState state = frameState(2);
		List<MoveFrame> frames = List.of(
			frame(new Position(0, 64, 0), Rotation.zero(), Collections.emptyMap()),
			frame(new Position(1, 64, 0), Rotation.zero(), Collections.emptyMap(), state)
		);
		MovementRecording source = recording(frames, Collections.emptyList());

		MovementRecording tail = MovementRecordingWindow.tail(source, 1);

		assertEquals(state, tail.frames().get(0).movementState());
		assertEquals(2, tail.frames().get(0).movementState().reduceTicks());
	}

	@Test
	void seededSolidBlockProducesOneRemovalDelta() {
		BlockPosition block = new BlockPosition(0, 0, 0);
		MovementRecording tail = MovementRecordingWindow.tail(recording(List.of(frame(
			new Position(0, 0, 0), Rotation.zero(),
			blocks(block, MaterialVariantStore.of(Material.STONE, 0))
		)), Collections.emptyList()), 1);
		MockFullBlockStaticPlane emptyWorld = new MockFullBlockStaticPlane();

		tail.insertFrame(
			BoundingBox.empty(), Input.none(), new Position(0, 0, 0), Rotation.zero(), emptyWorld, false
		);
		tail.insertFrame(
			BoundingBox.empty(), Input.none(), new Position(0, 0, 0), Rotation.zero(), emptyWorld, false
		);

		assertEquals(MaterialVariantStore.air(), tail.frames().get(1).blocks().get(block));
		assertTrue(tail.frames().get(2).blocks().isEmpty());
	}


	private static byte[] compressedBytes(MovementRecording recording) throws IOException {
		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DeflaterOutputStream compressed = new DeflaterOutputStream(bytes)) {
				buffer.readBytes(compressed, buffer.readableBytes());
			}
			return bytes.toByteArray();
		} finally {
			buffer.release();
		}
	}

	@Test
	void tailKeepsNearbyStateWithoutCarryingDistantHistory() {
		BlockPosition oldBlock = new BlockPosition(0, 64, 0);
		BlockPosition currentBlock = new BlockPosition(100, 64, 0);
		List<MoveFrame> frames = new ArrayList<>();
		frames.add(frame(
			new Position(0, 64, 0), Rotation.zero(),
			blocks(oldBlock, MaterialVariantStore.of(Material.STONE, 0))
		));
		frames.add(frame(
			new Position(100, 64, 0), null,
			blocks(currentBlock, MaterialVariantStore.of(Material.STONE, 0))
		));
		frames.add(frame(null, null, Collections.emptyMap()));

		MovementRecording tail = MovementRecordingWindow.tail(
			recording(frames, Collections.emptyList()), 1
		);

		assertEquals(1, tail.frameCount());
		assertEquals(
			MaterialVariantStore.of(Material.STONE, 0),
			tail.frames().get(0).blocks().get(currentBlock)
		);
		assertFalse(tail.frames().get(0).blocks().containsKey(oldBlock));
	}

	private static MovementRecording recording(List<MoveFrame> frames, List<Action> actions) {
		List<Map<String, Attribute>> attributes = new ArrayList<>();
		for (int index = 0; index < frames.size(); index++) {
			attributes.add(Collections.emptyMap());
		}
		return new MovementRecording(
			UUID.randomUUID(),
			47,
			MinecraftVersions.VER1_21_4,
			frames,
			attributes,
			actions,
			new HashMap<>(),
			new HashMap<>(),
			new HashMap<>()
		);
	}

	private static MovementFrameState frameState(int reduceTicks) {
		return new MovementFrameState(
			MovementFrameState.AbilityState.empty(),
			MovementFrameState.EffectState.empty(),
			MovementFrameState.InventoryState.empty(),
			1, 0, null, Collections.emptyList(),
			"ON_LAND", "ON_LAND", 0.0F, 0.0D,
			reduceTicks, 100, 100
		);
	}

	private static MoveFrame frame(
		Position position,
		Rotation rotation,
		Map<BlockPosition, MaterialVariantStore> blocks
	) {
		return new MoveFrame(position, rotation, blocks, Input.none(), false, null);
	}

	private static MoveFrame frame(
		Position position,
		Rotation rotation,
		Map<BlockPosition, MaterialVariantStore> blocks,
		MovementFrameState movementState
	) {
		return new MoveFrame(
			position, rotation, blocks, Input.none(), false, null, movementState
		);
	}

	private static Map<BlockPosition, MaterialVariantStore> blocks(
		BlockPosition position,
		MaterialVariantStore block
	) {
		Map<BlockPosition, MaterialVariantStore> blocks = new HashMap<>();
		blocks.put(position, block);
		return blocks;
	}
}
