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

public enum PrivacyMode {
	BEST_DETECTION,
	HIGHEST_PRIVACY

	;

	public boolean annotateINetAdds() {
		return this == BEST_DETECTION;
	}

	public static PrivacyMode fromString(String string) {
		for (PrivacyMode mode : values()) {
			if (mode.name().equalsIgnoreCase(string)) {
				return mode;
			}
		}
		throw new IllegalArgumentException("Unknown privacy mode: " + string);
	}
}
