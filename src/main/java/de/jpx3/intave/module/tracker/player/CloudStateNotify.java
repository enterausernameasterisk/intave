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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CloudStateNotify extends Module {
  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    plugin.cloud().playerLogin(join.getPlayer());
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    plugin.cloud().playerLogout(quit.getPlayer());
  }
}
