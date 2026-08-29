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

package de.jpx3.intave.module.nayoro.stream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class PeriodicFlushOutputStream extends FilterOutputStream {
	private final int threshold;
	private int bytesSinceFlush;

	public PeriodicFlushOutputStream(OutputStream output, int threshold) {
		super(output);
		this.threshold = threshold;
	}

	@Override
	public void write(int value) throws IOException {
		out.write(value);
		flushIfNeeded(1);
	}

	@Override
	public void write(byte[] bytes, int offset, int length) throws IOException {
		out.write(bytes, offset, length);
		flushIfNeeded(length);
	}

	private void flushIfNeeded(int writtenBytes) throws IOException {
		bytesSinceFlush += writtenBytes;
		if (bytesSinceFlush >= threshold) {
			out.flush();
			bytesSinceFlush = 0;
		}
	}

	@Override
	public void close() throws IOException {
		out.close();
	}
}
