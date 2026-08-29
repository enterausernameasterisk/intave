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

package de.jpx3.intave.share;

public final class Result<T, E> {
	private final T result;
	private final E error;
	private boolean checked = false;

	public Result(T result, E error) {
		this.result = result;
		this.error = error;
	}

	public boolean successful() {
		checked = true;
		return error == null;
	}

	public boolean erroneous() {
		checked = true;
		return error != null;
	}

	public T result() {
		if (!checked) {
			throw new IllegalStateException("Result has not been checked yet");
		}
		if (error != null) {
			throw new IllegalStateException("Result is an error");
		}
		return result;
	}

	public E error() {
		if (!checked) {
			throw new IllegalStateException("Result has not been checked yet");
		}
		if (error == null) {
			throw new IllegalStateException("Result is not an error");
		}
		return error;
	}

	public static <T, E> Result<T, E> ok(T result) {
		return new Result<>(result, null);
	}

	public static <T, E> Result<T, E> success(T result) {
		return new Result<>(result, null);
	}

	public static <T, E> Result<T, E> error(E error) {
		return new Result<>(null, error);
	}
}
