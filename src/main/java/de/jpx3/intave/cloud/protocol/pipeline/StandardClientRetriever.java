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

package de.jpx3.intave.cloud.protocol.pipeline;

import ac.intave.cloud.protocol.Identity;
import ac.intave.cloud.protocol.Packet;
import ac.intave.cloud.protocol.listener.Clientbound;
import ac.intave.cloud.protocol.packets.ClientboundCombatModifier;
import ac.intave.cloud.protocol.packets.ClientboundSetTrustfactor;
import ac.intave.cloud.protocol.packets.ClientboundViolation;
import ac.intave.cloud.protocol.packets.base.ClientboundConfirmAttestations;
import ac.intave.cloud.protocol.packets.base.ClientboundDisconnect;
import ac.intave.cloud.protocol.packets.base.ClientboundHello;
import ac.intave.cloud.protocol.packets.base.ClientboundKeepAlive;
import ac.intave.cloud.protocol.packets.player.ClientboundClarifyUnknownPlayerId;
import ac.intave.cloud.protocol.packets.player.ClientboundSendMessage;
import ac.intave.cloud.protocol.packets.player.ClientboundSetPlayerId;
import ac.intave.cloud.protocol.packets.sampling.ClientboundSetSamplingState;
import ac.intave.samples.share.Classifier;
import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.cloud.Session;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ac.intave.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_TRANSMISSION;

public final class StandardClientRetriever extends ChannelInboundHandlerAdapter implements Clientbound {
  private final Session session;

  public StandardClientRetriever(Session session) {
    this.session = session;
  }

  @Override
  public void channelRead(ChannelHandlerContext channelHandlerContext, Object message) {
    if (!(message instanceof Packet)) {
      throw new IllegalArgumentException(
        "Cloud processor received unexpected message type "
          + (message == null ? "null" : message.getClass().getName())
      );
    }
    Packet<?> packet = (Packet<?>) message;
    if (packet.direction() != CLIENTBOUND) {
      throw new IllegalArgumentException(
        "Cloud processor received " + packet.direction().name().toLowerCase()
          + " packet '" + packet.name() + "' in the clientbound pipeline"
      );
    }
    onSelect(packet);
  }

  @Override
  public void onCloseConnection(ClientboundDisconnect packet) {
    IntaveLogger.logger().info("[Cloud] Connection closed by cloud: " + packet.reason());
    session.close();
  }

  @Override
  public void onClientHello(ClientboundHello packet) {
    throw new RuntimeException("Unexpected packet " + packet.name());
  }

  @Override
  public void onSetPlayerId(ClientboundSetPlayerId packet) {
    UUID userId = packet.identity().id();
    if (!session.awaitingPlayerId(packet.identity())) {
      Player player = findPlayer(packet.identity());
      if (player == null) {
        IntaveLogger.logger().error(
          "[Cloud] Received player ID for unknown player: " + packet.identity()
        );
        return;
      }
      userId = player.getUniqueId();
    }
    User user = UserRepository.userOf(userId);
    if (!user.hasPlayer()) {
      // A player may have logged out while its PLAYER_LOGIN was in flight.
      // Session keeps the UUID so it can immediately acknowledge with PLAYER_LOGOUT.
      if (!session.awaitingPlayerId(packet.identity())) {
        IntaveLogger.logger().error(
          "[Cloud] Received player ID for user without player: " + userId
        );
        return;
      }
    }
    session.setUserId(userId, packet.id());
  }

  @Override
  public void onConfirmAttestations(ClientboundConfirmAttestations packet) {
    session.confirmAttestations(packet.requestIds());
  }

  @Override
  public void onClarifyUnknownPlayerId(ClientboundClarifyUnknownPlayerId packet) {
    User user = session.userById(packet.playerId());
    if (!user.hasPlayer()) {
      IntaveLogger.logger().error(
        "[Cloud] Cannot clarify unknown player ID " + packet.playerId()
          + ": no local player is mapped to it"
      );
      return;
    }
    session.clarifyUnknownPlayerId(user, packet.playerId());
  }

  @Override
  public void onKeepAlive(ClientboundKeepAlive packet) {
    // do nothing
  }

