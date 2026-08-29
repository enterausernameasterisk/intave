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

package de.jpx3.intave.module.player;

import ac.intave.cloud.protocol.packets.player.ServerboundPlaytime;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;

public final class PlaytimeRecorder extends Module {
	private int taskId;

	@Override
	public void enable() {
		taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(IntavePlugin.singletonInstance(), this::heartbeat, 0L, 20L * 60 * 2);
	}

	private void heartbeat() {
		UserRepository.applyOnAll(user -> {
			MovementMetadata movement = user.meta().movement();
			long activeTicks = movement.activeTicks.sumThenReset();
			long passiveTicks = movement.passiveTicks.sumThenReset();
			user.transmitCloudPacket(value ->
				new ServerboundPlaytime(value, activeTicks, passiveTicks)
			);
		});
	}

	@Override
	public void disable() {
		Bukkit.getScheduler().cancelTask(taskId);
	}
}
