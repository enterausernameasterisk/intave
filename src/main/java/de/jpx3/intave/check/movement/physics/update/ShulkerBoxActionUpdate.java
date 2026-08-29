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

package de.jpx3.intave.check.movement.physics.update;

import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ShulkerBoxActionUpdate extends TickAmbiguousUpdate {
	private final BlockPosition position;
	private final Direction direction;
	private final boolean opening;
	private CausalConstraint causalConstraint;

	ShulkerBoxActionUpdate(
		BlockPosition position,
		Direction direction,
		boolean opening,
		CausalConstraint causalConstraint
	) {
		this.position = Objects.requireNonNull(position, "position");
		this.direction = Objects.requireNonNull(direction, "direction");
		this.opening = opening;
		this.causalConstraint = Objects.requireNonNull(causalConstraint, "causalConstraint");
	}

	@Override
	public void applyTo(SimulationEnvironment environment) {
		Map<BlockPosition, ShulkerBox> boxes = new LinkedHashMap<>(environment.shulkerBoxes());
		ShulkerBox current = boxes.get(position);
		if (current == null) {
			current = opening ? ShulkerBox.opening(direction) : ShulkerBox.closing(direction);
		} else {
			current = opening ? current.open() : current.close();
		}
		boxes.put(position, current);
		environment.setShulkerBoxes(boxes);
	}

	@Override
	public CausalConstraint constraint() {
		return causalConstraint;
	}

	public void canNotRunAfterThisTick(SimulationEnvironment environment) {
		causalConstraint = causalConstraint.notAfter(environment.currentTick());
	}

	public void setRunNotAfter(long notAfter) {
		causalConstraint = causalConstraint.notAfter(notAfter);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof ShulkerBoxActionUpdate)) {
			return false;
		}
		ShulkerBoxActionUpdate other = (ShulkerBoxActionUpdate) object;
		return opening == other.opening
			&& position.equals(other.position)
			&& direction == other.direction
			&& causalConstraint.equals(other.causalConstraint);
	}

	@Override
	public int hashCode() {
		return Objects.hash(position, direction, opening, causalConstraint);
	}

	@Override
	public String toString() {
		return "ShulkerBoxActionUpdate{position=" + position
			+ ", direction=" + direction
			+ ", opening=" + opening
			+ ", at=" + causalConstraint + '}';
	}

	public static ShulkerBoxActionUpdate openEnded(
		BlockPosition position,
		Direction direction,
		boolean opening,
		MovementMetadata metadata
	) {
		return new ShulkerBoxActionUpdate(
			position,
			direction,
			opening,
			CausalConstraint.openEnded(metadata.currentTick(), metadata.newSequenceNumber())
		);
	}
}
