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

package de.jpx3.intave.module.nayoro.sink;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.EventSink;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.PlayerContainer;

public final class ForwardEventSink extends EventSink {
  private final PlayerContainer player;

  public ForwardEventSink(PlayerContainer player) {
    this.player = player;
  }

  @Override
  public void visitAny(Event event) {
    Modules.linker().nayoroEvents().fireEvent(player, event);
  }

  @Override
  public String name() {
    return "FES";
  }
}
