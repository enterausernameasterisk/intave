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

import ac.intave.cloud.protocol.Packet;
import ac.intave.cloud.protocol.PacketRegistry;
import ac.intave.cloud.protocol.ProtocolSpecification;
import ac.intave.cloud.protocol.compression.CompressionAlgorithms;
import ac.intave.cloud.protocol.listener.Clientbound;
import ac.intave.cloud.protocol.packets.base.ClientboundDisconnect;
import ac.intave.cloud.protocol.packets.base.ClientboundHello;
import ac.intave.cloud.protocol.packets.base.ServerboundConfirmEncryption;
import ac.intave.cloud.protocol.packets.base.ServerboundHello;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.cloud.Session;
import de.jpx3.intave.cloud.protocol.CloudToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static ac.intave.cloud.protocol.Direction.CLIENTBOUND;
import static ac.intave.cloud.protocol.Direction.SERVERBOUND;

public final class HandshakeReceiver extends ChannelInboundHandlerAdapter implements Clientbound {
  private final Session session;

  public HandshakeReceiver(Session session) {
    this.session = session;
  }

  private static Key generateKey(String cipher, int keySize) throws NoSuchAlgorithmException {
    if (cipher.contains("/")) {
      cipher = cipher.substring(0, cipher.indexOf("/"));
    }
    KeyGenerator keyGenerator = KeyGenerator.getInstance(cipher);
    keyGenerator.init(keySize);
    return keyGenerator.generateKey();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    CloudToken cloudToken = session.shard();
    ArrayList<String> hmacs = new ArrayList<>(Security.getAlgorithms("Mac"));
    ServerboundHello serverHelloPacket = ServerboundHello.builder()
      .token(new String(cloudToken.token(), StandardCharsets.UTF_8))
      .supportedEncryptionAlgorithms(Security.getAlgorithms("Cipher").stream().filter(s -> s.startsWith("AES")).collect(Collectors.toList()))
      .supportedEncryptionKeySizes(Collections.singletonList(128))
      .supportedCompressionAlgorithms(CompressionAlgorithms.supportedNames())
      .supportedHMACAlgorithms(hmacs)
      .clientboundProtocol(PacketRegistry.packetSpecsFor(CLIENTBOUND))
      .serverboundProtocol(PacketRegistry.packetSpecsFor(SERVERBOUND))
      .build();
    ctx.writeAndFlush(serverHelloPacket).addListener(future -> {
      if (!future.isSuccess()) {
        ctx.fireExceptionCaught(new IllegalStateException(
          "Unable to send the initial cloud handshake packet",
          future.cause()
        ));
      }
    });
    ctx.fireChannelActive();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object object) {
    Packet<?> packet = (Packet<?>) object;
    if (packet instanceof ClientboundDisconnect) {
      onCloseConnection((ClientboundDisconnect) packet);
      return;
    }
    if (!(packet instanceof ClientboundHello)) {
      //noinspection unchecked
      session.receivePacketLater((Packet<Clientbound>) packet);
      return;
    }
    //noinspection unchecked
    ((Packet<Clientbound>) packet).accept(this);
    ctx.writeAndFlush(buildConfirmEncryptionPacket()).addListener(future -> {
      if (!future.isSuccess()) {
        ctx.fireExceptionCaught(new IllegalStateException(
          "Unable to send the cloud encryption confirmation packet",
          future.cause()
        ));
        return;
      }
      try {
        String algorithm = session.encryptionScheme();
        // use AES with key from packet
        Cipher downDecryptCipher = Cipher.getInstance(algorithm);
        Cipher upEncryptCipher = Cipher.getInstance(algorithm);

        byte[] iv = session.verifyBytes();
        downDecryptCipher.init(Cipher.DECRYPT_MODE, session.primaryKey(), new IvParameterSpec(iv));
        upEncryptCipher.init(Cipher.ENCRYPT_MODE, session.primaryKey(), new IvParameterSpec(iv));
        session.setEncryption(downDecryptCipher, upEncryptCipher);

        StandardClientRetriever processor = new StandardClientRetriever(session);
        session.setProcessor(processor);
        session.markStarted();

        Packet<Clientbound> pendingPacket;
        while ((pendingPacket = session.pendingIncoming().poll()) != null) {
          pendingPacket.accept(processor);
        }
      } catch (Exception exception) {
        ctx.fireExceptionCaught(new IllegalStateException(
          "Unable to finish the cloud encryption handshake",
          exception
        ));
      }
    });
  }

  @Override
  public void onClientHello(ClientboundHello packet) {
    ProtocolSpecification protocol = session.protocol();
    protocol.overrideAvailablePackets(CLIENTBOUND, new HashSet<>(packet.clientboundPackets()));
    protocol.overrideAvailablePackets(SERVERBOUND, new HashSet<>(packet.serverboundPackets()));
    protocol.overridePacketIds(CLIENTBOUND, packet.clientboundPackets());
    protocol.overridePacketIds(SERVERBOUND, packet.serverboundPackets());
    session.selectCompressionAlgorithm(packet.compressionAlgorithm());

    String encryption = packet.encryptionAlgorithm();
    session.setEncryptionScheme(encryption);
    if (encryption.contains("/")) {
      encryption = encryption.substring(0, encryption.indexOf("/"));
    }
    session.setEncryptionAlgorithm(encryption);
    session.setServerPublicKey(packet.publicKey());
    try {
      Key generatedKey = generateKey(session.encryptionAlgorithm(), 128);
      session.setPrimaryKey(generatedKey);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(
        "Cloud selected unsupported encryption algorithm "
          + session.encryptionAlgorithm(),
        e
      );
    }
    session.setVerifyBytes(packet.verifyToken());
  }

  @Override
  public void onCloseConnection(ClientboundDisconnect packet) {
    IntaveLogger.logger().info("[Cloud] Connection closed: " + packet.reason());
    session.close();
  }

  private ServerboundConfirmEncryption buildConfirmEncryptionPacket() {
    byte[] sharedSecretEncrypted = encryptRSAChunked(session.primaryKey().getEncoded());
    byte[] verifyBytesEncrypted = encryptRSAChunked(session.verifyBytes());
    return new ServerboundConfirmEncryption(sharedSecretEncrypted, verifyBytesEncrypted);
  }

  private byte[] encryptRSAChunked(byte[] bytes) {
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, session.serverPublicKey());
      return cipher.doFinal(bytes);
    } catch (Exception e) {
      throw new IllegalStateException(
        "Unable to encrypt the cloud handshake secret with the server public key",
        e
      );
    }
  }
}
