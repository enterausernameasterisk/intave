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

import ac.intave.samples.event.Event;

import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class InstantPlayback extends Playback implements Runnable {
  private final Executor executor;
  private final Consumer<? super Playback> onComplete;
  private boolean interrupted = false;
  private long passedTime = 0;

  public InstantPlayback(InputStream stream, Executor executor) {
    this(stream, executor, (playback) -> {});
  }

  public InstantPlayback(InputStream stream, Executor executor, Consumer<? super Playback> onComplete) {
    super(stream);
    this.executor = executor;
    this.onComplete = onComplete;
  }

  @Override
  public void start() {
    executor.execute(this);
  }

  @Override
  public void run() {
    try {
      Event event;
      // ignore schedule time
      while ((event = nextEvent()) != null && !interrupted) {
        long offset = event.offset();
        passedTime += offset;
        visitSelect(event);
      }
    } finally {
      try {
        closeRecording();
      } finally {
        onComplete.accept(this);
      }
    }
  }

  @Override
  public void stop() {
    interrupted = true;
  }

  @Override
  public long currentTime() {
    return passedTime;
  }

  @Override
  public String name() {
    return "IP";
  }
}
