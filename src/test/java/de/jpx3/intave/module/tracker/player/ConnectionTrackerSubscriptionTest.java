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

import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ConnectionTrackerSubscriptionTest {
  @Test
  void observesFinalNonCancelledKeepAlivePackets() throws Exception {
    assertFinalObserver(ConnectionTracker.class.getMethod("processOutgoingPingPackets", com.comphenix.protocol.events.PacketEvent.class));
    assertFinalObserver(ConnectionTracker.class.getMethod("processIncomingPingPackets", com.comphenix.protocol.events.PacketEvent.class));
  }

  private static void assertFinalObserver(Method method) {
    PacketSubscription subscription = method.getAnnotation(PacketSubscription.class);
    assertEquals(ListenerPriority.MONITOR, subscription.priority());
    assertEquals(PrioritySlot.EXTERNAL, subscription.prioritySlot());
    assertFalse(subscription.ignoreCancelled());
  }
}
