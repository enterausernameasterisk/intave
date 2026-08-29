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

package de.jpx3.intave.cloud.protocol;

import ac.intave.cloud.protocol.AttestedPacket;
import ac.intave.cloud.protocol.listener.Serverbound;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Attestation {
	private final UUID idempotencyKey;
	private final AttestedPacket<Serverbound> packet;
	private final Set<UUID> requests = new HashSet<>();
	private final Consumer<Boolean> onComplete;

	private final int maximumRetries;
	private final long retryDelayMillis;
	private long lastAttemptTime = 0;

	public Attestation(
		AttestedPacket<Serverbound> packet,
		int maximumRetries, long retryDelayValue, TimeUnit retryDelayUnit
	) {
		this.idempotencyKey = UUID.randomUUID();
		this.packet = packet;
		this.onComplete = aBoolean -> {};
		this.maximumRetries = maximumRetries;
		this.retryDelayMillis = retryDelayUnit.toMillis(retryDelayValue);
	}

	public UUID idempotencyKey() {
		return idempotencyKey;
	}

	public UUID newRequestId() {
		UUID requestId = UUID.randomUUID();
		requests.add(requestId);
		return requestId;
	}

	public AttestedPacket<Serverbound> packet() {
		AttestedPacket<Serverbound> packet = this.packet;
		packet.setIdempotencyToken(idempotencyKey);
		packet.setRequestId(newRequestId());
		return packet;
	}

	public boolean needsRetry() {
		return requests.size() < maximumRetries &&
			(System.currentTimeMillis() - lastAttemptTime) >= retryDelayMillis;
	}

	public boolean isExpired() {
		return requests.size() >= maximumRetries;
	}

	public void complete() {
		onComplete.accept(true);
	}

	public void fail() {
		onComplete.accept(false);
	}

	public boolean hasRequest(UUID requestId) {
		return requests.contains(requestId);
	}

	public int maximumRetries() {
		return maximumRetries;
	}

	public long retryDelayMillis() {
		return retryDelayMillis;
	}
}
