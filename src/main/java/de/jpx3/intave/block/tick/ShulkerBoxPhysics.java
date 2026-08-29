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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_17;

/**
 * Simulates shulker-box block entities in the client's post-entity block-entity phase.
 */
public final class ShulkerBoxPhysics {
	private ShulkerBoxPhysics() {
	}

	public static Motion applyAfterPlayerTick(
		User user,
		SimulationEnvironment environment,
		Position position,
		Motion motion
	) {
		Map<BlockPosition, ShulkerBox> currentBoxes = environment.shulkerBoxes();
		if (currentBoxes.isEmpty()) {
			return motion;
		}

		LinkedHashMap<BlockPosition, ShulkerBox> boxes = new LinkedHashMap<>(currentBoxes);
		Position resultPosition = position;
		BoundingBox entityBox = BoundingBox.fromPosition(user, environment, position);

		Iterator<Map.Entry<BlockPosition, ShulkerBox>> iterator = boxes.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPosition, ShulkerBox> entry = iterator.next();
			ShulkerBox current = entry.getValue();
			if (current.complete()) {
				iterator.remove();
				continue;
			}
			if (!current.shouldTick()) {
				continue;
			}

			int protocolVersion = user.protocolVersion();
			float progressAfterTick = current.progressAfterTick();
			ShulkerBox next = current.tick();
			boolean legacyPush = legacyMovesCollidedEntities(protocolVersion, current, next);
			ShulkerBox collisionState = legacyPush
				? current.withProgress(progressAfterTick)
				: next;
			entry.setValue(collisionState);
			// The client advances animation progress before moving collided entities.
			environment.setShulkerBoxes(boxes);

			BoundingBox movementArea = null;
			if (modernMovesCollidedEntities(protocolVersion, current)) {
				movementArea = movementArea(
					entry.getKey(), current.direction(), current.progress(), next.progress()
				);
			} else if (legacyPush) {
				movementArea = legacyMovementArea(
					entry.getKey(), current.direction(), progressAfterTick
				);
			}
			if (movementArea != null && movementArea.intersectsWith(entityBox)) {
				Motion requested = legacyPush
					? legacyRequestedMovement(current.direction(), movementArea, entityBox)
					: requestedMovement(current.direction(), movementArea);
				Motion actual = collide(user, environment, entityBox, requested);
				resultPosition = resultPosition.add(actual);
				entityBox = entityBox.offset(actual.motionX(), actual.motionY(), actual.motionZ());
			}

			entry.setValue(next);
			if (next.complete()) {
				iterator.remove();
			}
		}

