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

import ac.intave.samples.event.AttackEvent;
import ac.intave.samples.event.EntityRemoveEvent;
import ac.intave.samples.event.EntitySpawnEvent;
import ac.intave.samples.event.EventSink;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.Set;

public final class SampleStage extends CommandStage {
  private static SampleStage singletonInstance;

  public SampleStage() {
    super(RootStage.singletonInstance(), "sample");
  }

  @SubCommand(
    selectors = "sinks"
  )
  public void sinksCommand(User user) {
    Nayoro nayoro = Modules.nayoro();
    user.player().sendMessage(ChatColor.GRAY + "Active sinks:");
    for (EventSink eventSink : nayoro.sinksOf(user)) {
      user.player().sendMessage(ChatColor.GRAY + " - " + eventSink.getClass().getSimpleName());
    }
  }

  @SubCommand(
    selectors = "entitycontrol"
  )
  public void entityControlCommand(User user) {
    Nayoro nayoro = Modules.nayoro();
    nayoro.pushSink(user, new EventSink() {
      private final Set<Integer> entities = new HashSet<>();

      @Override
      public void visit(EntitySpawnEvent event) {
        user.player().sendMessage(ChatColor.GRAY + "SPAWN: " + event.id() + " " + event.size() + " " + event.name());
        entities.add(event.id());
      }

      @Override
      public void visit(EntityRemoveEvent event) {
        user.player().sendMessage(ChatColor.GRAY + "REMOVE: " + event.id());
        if (!entities.remove(event.id())) {
          user.player().sendMessage(ChatColor.RED + "Entity " + event.id() + " was not spawned before!");
        }
      }

      @Override
      public void visit(AttackEvent event) {
        user.player().sendMessage(ChatColor.GRAY + "ATTACK: " + event.source() + " -> " + event.target());
        if (!entities.contains(event.target())) {
          user.player().sendMessage(ChatColor.RED + "Entity " + event.target() + " was not spawned before!");
        }
      }

      @Override
      public String name() {
        return "EC/anonymous";
      }
    });
    user.player().sendMessage(ChatColor.GREEN + "Entity control enabled");
  }

  public static SampleStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new SampleStage();
    }
    return singletonInstance;
  }
}
