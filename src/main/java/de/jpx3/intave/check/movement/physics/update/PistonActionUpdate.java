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

import de.jpx3.intave.block.tick.piston.PistonSlimeMovement;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PistonActionUpdate extends TickAmbiguousUpdate {
	private final Direction direction;
	private final List<BlockPosition> slimeSources;
	private CausalConstraint causalConstraint;

	PistonActionUpdate(
		Direction direction,
		List<BlockPosition> slimeSources,
		CausalConstraint causalConstraint
	) {
		this.direction = Objects.requireNonNull(direction, "direction");
		this.slimeSources = new ArrayList<>(slimeSources);
		this.causalConstraint = causalConstraint;
	}

	@Override
	public void applyTo(SimulationEnvironment environment) {
		// The player sends this tick's movement before block entities tick. Queue
		// progress zero now; the first piston push runs in the after-player phase.
		List<PistonSlimeMovement> movements = new ArrayList<>(environment.pistonSlimeMovements());
		movements.add(new PistonSlimeMovement(
			direction, slimeSources, environment.currentTick()
		));
		environment.setPistonSlimeMovements(movements);
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
		if (!(object instanceof PistonActionUpdate)) {
			return false;
		}
		PistonActionUpdate other = (PistonActionUpdate) object;
		return direction == other.direction
			&& slimeSources.equals(other.slimeSources)
			&& causalConstraint.equals(other.causalConstraint);
	}

	@Override
	public int hashCode() {
		return Objects.hash(direction, slimeSources, causalConstraint);
	}

	@Override
	public String toString() {
		return "PistonActionUpdate{direction=" + direction
			+ ", slimeSources=" + slimeSources
			+ ", at=" + causalConstraint + "}";
	}

	public static PistonActionUpdate openEnded(
		Direction direction,
		List<BlockPosition> slimeSources,
		MovementMetadata metadata
	) {
		return new PistonActionUpdate(
			direction,
			slimeSources,
			CausalConstraint.openEnded(metadata.currentTick(), metadata.newSequenceNumber())
		);
	}
}
