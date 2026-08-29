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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class PistonSlimePhysics {
	private PistonSlimePhysics() {
	}

	public static Motion applyAfterPlayerTick(
		User user,
		SimulationEnvironment environment,
		Position position,
		Motion motion
	) {
		List<PistonSlimeMovement> movements = environment.pistonSlimeMovements();
		if (movements.isEmpty() || user.protocolVersion() < VER_1_9) {
			return motion;
		}
		AppliedEffects effects = applyEffects(
			user, environment, movements, environment.currentTick(),
			position, motion, Motion.newEmpty()
		);
		if (!effects.position.equals(position)) {
			environment.setPosition(effects.position);
		}
		return effects.motion;
	}

	public static Motion apply(
		List<PistonSlimeMovement> movements,
		long currentTick,
		BoundingBox entityBox,
		Motion motion
	) {
		Motion result = motion;
		for (PistonSlimeMovement movement : movements) {
			result = movement.apply(currentTick, entityBox, result);
		}
		return result;
	}

	private static AppliedEffects applyEffects(
		User user,
		SimulationEnvironment environment,
		List<PistonSlimeMovement> movements,
		long pistonTick,
		Position initialPosition,
		Motion initialMotion,
		Motion initialRestriction
	) {
		Position position = initialPosition;
		Motion motion = initialMotion;
		Motion restriction = initialRestriction.copy();
		BoundingBox entityBox = entityBoxAt(user, environment, position, environment.boundingBox());
		boolean capToProgress = user == null || user.protocolVersion() >= VER_1_14;

		for (PistonSlimeMovement movement : movements) {
			if (!movement.activeAt(pistonTick)) {
				continue;
			}
			for (BlockPosition source : movement.slimeSources()) {
				if (movement.movementQueryArea(source, pistonTick).intersectsWith(entityBox)) {
					if (motion == initialMotion) {
						motion = initialMotion.copy();
					}
					movement.overwriteDirectionAxis(motion);
				}

				double requestedDistance = movement.pushDistance(
					source, pistonTick, entityBox, capToProgress
				);
				if (requestedDistance <= 0.0) {
					continue;
				}

				Direction direction = movement.direction();
				double restrictedDistance = restrictPistonMovement(
					restriction, direction, requestedDistance,
					user == null || user.protocolVersion() >= VER_1_14
				);
				if (Math.abs(restrictedDistance) <= 1.0E-5F) {
					continue;
				}

				Motion requested = new Motion(
					restrictedDistance * direction.normalX(),
					restrictedDistance * direction.normalY(),
					restrictedDistance * direction.normalZ()
				);
				Motion actual = collidePistonMovement(user, environment, entityBox, requested);
				position = position.add(actual);
				entityBox = entityBox.offset(actual.motionX(), actual.motionY(), actual.motionZ());
			}
		}

		return new AppliedEffects(position, motion, restriction);
	}

	private static double restrictPistonMovement(
		Motion restriction,
		Direction direction,
		double requestedDistance,
		boolean restricted
	) {
		if (!restricted) {
			return requestedDistance;
		}
		double signedDistance = requestedDistance * direction.axisDirection().offset();
		double previous;
		switch (direction.axis()) {
			case X_AXIS:
				previous = restriction.motionX();
				break;
			case Y_AXIS:
				previous = restriction.motionY();
				break;
			case Z_AXIS:
				previous = restriction.motionZ();
				break;
			default:
				throw new IllegalStateException("Unsupported piston axis " + direction.axis());
		}
		double cumulative = Math.max(-0.51D, Math.min(0.51D, previous + signedDistance));
		double allowedSigned = cumulative - previous;
		switch (direction.axis()) {
			case X_AXIS:
				restriction.setMotionX(cumulative);
				break;
			case Y_AXIS:
				restriction.setMotionY(cumulative);
				break;
			case Z_AXIS:
				restriction.setMotionZ(cumulative);
				break;
		}
		return Math.abs(allowedSigned);
	}

	private static Motion collidePistonMovement(
		User user,
		SimulationEnvironment environment,
		BoundingBox entityBox,
		Motion requested
	) {
		if (user == null) {
			return requested;
		}
		SimpleColliderResult result = user.simplifiedCollider().collide(
			user, environment, entityBox, requested
		);
		return new Motion(result.motionX(), result.motionY(), result.motionZ());
	}

	private static BoundingBox entityBoxAt(
		User user,
		SimulationEnvironment environment,
		Position position,
		BoundingBox fallback
	) {
		if (user != null) {
			return BoundingBox.fromPosition(user, environment, position);
		}
		return fallback == null ? BoundingBox.fromPosition(position.getX(), position.getY(), position.getZ()) : fallback;
	}

	private static final class AppliedEffects {
		private final Position position;
		private final Motion motion;
		private final Motion restriction;

		private AppliedEffects(Position position, Motion motion, Motion restriction) {
			this.position = position;
			this.motion = motion;
			this.restriction = restriction;
		}
	}
}
