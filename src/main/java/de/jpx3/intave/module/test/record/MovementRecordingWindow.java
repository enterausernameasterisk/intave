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

import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import org.bukkit.Material;

import java.util.*;

/**
 * Rebuilds a frame window as a standalone movement recording.
 *
 * <p>Movement frames contain block deltas and may omit unchanged position or rotation values. A
 * usable window therefore needs an effective block snapshot and absolute position and rotation in
 * its first frame. Tick-based actions also need to be clipped and rebased to the new frame zero.
 */
public final class MovementRecordingWindow {
	// Live recordings rasterize the player box grown by two blocks. Sixteen blocks is deliberately
	// conservative while still preventing old world positions from surviving every rotation.
	private static final double BLOCK_CONTEXT_RADIUS = 16;

	private MovementRecordingWindow() {
	}

	public static MovementRecording tail(MovementRecording source, int frameCount) {
		if (frameCount < 0) {
			throw new IllegalArgumentException("frameCount cannot be negative");
		}
		List<MoveFrame> sourceFrames = source.frames();
		int fromInclusive = Math.max(0, sourceFrames.size() - frameCount);
		int toExclusive = sourceFrames.size();

		Map<BlockPosition, MaterialVariantStore> effectiveBlocks = new HashMap<>();
		Position effectivePosition = null;
		Rotation effectiveRotation = null;
		List<MoveFrame> frames = new ArrayList<>(toExclusive - fromInclusive);
		List<Map<String, Attribute>> attributes = new ArrayList<>(toExclusive - fromInclusive);
		List<Position> windowPositions = new ArrayList<>(toExclusive - fromInclusive);

		for (int index = 0; index < toExclusive; index++) {
			MoveFrame frame = sourceFrames.get(index);
			effectiveBlocks.putAll(frame.blocks());
			if (frame.moveTo() != null) {
				effectivePosition = copy(frame.moveTo());
			}
			if (frame.rotateTo() != null) {
				effectiveRotation = copy(frame.rotateTo());
			}
			if (index < fromInclusive) {
				continue;
			}

			Map<BlockPosition, MaterialVariantStore> frameBlocks = index == fromInclusive ? new HashMap<>(effectiveBlocks) : new HashMap<>(frame.blocks());
			Position position = index == fromInclusive ? copy(effectivePosition) : copy(frame.moveTo());
			Rotation rotation = index == fromInclusive ? copy(effectiveRotation) : copy(frame.rotateTo());
			frames.add(new MoveFrame(position, rotation, frameBlocks, frame.input(), frame.gliding(), frame.physicalPose(), frame.movementState()));
			attributes.add(source.attributesForFrame(index));
			if (effectivePosition != null) {
				windowPositions.add(copy(effectivePosition));
			}
		}
		if (!frames.isEmpty()) {
			retainBlocksNear(frames.get(0).blocks(), windowPositions);
		}
		Map<BlockPosition, MaterialVariantStore> retainedFinalBlocks = new HashMap<>(effectiveBlocks);
		retainBlocksNear(retainedFinalBlocks, windowPositions);
		Map<Material, Set<Integer>> referencedVariants = referencedVariants(frames);

		List<Action> actions = rebaseActions(source.actions(), fromInclusive, toExclusive);
		MovementRecording window = new MovementRecording(UUID.randomUUID(), source.clientProtocolVersion(), source.serverVersion(), frames, attributes, actions, copyNestedMap(source.collisionShapes(), referencedVariants), copyNestedMap(source.fluids(), referencedVariants), copyBlockVariants(source.blockVariants(), referencedVariants));
		window.seedBlocks(retainedFinalBlocks);
		window.inheritVelocities(source, fromInclusive);
		return window;
	}

	private static void retainBlocksNear(Map<BlockPosition, MaterialVariantStore> blocks, List<Position> positions) {
		if (positions.isEmpty()) {
			return;
		}
		blocks.entrySet().removeIf(entry -> positions.stream().noneMatch(position -> {
			BlockPosition block = entry.getKey();
			return Math.abs(block.getBlockX() - position.getX()) <= BLOCK_CONTEXT_RADIUS && Math.abs(block.getBlockY() - position.getY()) <= BLOCK_CONTEXT_RADIUS && Math.abs(block.getBlockZ() - position.getZ()) <= BLOCK_CONTEXT_RADIUS;
		}));
	}

	private static Map<Material, Set<Integer>> referencedVariants(List<MoveFrame> frames) {
		Map<Material, Set<Integer>> referenced = new HashMap<>();
		for (MoveFrame frame : frames) {
			for (MaterialVariantStore block : frame.blocks().values()) {
				referenced.computeIfAbsent(block.type(), key -> new HashSet<>()).add(block.variantIndex());
			}
		}
		return referenced;
	}

	private static List<Action> rebaseActions(List<Action> source, int fromInclusive, int toExclusive) {
		List<Action> actions = new ArrayList<>();
		for (Action action : source) {
			if (!(action instanceof ReceiveVelocity)) {
				throw new IllegalStateException("Unsupported movement recording action " + action.type());
			}
			ReceiveVelocity velocity = (ReceiveVelocity) action;
			TickRange range = velocity.tickRange();
			long clippedStart = Math.max(range.start(), fromInclusive);
			long clippedEnd = Math.min(range.end(), toExclusive);
			if (clippedStart >= clippedEnd) {
				continue;
			}
			actions.add(new ReceiveVelocity(velocity.motion().copy(), TickRange.betweenExclusive(clippedStart - fromInclusive, clippedEnd - fromInclusive)));
		}
		return actions;
	}

	private static Position copy(Position position) {
		return position == null ? null : new Position(position.getX(), position.getY(), position.getZ());
	}

	private static Rotation copy(Rotation rotation) {
		return rotation == null ? null : new Rotation(rotation.yaw(), rotation.pitch());
	}

	private static <T> Map<Material, Map<Integer, T>> copyNestedMap(Map<Material, Map<Integer, T>> source, Map<Material, Set<Integer>> referencedVariants) {
		Map<Material, Map<Integer, T>> copy = new LinkedHashMap<>();
		for (Map.Entry<Material, Map<Integer, T>> entry : source.entrySet()) {
			Set<Integer> referenced = referencedVariants.get(entry.getKey());
			if (referenced == null) {
				continue;
			}
			Map<Integer, T> values = new LinkedHashMap<>();
			for (Map.Entry<Integer, T> value : entry.getValue().entrySet()) {
				if (referenced.contains(value.getKey())) {
					values.put(value.getKey(), value.getValue());
				}
			}
			if (!values.isEmpty()) {
				copy.put(entry.getKey(), values);
			}
		}
		return copy;
	}

	private static Map<Material, Map<Integer, BlockVariant>> copyBlockVariants(Map<Material, Map<Integer, BlockVariant>> source, Map<Material, Set<Integer>> referencedVariants) {
		Map<Material, Map<Integer, BlockVariant>> copy = new LinkedHashMap<>();
		for (Map.Entry<Material, Map<Integer, BlockVariant>> entry : source.entrySet()) {
			Set<Integer> referenced = referencedVariants.get(entry.getKey());
			if (referenced == null) {
				continue;
			}
			Map<Integer, BlockVariant> variants = new LinkedHashMap<>();
			for (Map.Entry<Integer, BlockVariant> variant : entry.getValue().entrySet()) {
				if (referenced.contains(variant.getKey())) {
					variants.put(variant.getKey(), variant.getValue().copy());
				}
			}
			if (!variants.isEmpty()) {
				copy.put(entry.getKey(), variants);
			}
		}
		return copy;
	}
}
