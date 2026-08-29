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

package de.jpx3.intave.block.tick.piston;

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The client-side movement of the slime blocks created by one piston action.
 * A piston block entity advances by half a block on each of its two ticks.
 */
public final class PistonSlimeMovement {
	private static final double PROGRESS_PER_TICK = 0.5;
	private static final long DURATION_TICKS = 2;

	private final Direction direction;
	private final List<BlockPosition> slimeSources;
	private final long startTick;

	public PistonSlimeMovement(
		Direction direction,
		List<BlockPosition> slimeSources,
		long startTick
	) {
		this.direction = Objects.requireNonNull(direction, "direction");
		this.slimeSources = Collections.unmodifiableList(new ArrayList<>(slimeSources));
		this.startTick = startTick;
	}

	public Motion apply(long currentTick, BoundingBox entityBox, Motion motion) {
		if (!activeAt(currentTick)) {
			return motion;
		}

		for (BlockPosition source : slimeSources) {
			if (movementQueryArea(source, currentTick).intersectsWith(entityBox)) {
				Motion result = motion.copy();
				overwriteDirectionAxis(result);
				return result;
			}
		}
		return motion;
	}

	public boolean expired(long currentTick) {
		return currentTick - startTick >= DURATION_TICKS;
	}

	public Direction direction() {
		return direction;
	}

	public List<BlockPosition> slimeSources() {
		return slimeSources;
	}

	public long startTick() {
		return startTick;
	}

	public boolean activeAt(long currentTick) {
		long age = currentTick - startTick;
		return age >= 0 && age < DURATION_TICKS;
	}

	BoundingBox movementQueryArea(BlockPosition source, long currentTick) {
		BoundingBox currentPosition = collisionBoxAt(source, currentTick);
		return currentPosition.expand(
			direction.normalX() * PROGRESS_PER_TICK,
			direction.normalY() * PROGRESS_PER_TICK,
			direction.normalZ() * PROGRESS_PER_TICK
		);
	}

	double pushDistance(BlockPosition source, long currentTick, BoundingBox entityBox, boolean capToProgress) {
		BoundingBox movementArea = leadingMovementArea(collisionBoxAt(source, currentTick));
		if (!movementArea.intersectsWith(entityBox)) {
			return 0.0;
		}

		double overlap;
		switch (direction) {
			case EAST:
				overlap = movementArea.maxX - entityBox.minX;
				break;
			case WEST:
				overlap = entityBox.maxX - movementArea.minX;
				break;
			case UP:
				overlap = movementArea.maxY - entityBox.minY;
				break;
			case DOWN:
				overlap = entityBox.maxY - movementArea.minY;
				break;
			case SOUTH:
				overlap = movementArea.maxZ - entityBox.minZ;
				break;
			case NORTH:
				overlap = entityBox.maxZ - movementArea.minZ;
				break;
			default:
				throw new IllegalStateException("Unsupported piston direction " + direction);
		}
		if (overlap <= 0.0) {
			return 0.0;
		}
		return (capToProgress ? Math.min(overlap, PROGRESS_PER_TICK) : overlap) + 0.01D;
	}

	void overwriteDirectionAxis(Motion motion) {
		switch (direction.axis()) {
			case X_AXIS:
				motion.setMotionX(direction.normalX());
				break;
			case Y_AXIS:
				motion.setMotionY(direction.normalY());
				break;
			case Z_AXIS:
				motion.setMotionZ(direction.normalZ());
				break;
		}
	}

	public BoundingBox collisionBoxAt(BlockPosition source, long currentTick) {
		double progress = (currentTick - startTick) * PROGRESS_PER_TICK;
		double offsetX = direction.normalX() * progress;
		double offsetY = direction.normalY() * progress;
		double offsetZ = direction.normalZ() * progress;
		return BoundingBox.fromBounds(
			source.getX() + offsetX,
			source.getY() + offsetY,
			source.getZ() + offsetZ,
			source.getX() + offsetX + 1.0,
			source.getY() + offsetY + 1.0,
			source.getZ() + offsetZ + 1.0
		);
	}

	private BoundingBox leadingMovementArea(BoundingBox currentPosition) {
		switch (direction) {
			case EAST:
				return BoundingBox.fromBounds(
					currentPosition.maxX, currentPosition.minY, currentPosition.minZ,
					currentPosition.maxX + PROGRESS_PER_TICK, currentPosition.maxY, currentPosition.maxZ
				);
			case WEST:
				return BoundingBox.fromBounds(
					currentPosition.minX - PROGRESS_PER_TICK, currentPosition.minY, currentPosition.minZ,
					currentPosition.minX, currentPosition.maxY, currentPosition.maxZ
				);
			case UP:
				return BoundingBox.fromBounds(
					currentPosition.minX, currentPosition.maxY, currentPosition.minZ,
					currentPosition.maxX, currentPosition.maxY + PROGRESS_PER_TICK, currentPosition.maxZ
				);
			case DOWN:
				return BoundingBox.fromBounds(
					currentPosition.minX, currentPosition.minY - PROGRESS_PER_TICK, currentPosition.minZ,
					currentPosition.maxX, currentPosition.minY, currentPosition.maxZ
				);
			case SOUTH:
				return BoundingBox.fromBounds(
					currentPosition.minX, currentPosition.minY, currentPosition.maxZ,
					currentPosition.maxX, currentPosition.maxY, currentPosition.maxZ + PROGRESS_PER_TICK
				);
			case NORTH:
				return BoundingBox.fromBounds(
					currentPosition.minX, currentPosition.minY, currentPosition.minZ - PROGRESS_PER_TICK,
					currentPosition.maxX, currentPosition.maxY, currentPosition.minZ
				);
			default:
				throw new IllegalStateException("Unsupported piston direction " + direction);
		}
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof PistonSlimeMovement)) {
			return false;
		}
		PistonSlimeMovement other = (PistonSlimeMovement) object;
		return startTick == other.startTick
			&& direction == other.direction
			&& slimeSources.equals(other.slimeSources);
	}

	@Override
	public int hashCode() {
		return Objects.hash(direction, slimeSources, startTick);
	}

	@Override
	public String toString() {
		return "PistonSlimeMovement{direction=" + direction
			+ ", slimeSources=" + slimeSources
			+ ", startTick=" + startTick + "}";
	}
}
