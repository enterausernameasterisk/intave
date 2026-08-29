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

package de.jpx3.intave.module.linker.nayoro;

import ac.intave.samples.event.Event;
import de.jpx3.intave.module.nayoro.PlayerContainer;

public final class NayoroRegisteredListener {
  private final NayoroEventSubscriber subscriber;
  private final NayoroEventExecutor eventExecutor;

  public NayoroRegisteredListener(NayoroEventSubscriber subscriber, NayoroEventExecutor eventExecutor) {
    this.subscriber = subscriber;
    this.eventExecutor = eventExecutor;
  }

  public void execute(PlayerContainer player, Event event) {
    eventExecutor.execute(subscriber, player, event);
  }

  public void initialize() {

  }

  public NayoroEventSubscriber subscriber() {
    return subscriber;
  }
}
