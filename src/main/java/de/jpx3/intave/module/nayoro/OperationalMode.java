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

package de.jpx3.intave.module.nayoro;

public enum OperationalMode {
  DISABLE,
  LOCAL_STORAGE,
  CLOUD_TRANSMISSION;

  public boolean keepCopyOfSamples() {
    return (this == LOCAL_STORAGE);
  }
}
