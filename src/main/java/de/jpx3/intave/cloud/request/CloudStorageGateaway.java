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

package de.jpx3.intave.cloud.request;

import de.jpx3.intave.access.player.storage.StorageGateway;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;

public final class CloudStorageGateaway implements StorageGateway {
	public CloudStorageGateaway() {
	}

  @Override
  public void requestStorage(UUID id, Consumer<ByteBuffer> lazyReturn) {
//    cloud.storageRequest(id, lazyReturn);
  }

  @Override
  public void saveStorage(UUID id, ByteBuffer storage) {
//    cloud.saveStorage(id, storage);
  }

  @Override
  public String toString() {
    return "CloudStorageGateaway";
  }
}