  @Override
  public void onSetTrustfactor(ClientboundSetTrustfactor packet) {
    UUID playerId = session.userIdByPlayerId(packet.id());
    if (playerId == null) {
      IntaveLogger.logger().error(
        "[Cloud] Cannot apply packet '" + packet.name() + "': player id "
          + packet.id() + " is not mapped in this cloud session"
      );
      return;
    }
    Synchronizer.synchronize(() -> {
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        IntaveLogger.logger().warn(
          "[Cloud] Player id " + packet.id()
            + " went offline before packet '" + packet.name() + "' could be applied"
        );
        return;
      }
      IntaveAccessor.unsafeAccess()
        .player(player)
        .setTrustFactor(
          de.jpx3.intave.access.player.trust.TrustFactor.valueOf(
            packet.trustFactor()
          )
        );
    });
  }

  @Override
  public void onCombatModifier(ClientboundCombatModifier packet) {
    User user = session.userById(packet.id());
    if (!user.hasPlayer()) {
      return;
    }
    AttackNerfStrategy strat = AttackNerfStrategy.byName(packet.modifier());
    if (strat == null) {
      return;
    }
    switch (packet.duration()) {
      case 0:
        user.nerfOnce(strat, "cc");
        break;
      case 1:
        user.nerf(strat, "cc");
        break;
      case 2:
        user.nerfPermanently(strat, "cc");
        break;
    }
  }

  @Override
  public void onChangeSampling(ClientboundSetSamplingState packet) {
    User user = session.userById(packet.id());
    if (!user.hasPlayer()) {
      return;
    }
    Nayoro nayoro = Modules.nayoro();
    boolean startRequested = packet.newState() == ClientboundSetSamplingState.SamplingState.START;
    boolean currentlyActive = nayoro.recordingActiveFor(user);
    if (currentlyActive && startRequested) {
      nayoro.disableRecordingFor(user);
      currentlyActive = false;
    }
    if (currentlyActive == startRequested) {
      return;
    }
    IntaveLogger.logger().info("Sampling state changed for " + user + ": " + (startRequested ? "START" : "STOP"));
    if (currentlyActive) {
      nayoro.disableRecordingFor(user);
	  } else {
      nayoro.enableRecordingFor(user, Classifier.UNKNOWN, CLOUD_TRANSMISSION, packet.transmissionId());
    }
  }

  @Override
  public void onSendMessage(ClientboundSendMessage packet) {
    UUID playerId = session.userIdByPlayerId(packet.playerId());
    if (playerId == null) {
      IntaveLogger.logger().error(
        "[Cloud] Cannot deliver clientbound packet '" + packet.name()
          + "': player id " + packet.playerId() + " is not mapped in this cloud session"
      );
      return;
    }
    List<TextComponent> lines = new ArrayList<>(packet.lines());
    Synchronizer.synchronize(() -> {
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        IntaveLogger.logger().warn(
          "[Cloud] Player id " + packet.playerId()
            + " went offline before packet '" + packet.name() + "' could be delivered"
        );
        return;
      }
      try {
        for (TextComponent component : lines) {
          player.spigot().sendMessage(component);
        }
      } catch (Exception exception) {
        IntaveLogger.logger().error(
          "[Cloud] Failed to deliver packet '" + packet.name() + "' to "
            + player.getName() + ": " + Session.describeFailure(exception)
        );
        exception.printStackTrace();
      }
    });
  }

  @Override
  public void onUncaught(Packet<?> packet) {
    IntaveLogger.logger().error(
      "[Cloud] Dropped clientbound packet '" + packet.name()
        + "' (version " + packet.version()
        + "): no application handler is registered"
    );
  }

  private static Player findPlayer(Identity identity) {
    if (identity.id() != null) {
      Player player = Bukkit.getPlayer(identity.id());
      if (player != null) {
        return player;
      }
    }
    return identity.name() == null ? null : Bukkit.getPlayerExact(identity.name());
  }

  @Override
  public void exceptionCaught(
    ChannelHandlerContext channelHandlerContext,
    Throwable throwable
  ) {
    channelHandlerContext.fireExceptionCaught(throwable);
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) {
    if (session.started()) {
      IntaveLogger.logger().warn(
        "[Cloud] Channel became inactive while the cloud session was ready"
      );
    }
    context.fireChannelInactive();
  }

  @Override
  public void onViolation(ClientboundViolation packet) {
    User user = session.userById(packet.id());
    if (!user.hasPlayer()) {
      return;
    }
    Player player = user.player();
    try {
      IntavePlugin intave = IntavePlugin.singletonInstance();
      CheckService checks = intave.checks();
      Check check = checks.searchCheck(packet.check());
      Violation violation = Violation.builderFor(check.getClass())
        .forPlayer(player)
        .withCustomThreshold(packet.threshold())
        .withMessage(packet.message())
        .withDetails(packet.details())
        .withVL(packet.vl())
        .build();
      ViolationProcessor violationProcessor = Modules.violationProcessor();
      violationProcessor.processViolation(violation);
    } catch (Exception exception) {
      IntaveLogger.logger().error(
        "[Cloud] Failed to process clientbound violation for player id "
          + packet.id() + ": " + Session.describeFailure(exception)
      );
      exception.printStackTrace();
    }
  }
}
