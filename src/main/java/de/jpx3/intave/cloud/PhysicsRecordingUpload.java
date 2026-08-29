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

package de.jpx3.intave.cloud;

import de.jpx3.intave.module.test.record.MovementRecording;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PhysicsRecordingUpload {
	private final UUID recordingId;
	private final int frameCount;
	private final int clientProtocolVersion;
	private final String serverVersion;
	private final String reason;
	private final String details;
	private final double addedViolationPoints;
	private final double violationLevelAfter;
	private final byte[] payload;

	public PhysicsRecordingUpload(
		MovementRecording recording,
		String reason,
		String details,
		double addedViolationPoints,
		double violationLevelAfter,
		byte[] payload
	) {
		this.recordingId = recording.internalId();
		this.frameCount = recording.frameCount();
		this.clientProtocolVersion = recording.clientProtocolVersion();
		this.serverVersion = recording.serverVersion().getVersion();
		this.reason = Objects.requireNonNull(reason, "reason");
		this.details = Objects.requireNonNull(details, "details");
		this.addedViolationPoints = addedViolationPoints;
		this.violationLevelAfter = violationLevelAfter;
		this.payload = Objects.requireNonNull(payload, "payload").clone();
	}

	public UUID recordingId() {
		return recordingId;
	}

	public int frameCount() {
		return frameCount;
	}

	public int clientProtocolVersion() {
		return clientProtocolVersion;
	}

	public String serverVersion() {
		return serverVersion;
	}

	public String reason() {
		return reason;
	}

	public String details() {
		return details;
	}

	public double addedViolationPoints() {
		return addedViolationPoints;
	}

	public double violationLevelAfter() {
		return violationLevelAfter;
	}

	public int chunkCount(int chunkSize) {
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be positive");
		}
		return Math.max(1, (int) ((payload.length + (long) chunkSize - 1) / chunkSize));
	}

	public byte[] chunk(int index, int chunkSize) {
		int chunkCount = chunkCount(chunkSize);
		if (index < 0 || index >= chunkCount) {
			throw new IndexOutOfBoundsException("Invalid chunk " + index + "/" + chunkCount);
		}
		int start = index * chunkSize;
		int end = Math.min(payload.length, start + chunkSize);
		return Arrays.copyOfRange(payload, start, end);
	}
}
