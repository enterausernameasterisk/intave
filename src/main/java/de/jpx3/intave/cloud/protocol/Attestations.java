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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class Attestations {
	private final Map<UUID, Attestation> pendingAttestationsByIdempotencyKey = new ConcurrentHashMap<>();
	private final ReentrantLock lock = new ReentrantLock();

	public void add(Attestation attestation) {
		try {
			lock.lock();
			pendingAttestationsByIdempotencyKey.put(attestation.idempotencyKey(), attestation);
		} finally {
			lock.unlock();
		}
	}

	public Attestation get(UUID idempotencyKey) {
		try {
			lock.lock();
			return pendingAttestationsByIdempotencyKey.get(idempotencyKey);
		} finally {
			lock.unlock();
		}
	}

	public boolean contains(UUID idempotencyKey) {
		try {
			lock.lock();
			return pendingAttestationsByIdempotencyKey.containsKey(idempotencyKey);
		} finally {
			lock.unlock();
		}
	}

	public void remove(List<UUID> requestIds) {
		try {
			lock.lock();
			for (UUID requestId : requestIds) {
				pendingAttestationsByIdempotencyKey.remove(requestId);
			}
		} finally {
			lock.unlock();
		}
	}

	public void confirm(List<UUID> requestIds) {
		try {
			lock.lock();
			pendingAttestationsByIdempotencyKey.values().removeIf(attestation -> {
				for (UUID requestId : requestIds) {
					if (attestation.hasRequest(requestId)) {
						attestation.complete();
						return true;
					}
				}
				return false;
			});
		} finally {
			lock.unlock();
		}
	}

	public void removeExpired() {
		try {
			lock.lock();
			pendingAttestationsByIdempotencyKey.values()
				.stream()
				.filter(Attestation::isExpired)
				.forEach(attestation -> {
					pendingAttestationsByIdempotencyKey.remove(attestation.idempotencyKey());
					attestation.fail();
				});
		} finally {
			lock.unlock();
		}
	}

	public void forEachResendable(Consumer<Attestation> action) {
		try {
			lock.lock();
			pendingAttestationsByIdempotencyKey.values().stream()
				.filter(Attestation::needsRetry)
				.forEach(action);
		} finally {
			lock.unlock();
		}
	}
}
