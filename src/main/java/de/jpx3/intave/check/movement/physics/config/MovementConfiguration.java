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

package de.jpx3.intave.check.movement.physics.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.StreamCodec;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

public interface MovementConfiguration {
	StreamCodec<JsonReader, JsonWriter, MovementConfiguration> JSON_CODEC = object(
		integerField("forward", MovementConfiguration::forward),
		integerField("strafe", MovementConfiguration::strafe),
		booleanField("handActive", MovementConfiguration::isHandActive),
		booleanField("jumping", MovementConfiguration::isJumping),
		integerField("reduceTicks", MovementConfiguration::reduceTicks),
		booleanField("sprinting", MovementConfiguration::isSprinting),
		booleanField("reduceBefore", MovementConfiguration::reduceBefore),
		booleanField(
			"overrideEndMotionToActualMotion",
			MovementConfiguration::overrideEndMotionToActualMotion,
			true
		),
		booleanField(
			"v21_9BlockInsideCheck",
			MovementConfiguration::usesAlternateBlockInsideCheck,
			false
		),
		(forward, strafe, handActive, jumping, reduceTicks, sprinting, reduceBefore, overrideEndMotion, v21_9BlockInsideCheck) -> {
			MovementConfiguration configuration = MovementConfiguration.blank()
				.withKeypress(forward, strafe)
				.withHandActive(handActive)
				.withJumped(jumping)
				.withReduceTicks(reduceTicks)
				.withSprintingSetTo(sprinting)
				.withReduceBefore(reduceBefore);
			configuration = overrideEndMotion
				? configuration.allowOverrideToActualMotion()
				: configuration.denyOverrideToActualMotion();
			return v21_9BlockInsideCheck
				? configuration.withAlternativeBlockInsideCheck()
				: configuration.withoutAlternativeBlockInsideCheck();
		}
	);

	int forward();

	boolean isHandActive();

	boolean isJumping();

	boolean isReducing();

	boolean isSprinting();

	MovementConfiguration pressingA();

	MovementConfiguration pressingD();

	MovementConfiguration pressingS();

	MovementConfiguration pressingW();

	boolean reduceBefore();

	boolean overrideEndMotionToActualMotion();

	boolean usesAlternateBlockInsideCheck();

	int reduceTicks();

	int strafe();

	MovementConfiguration withActiveHand();

	MovementConfiguration withForward(int forward);

	MovementConfiguration withHandActive(boolean hasHandActive);

	MovementConfiguration withJump();

	MovementConfiguration withJumped(boolean hasJumped);

	MovementConfiguration withKeypress(int forward, int strafe);

	MovementConfiguration withReduceBefore(boolean hasReduceBefore);

	MovementConfiguration withReduceTicks(int ticks);

	MovementConfiguration withSprinting();

	MovementConfiguration withSprintingSetTo(boolean sprinting);

	MovementConfiguration withStrafe(int strafe);

	MovementConfiguration withoutActiveHand();

	MovementConfiguration withoutJump();

	MovementConfiguration withoutKeypress();

	MovementConfiguration withoutReducing();

	MovementConfiguration withoutSprinting();

	MovementConfiguration allowOverrideToActualMotion();

	MovementConfiguration denyOverrideToActualMotion();

	MovementConfiguration withAlternativeBlockInsideCheck();

	MovementConfiguration withoutAlternativeBlockInsideCheck();

	TraceImmutableMovementConfiguration withRecording();

	static MovementConfiguration blank() {
		return IndexBasedMovementConfiguration.blank();
	}

	default String keysToString() {
		StringBuilder builder = new StringBuilder();
		int forward = forward();
		int strafe = strafe();
		if (forward == 1) {
			builder.append("W");
		} else if (forward == -1) {
			builder.append("S");
		}
		if (strafe == 1) {
			builder.append("D");
		} else if (strafe == -1) {
			builder.append("A");
		}
		return builder.toString();
	}

	default String toCompactString() {
		return ("(" + keysToString() + ") " +
			(isReducing() ? "_RED" + reduceTicks() : "") +
			(isSprinting() ? "_SPR" : "") +
			(isJumping() ? "_JMP" : "") +
			(isHandActive() ? "_HA" : "")
		).trim();
	}

	default boolean anyKeypress() {
		return forward() != 0 || strafe() != 0 || isJumping();
	}
}
