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

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;

import java.util.Objects;

/**
 * Branch-local client animation state for one shulker-box block entity.
 */
public final class ShulkerBox {
	private static final BoundingBox FULL_BLOCK = BoundingBox.originFrom(0, 0, 0, 1, 1, 1);

	private final boolean opening;
	private final Direction direction;
	private final float progress;

	private ShulkerBox(boolean opening, Direction direction, float progress) {
		this.opening = opening;
		this.direction = Objects.requireNonNull(direction, "direction");
		this.progress = progress;
	}

	public ShulkerBox open() {
		return opening ? this : new ShulkerBox(true, direction, progress);
	}

	public ShulkerBox close() {
		return opening ? new ShulkerBox(false, direction, progress) : this;
	}

	public boolean shouldTick() {
		return opening ? progress < 1.0F : progress > 0.0F;
	}

	public boolean complete() {
		return !opening && progress <= 0.0F;
	}

	public ShulkerBox tick() {
		if (!shouldTick()) {
			return this;
		}
		float nextProgress = progressAfterTick();
		if (nextProgress >= 1.0F) {
			nextProgress = 1.0F;
		} else if (nextProgress <= 0.0F) {
			nextProgress = 0.0F;
		}
		return new ShulkerBox(opening, direction, nextProgress);
	}

	float progressAfterTick() {
		return opening ? progress + 0.1F : progress - 0.1F;
	}

	ShulkerBox withProgress(float newProgress) {
		return new ShulkerBox(opening, direction, newProgress);
	}

	public BlockShape originShape() {
		float expansion = 0.5F * progress;
		return FULL_BLOCK.expand(
			expansion * direction.offsetX(),
			expansion * direction.offsetY(),
			expansion * direction.offsetZ()
		);
	}

	public boolean opening() {
		return opening;
	}

	public Direction direction() {
		return direction;
	}

	public float progress() {
		return progress;
	}

	public static ShulkerBox opening(Direction direction) {
		return new ShulkerBox(true, direction, 0.0F);
	}

	public static ShulkerBox closing(Direction direction) {
		return new ShulkerBox(false, direction, 1.0F);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof ShulkerBox)) {
			return false;
		}
		ShulkerBox other = (ShulkerBox) object;
		return opening == other.opening
			&& Float.compare(progress, other.progress) == 0
			&& direction == other.direction;
	}

	@Override
	public int hashCode() {
		return Objects.hash(opening, direction, progress);
	}

	@Override
	public String toString() {
		return "ShulkerBox{opening=" + opening
			+ ", direction=" + direction
			+ ", progress=" + progress + '}';
	}
}