		environment.setShulkerBoxes(boxes);
		if (!resultPosition.equals(position)) {
			environment.setPosition(resultPosition);
		}
		return motion;
	}

	static boolean modernMovesCollidedEntities(int protocolVersion, ShulkerBox stateBeforeTick) {
		return protocolVersion >= VER_1_17 && stateBeforeTick.opening();
	}

	static boolean legacyMovesCollidedEntities(
		int protocolVersion,
		ShulkerBox stateBeforeTick,
		ShulkerBox stateAfterTick
	) {
		return protocolVersion >= VER_1_14
			&& protocolVersion < VER_1_17
			&& (stateBeforeTick.opening() || stateAfterTick.progress() > 0.0F);
	}

	static BoundingBox movementArea(
		BlockPosition position,
		Direction direction,
		float progressFrom,
		float progressTo
	) {
		double minimum = Math.min(progressFrom, progressTo);
		double maximum = Math.max(progressFrom, progressTo);
		double minX = position.getX();
		double minY = position.getY();
		double minZ = position.getZ();
		double maxX = minX + 1.0D;
		double maxY = minY + 1.0D;
		double maxZ = minZ + 1.0D;

		switch (direction) {
			case EAST:
				return BoundingBox.fromBounds(maxX + minimum, minY, minZ, maxX + maximum, maxY, maxZ);
			case WEST:
				return BoundingBox.fromBounds(minX - maximum, minY, minZ, minX - minimum, maxY, maxZ);
			case UP:
				return BoundingBox.fromBounds(minX, maxY + minimum, minZ, maxX, maxY + maximum, maxZ);
			case DOWN:
				return BoundingBox.fromBounds(minX, minY - maximum, minZ, maxX, minY - minimum, maxZ);
			case SOUTH:
				return BoundingBox.fromBounds(minX, minY, maxZ + minimum, maxX, maxY, maxZ + maximum);
			case NORTH:
				return BoundingBox.fromBounds(minX, minY, minZ - maximum, maxX, maxY, minZ - minimum);
			default:
				throw new IllegalStateException("Unsupported shulker direction " + direction);
		}
	}

	static Motion requestedMovement(Direction direction, BoundingBox movementArea) {
		double distance;
		switch (direction.axis()) {
			case X_AXIS:
				distance = movementArea.sizeX() + 0.01D;
				break;
			case Y_AXIS:
				distance = movementArea.sizeY() + 0.01D;
				break;
			case Z_AXIS:
				distance = movementArea.sizeZ() + 0.01D;
				break;
			default:
				throw new IllegalStateException("Unsupported shulker axis " + direction.axis());
		}
		return new Motion(
			distance * direction.normalX(),
			distance * direction.normalY(),
			distance * direction.normalZ()
		);
	}

	static BoundingBox legacyMovementArea(
		BlockPosition position,
		Direction direction,
		float progress
	) {
		double expansion = 0.5F * progress;
		double minX = position.getX();
		double minY = position.getY();
		double minZ = position.getZ();
		double maxX = minX + 1.0D;
		double maxY = minY + 1.0D;
		double maxZ = minZ + 1.0D;

		switch (direction) {
			case EAST:
				return BoundingBox.fromBounds(maxX, minY, minZ, maxX + expansion, maxY, maxZ);
			case WEST:
				return BoundingBox.fromBounds(minX - expansion, minY, minZ, minX, maxY, maxZ);
			case UP:
				return BoundingBox.fromBounds(minX, maxY, minZ, maxX, maxY + expansion, maxZ);
			case DOWN:
				return BoundingBox.fromBounds(minX, minY - expansion, minZ, maxX, minY, maxZ);
			case SOUTH:
				return BoundingBox.fromBounds(minX, minY, maxZ, maxX, maxY, maxZ + expansion);
			case NORTH:
				return BoundingBox.fromBounds(minX, minY, minZ - expansion, maxX, maxY, minZ);
			default:
				throw new IllegalStateException("Unsupported shulker direction " + direction);
		}
	}

	static Motion legacyRequestedMovement(
		Direction direction,
		BoundingBox movementArea,
		BoundingBox entityBox
	) {
		double distance;
		switch (direction) {
			case EAST:
				distance = movementArea.maxX - entityBox.minX;
				break;
			case WEST:
				distance = entityBox.maxX - movementArea.minX;
				break;
			case UP:
				distance = movementArea.maxY - entityBox.minY;
				break;
			case DOWN:
				distance = entityBox.maxY - movementArea.minY;
				break;
			case SOUTH:
				distance = movementArea.maxZ - entityBox.minZ;
				break;
			case NORTH:
				distance = entityBox.maxZ - movementArea.minZ;
				break;
			default:
				throw new IllegalStateException("Unsupported shulker direction " + direction);
		}
		distance += 0.01D;
		return new Motion(
			distance * direction.normalX(),
			distance * direction.normalY(),
			distance * direction.normalZ()
		);
	}

	private static Motion collide(
		User user,
		SimulationEnvironment environment,
		BoundingBox entityBox,
		Motion requested
	) {
		SimpleColliderResult result = user.simplifiedCollider().collide(
			user, environment, entityBox, requested
		);
		return new Motion(result.motionX(), result.motionY(), result.motionZ());
	}
}
