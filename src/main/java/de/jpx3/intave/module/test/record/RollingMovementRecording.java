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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A replay-safe segment and cooldown controller for one player's movement recordings.
 *
 * <p>Frame and action state belongs to {@link MovementRecording}; this class only rotates the live
 * recording and detaches upload snapshots.
 */
public final class RollingMovementRecording {
	private final int clientProtocolVersion;
	private final MinecraftVersion serverVersion;
	private final int segmentFrameLimit;
	private final int overlapFrameCount;
	private final int uploadCooldownFrames;

	private MovementRecording active;
	private int framesSinceReset;
	private int cooldownFramesRemaining;

	public RollingMovementRecording(
		int clientProtocolVersion,
		MinecraftVersion serverVersion,
		int segmentFrameLimit,
		int overlapFrameCount,
		int uploadCooldownFrames
	) {
		if (segmentFrameLimit <= 0) {
			throw new IllegalArgumentException("segmentFrameLimit must be positive");
		}
		if (overlapFrameCount < 0 || overlapFrameCount > segmentFrameLimit) {
			throw new IllegalArgumentException(
				"overlapFrameCount must be between zero and segmentFrameLimit"
			);
		}
		if (uploadCooldownFrames < 0) {
			throw new IllegalArgumentException("uploadCooldownFrames cannot be negative");
		}
		this.clientProtocolVersion = clientProtocolVersion;
		this.serverVersion = serverVersion;
		this.segmentFrameLimit = segmentFrameLimit;
		this.overlapFrameCount = overlapFrameCount;
		this.uploadCooldownFrames = uploadCooldownFrames;
		this.active = createRecording();
	}

	public synchronized void insertFrame(
		BoundingBox boundingBox,
		Input input,
		@Nullable Position position,
		@Nullable Rotation rotation,
		BlockCache blockCache,
		Map<String, Attribute> attributes,
		boolean gliding,
		@Nullable Pose physicalPose
	) {
		insertFrame(
			boundingBox, input, position, rotation, blockCache, attributes,
			gliding, physicalPose, null
		);
	}

	public synchronized void insertFrame(
		BoundingBox boundingBox,
		Input input,
		@Nullable Position position,
		@Nullable Rotation rotation,
		BlockCache blockCache,
		Map<String, Attribute> attributes,
		boolean gliding,
		@Nullable Pose physicalPose,
		@Nullable MovementFrameState frameState
	) {
		rotateAtLimit();
		if (frameState == null) {
			active.insertFrame(
				boundingBox, input, position, rotation, blockCache, attributes,
				gliding, physicalPose
			);
		} else {
			active.insertFrame(
				boundingBox, input, position, rotation, blockCache, attributes,
				gliding, physicalPose, frameState
			);
		}
		framesSinceReset++;
		if (cooldownFramesRemaining > 0) {
			cooldownFramesRemaining--;
		}
	}

	public synchronized boolean needsPositionSeed() {
		return !active.firstPositionHasBeenSent();
	}

	public synchronized boolean needsRotationSeed() {
		return !active.firstRotationHasBeenSent();
	}

	public synchronized <T> T applyToActive(Function<MovementRecording, T> operation) {
		return operation.apply(active);
	}

	public synchronized void acceptOnActive(Consumer<MovementRecording> operation) {
		operation.accept(active);
	}

	public synchronized boolean uploadOnCooldown() {
		return cooldownFramesRemaining > 0;
	}

	/**
	 * Freezes the current recording and starts a new segment from its replay-safe overlap.
	 *
	 * @return the frozen recording, or {@code null} when no frame is available or upload cooldown is
	 * active
	 */
	public synchronized @Nullable MovementRecording snapshotAndReset() {
		if (active.frameCount() == 0 || cooldownFramesRemaining > 0) {
			return null;
		}
		MovementRecording snapshot = active;
		// Migrate native intervals first so an acknowledgement can complete after this boundary.
		active = MovementRecordingWindow.tail(snapshot, overlapFrameCount);
		snapshot.materializeVelocities();
		framesSinceReset = 0;
		cooldownFramesRemaining = uploadCooldownFrames;
		return snapshot;
	}

	public synchronized void reset() {
		active = createRecording();
		framesSinceReset = 0;
		cooldownFramesRemaining = 0;
	}

	public synchronized int activeFrameCount() {
		return active.frameCount();
	}

	public synchronized int framesSinceReset() {
		return framesSinceReset;
	}

	public synchronized int cooldownFramesRemaining() {
		return cooldownFramesRemaining;
	}

	MovementRecording activeRecording() {
		return active;
	}

	private void rotateAtLimit() {
		if (framesSinceReset < segmentFrameLimit) {
			return;
		}
		active = MovementRecordingWindow.tail(active, overlapFrameCount);
		framesSinceReset = 0;
	}

	private MovementRecording createRecording() {
		return MovementRecording.create(clientProtocolVersion, serverVersion);
	}
}
