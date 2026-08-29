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

package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cloud.Cloud;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CloudStage extends CommandStage {
  private static CloudStage singletonInstance;

  private CloudStage() {
    super(BaseStage.singletonInstance(), "cloud");
  }

  @SubCommand(
    selectors = "status",
    usage = "",
    description = "Show version info"
  )
  public void statusCommand(CommandSender commandSender) {
    Cloud cloud = IntavePlugin.singletonInstance().cloud();
    boolean enabled = cloud.isEnabled();

    if (!enabled) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Cloud connection is not enabled");
      return;
    }

    commandSender.sendMessage(IntavePlugin.prefix() + "Connection status");

    // connected to at least one
    boolean connectedToAtLeastOne = cloud.isConnected();
    commandSender.sendMessage(ChatColor.GRAY + " Cloud is " + (connectedToAtLeastOne ? ChatColor.GREEN + "connected" : ChatColor.RED + "disconnected"));
    commandSender.sendMessage(ChatColor.GRAY + " Received " + ChatColor.GREEN + formatBytes(cloud.receivedBytes()) + ChatColor.GRAY + ", sent " + ChatColor.GREEN + formatBytes(cloud.sentBytes()));
  }

  @SubCommand(
    selectors = "transmission",
    description = "Show player transmission status"
  )
  public void transmissionCommand(CommandSender commandSender) {
    Cloud cloud = IntavePlugin.singletonInstance().cloud();
    boolean enabled = cloud.isEnabled();

    if (!enabled) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Cloud connection is not enabled");
      return;
    }

    Nayoro nayoro = Modules.nayoro();
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      String mainBase = IntavePlugin.prefix() + "Player " + ChatColor.RED + onlinePlayer.getName() + ChatColor.GRAY;
      User user = UserRepository.userOf(onlinePlayer);
      if (nayoro.recordingActiveFor(user)) {
        mainBase += " is " + ChatColor.GREEN + "transmitting";
      } else {
        mainBase += " is " + ChatColor.RED + "not transmitting";
      }

      if (nayoro.hasRecordSink(user)) {
        mainBase += ChatColor.GRAY + " and " + ChatColor.GREEN + "recording";
      } else {
        mainBase += ChatColor.GRAY + " and " + ChatColor.RED + "not recording";
      }

      commandSender.sendMessage(mainBase);
    }
  }

  private String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + "B";
    } else if (bytes < 1024 * 1024) {
      return bytes / 1024 + "KB";
    } else if (bytes < 1024 * 1024 * 1024) {
      return bytes / (1024 * 1024) + "MB";
    } else {
      return bytes / (1024 * 1024 * 1024) + "GB";
    }
  }

  public static CloudStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new CloudStage();
    }
    return singletonInstance;
  }
}
