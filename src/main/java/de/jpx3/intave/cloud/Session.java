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

package de.jpx3.intave.cloud;

import ac.intave.cloud.protocol.*;
import ac.intave.cloud.protocol.compression.CompressionAlgorithm;
import ac.intave.cloud.protocol.compression.CompressionAlgorithms;
import ac.intave.cloud.protocol.listener.Clientbound;
import ac.intave.cloud.protocol.listener.Serverbound;
import ac.intave.cloud.protocol.packets.base.ServerboundKeepAlive;
import ac.intave.cloud.protocol.packets.player.ServerboundPlayerLogin;
import ac.intave.cloud.protocol.packets.player.ServerboundPlayerLogout;
import ac.intave.cloud.protocol.pipeline.*;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.cloud.protocol.Attestation;
import de.jpx3.intave.cloud.protocol.Attestations;
import de.jpx3.intave.cloud.protocol.CloudToken;
import de.jpx3.intave.cloud.protocol.pipeline.Errors;
import de.jpx3.intave.cloud.protocol.pipeline.HandshakeReceiver;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import java.net.InetAddress;
import java.security.Key;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import static ac.intave.cloud.protocol.Direction.CLIENTBOUND;
import static ac.intave.cloud.protocol.Direction.SERVERBOUND;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Session {
	private CloudToken cloudToken;
	private volatile Channel channel;
	private ProtocolSpecification protocol = new ProtocolSpecification();
	private final Queue<Packet<Serverbound>> pendingOutgoing = new ConcurrentLinkedQueue<>();
	private final Queue<Packet<Clientbound>> pendingIncoming = new ConcurrentLinkedQueue<>();
	private final List<Consumer<Void>> startupSubscribers = new ArrayList<>();
	private final List<Consumer<Session>> shutdownSubscribers = new ArrayList<>();

	private PublicKey serverPublicKey;
	private String encryptionAlgorithm;
	private String encryptionScheme;
	private Key primaryKey;
	private byte[] verifyBytes;
	private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithms.initial();

	private volatile boolean started;
	private final LongAdder receivedBytes = new LongAdder();
	private final LongAdder sentBytes = new LongAdder();

	private final Map<UUID, Long> userToPlayerId = new HashMap<>();
	private final Long2ObjectMap<UUID> playerIdToUser = new Long2ObjectOpenHashMap<>();
	private final Set<UUID> requestedPlayerIds = new HashSet<>();
	private final Set<UUID> pendingPlayerLogouts = new HashSet<>();
	private final Set<UUID> locallyOnlineUsers = new HashSet<>();
	private final Map<UUID, Queue<LongFunction<? extends Packet<Serverbound>>>> pendingPlayerPackets = new HashMap<>();


	private final Attestations attestations = new Attestations();

	public Session(CloudToken cloudToken) {
		this.cloudToken = cloudToken;
	}

	public void tryToConnect(Consumer<Boolean> lazyReturn) {
		EventLoopGroup group = new NioEventLoopGroup(2, IntaveThreadFactory.ofPriority(3));
		Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class).option(CONNECT_TIMEOUT_MILLIS, 8000).handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) {
				ch.pipeline().addLast("timeout", new ReadTimeoutHandler(120))
					.addLast("decompression", new Decompression(256, compressionAlgorithm))
					.addLast("compression", new Compression(256, compressionAlgorithm))
					.addLast("batching", new Batching(1024 * 128, 500, MILLISECONDS))
					.addLast("codec", new PacketCodec(protocol, CLIENTBOUND))
					.addLast("processor", new HandshakeReceiver(Session.this))
					.addLast("errors", new Errors(Session.this));
			}
		});

		try {
			ChannelFuture connectFuture = bootstrap.connect(cloudToken.domain(), cloudToken.port());
			channel = connectFuture.channel();
			if (!connectFuture.await(10, SECONDS)) {
				connectFuture.cancel(false);
				connectFuture.channel().close();
				IntaveLogger.logger().error("Timed out connecting to cloud after 10 seconds");
				group.shutdownGracefully();
				lazyReturn.accept(false);
				return;
			}
			if (!connectFuture.isSuccess()) {
				IntaveLogger.logger().error("Unable to connect to cloud: " + describeFailure(connectFuture.cause()));
				connectFuture.channel().close();
				group.shutdownGracefully();
				lazyReturn.accept(false);
				return;
			}
//			IntaveLogger.logger().info("Connection established with cloud; starting handshake");
			lazyReturn.accept(true);
			channel.closeFuture().addListener(closeFuture -> {
				started = false;
				if (closeFuture.cause() == null) {
					IntaveLogger.logger().info("Connection to cloud closed");
				} else {
					IntaveLogger.logger().error("Connection to cloud closed unexpectedly: " + describeFailure(closeFuture.cause()));
				}
				notifyShutdownSubscribers();
				group.shutdownGracefully();
				lazyReturn.accept(false);
			});
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			IntaveLogger.logger().error("Interrupted while connecting to cloud: " + describeFailure(exception));
			group.shutdownGracefully();
			lazyReturn.accept(false);
		} catch (Exception exception) {
			IntaveLogger.logger().error("Failed to initialize connection to cloud: " + describeFailure(exception));
			exception.printStackTrace();
			group.shutdownGracefully();
			lazyReturn.accept(false);
		}
	}

	public void keepAliveTick() {
		if (canSend(ServerboundKeepAlive.class)) {
			writePacket(new ServerboundKeepAlive());
		}
	}

	public void retryMissedAttestations() {
		BackgroundExecutors.execute(() -> {
			attestations.removeExpired();
			attestations.forEachResendable(attestation -> {
				if (attestation.needsRetry()) {
					writePacket(attestation.packet());
				}
			});
		});
	}

	public void confirmAttestations(List<UUID> uuids) {
		BackgroundExecutors.execute(() -> attestations.confirm(uuids));
	}

	public boolean active() {
		return started && channel != null && channel.isActive();
	}

	public void reset() {
		cloudToken = null;
		channel = null;
		protocol = new ProtocolSpecification();
		compressionAlgorithm = CompressionAlgorithms.initial();
		started = false;
		pendingIncoming.clear();
		pendingOutgoing.clear();
		userToPlayerId.clear();
		playerIdToUser.clear();
		requestedPlayerIds.clear();
		pendingPlayerLogouts.clear();
		locallyOnlineUsers.clear();
		pendingPlayerPackets.clear();
	}

	public synchronized void sendUserPacket(User user, LongFunction<? extends Packet<Serverbound>> packetGenerator) {
		if (!user.hasPlayer()) {
			return;
		}
		UUID userId = user.id();
		if (hasUserId(userId)) {
			long playerId = playerIdByUser(userId);
			sendPacket(packetGenerator.apply(playerId));
		} else {
			requestPlayerId(user, userId);
			pendingPlayerPackets.computeIfAbsent(userId, key -> new ArrayDeque<>()).add(packetGenerator);
		}
	}

	public synchronized void announceUser(User user) {
		if (!user.hasPlayer()) {
			return;
		}
		UUID userId = user.id();
		locallyOnlineUsers.add(userId);
		if (!hasUserId(userId)) {
			requestPlayerId(user, userId);
		}
	}

	public synchronized void sendUserLogout(User user) {
		UUID userId = user.id();
		locallyOnlineUsers.remove(userId);
		pendingPlayerPackets.remove(userId);
		if (!hasUserId(userId)) {
			if (requestedPlayerIds.contains(userId)) {
				pendingPlayerLogouts.add(userId);
			}
			return;
		}
		long playerId = playerIdByUser(userId);
		sendPacket(new ServerboundPlayerLogout(playerId));
		removeUserId(userId);
	}

	public void sendPacket(Packet<Serverbound> packet) {
		if (!packetSupported(packet)) {
			return;
		}
		System.out.println("Sending serverbound packet '" + packet.name() + "' to cloud");
		if (packet instanceof AttestedPacket) {
			AttestedPacket<?> attestedPacket = (AttestedPacket<?>) packet;
			if (attestedPacket.hasIdempotencyToken()) {
				throw new IllegalArgumentException("AttestedPacket already has an idempotency token");
			}
			//noinspection unchecked
			Attestation attestation = new Attestation((AttestedPacket<Serverbound>) attestedPacket, 3, 60, SECONDS);
			attestations.add(attestation);
			attestedPacket.setIdempotencyToken(attestation.idempotencyKey());
			attestedPacket.setRequestId(attestation.newRequestId());
		}
		writePacket(packet);
	}

	// Just write the packet to wire, no attestation checks, nothing
	public synchronized void writePacket(Packet<Serverbound> packet) {
		if (!started || channel == null || !channel.isActive()) {
			pendingOutgoing.add(packet);
			return;
		}
		flushPendingPackets();
		writeToChannel(packet);
	}

	public long sentBytes() {
		return sentBytes.longValue();
	}

	public long receivedBytes() {
		return receivedBytes.longValue();
	}

	public void receivePacketLater(Packet<Clientbound> packet) {
		pendingIncoming.add(packet);
	}

	public Queue<Packet<Clientbound>> pendingIncoming() {
		return pendingIncoming;
	}

	public synchronized void setEncryption(Cipher downwardDecryption, Cipher upwardEncryption) {
		ChannelPipeline pipeline = channel.pipeline();
		ChannelHandler current = pipeline.get("encryption");

		Encryption encryption = new Encryption(upwardEncryption, sentBytes);
		Decryption decryption = new Decryption(downwardDecryption, receivedBytes);

		if (current == null) {
			pipeline.addAfter("timeout", "encryption", encryption);
			pipeline.addAfter("timeout", "decryption", decryption);

			pipeline.addAfter("decryption", "accumulator", new Accumulator());
			pipeline.addAfter("encryption", "prepender", new Prepender());
		} else {
			pipeline.replace("encryption", "encryption", encryption);
			pipeline.replace("decryption", "decryption", decryption);
		}
	}

	public void setProcessor(ChannelHandler handler) {
		pipeline().replace("processor", "processor", handler);
	}

	public void selectCompressionAlgorithm(String name) {
		CompressionAlgorithm selected = CompressionAlgorithms.fromName(name);
		if (selected.name().equals(compressionAlgorithm.name())) {
			return;
		}
		ChannelPipeline pipeline = pipeline();
		pipeline.replace("decompression", "decompression", new Decompression(256, selected));
		pipeline.replace("compression", "compression", new Compression(256, selected));
		compressionAlgorithm = selected;
	}

	public ChannelPipeline pipeline() {
		return channel.pipeline();
	}

	public CloudToken shard() {
		return cloudToken;
	}

	public void close() {
		if (channel != null) {
			channel.close();
		}
	}

	public boolean canSend(Packet<Serverbound> packet) {
		return active() && protocol.packetAvailable(SERVERBOUND, packet.name());
	}

	public boolean canSend(Class<? extends Packet<Serverbound>> packetClass) {
		return active() && protocol.packetAvailable(SERVERBOUND, PacketRegistry.serverboundName(packetClass));
	}

	public synchronized void subscribeToStarted(Consumer<Void> consumer) {
		if (started) {
			consumer.accept(null);
		} else {
			startupSubscribers.add(consumer);
		}
	}

	public synchronized void markStarted() {
		started = true;
		flushPendingPackets();
		startupSubscribers.forEach(subscriber -> subscriber.accept(null));
		startupSubscribers.clear();
		IntaveLogger.logger().info("Handshake with cloud completed");
	}

	public boolean started() {
		return started;
	}

	public synchronized void subscribeToShutdown(Consumer<Session> consumer) {
		shutdownSubscribers.add(consumer);
	}

	public ProtocolSpecification protocol() {
		return protocol;
	}

	public PublicKey serverPublicKey() {
		return serverPublicKey;
	}

	public void setServerPublicKey(PublicKey serverPublicKey) {
		this.serverPublicKey = serverPublicKey;
	}

	public String encryptionAlgorithm() {
		return encryptionAlgorithm;
	}

	public void setEncryptionAlgorithm(String encryptionAlgorithm) {
		this.encryptionAlgorithm = encryptionAlgorithm;
	}

	public String encryptionScheme() {
		return encryptionScheme;
	}

	public void setEncryptionScheme(String encryptionScheme) {
		this.encryptionScheme = encryptionScheme;
	}

	public Key primaryKey() {
		return primaryKey;
	}

	public void setPrimaryKey(Key aesKey) {
		this.primaryKey = aesKey;
	}

	public byte[] verifyBytes() {
		return verifyBytes;
	}

	public void setVerifyBytes(byte[] verifyBytes) {
		this.verifyBytes = verifyBytes;
	}

	public synchronized @NotNull User userById(long playerId) {
		UUID userId = userIdByPlayerId(playerId);
		return userId == null ? UserRepository.fallback() : UserRepository.userOf(userId);
	}

	public synchronized UUID userIdByPlayerId(long playerId) {
		return playerIdToUser.get(playerId);
	}

	public synchronized long playerIdByUser(User user) {
		return playerIdByUser(user.id());
	}

	private long playerIdByUser(UUID userId) {
		return userToPlayerId.getOrDefault(userId, -1L);
	}

	public synchronized void setUserId(UUID userId, long playerId) {
		if (userToPlayerId.containsKey(userId)) {
			long previousPlayerId = userToPlayerId.get(userId);
			if (previousPlayerId != playerId) {
				playerIdToUser.remove(previousPlayerId);
			}
		}
		UUID previousUserId = playerIdToUser.get(playerId);
		if (previousUserId != null && !previousUserId.equals(userId)) {
			userToPlayerId.remove(previousUserId);
		}
		userToPlayerId.put(userId, playerId);
		playerIdToUser.put(playerId, userId);
		requestedPlayerIds.remove(userId);

		if (pendingPlayerLogouts.remove(userId)) {
			sendPacket(new ServerboundPlayerLogout(playerId));
			removeUserId(userId);
			if (locallyOnlineUsers.contains(userId)) {
				User currentUser = UserRepository.userOf(userId);
				announceUser(currentUser);
			}
			return;
		}

		Queue<LongFunction<? extends Packet<Serverbound>>> pending = pendingPlayerPackets.remove(userId);
		if (pending == null) {
			return;
		}
		LongFunction<? extends Packet<Serverbound>> packetGenerator;
		while ((packetGenerator = pending.poll()) != null) {
			sendPacket(packetGenerator.apply(playerId));
		}
	}

	public synchronized void removeUserId(User user) {
		removeUserId(user.id());
	}

	private void removeUserId(UUID userId) {
		long playerId = userToPlayerId.getOrDefault(userId, -1L);
		userToPlayerId.remove(userId);
		playerIdToUser.remove(playerId);
		requestedPlayerIds.remove(userId);
		pendingPlayerLogouts.remove(userId);
		pendingPlayerPackets.remove(userId);
	}

	public synchronized boolean hasUserId(User user) {
		return hasUserId(user.id());
	}

	private boolean hasUserId(UUID userId) {
		return userToPlayerId.containsKey(userId);
	}

	public synchronized boolean awaitingPlayerId(Identity identity) {
		UUID userId = identity.id();
		return userId != null && requestedPlayerIds.contains(userId);
	}

	public void clarifyUnknownPlayerId(User user, long id) {
		BackgroundExecutors.execute(() -> {
			sendPacket(playerLogin(user, id));
		});
	}

	private void flushPendingPackets() {
		Packet<Serverbound> pendingPacket;
		while ((pendingPacket = pendingOutgoing.poll()) != null) {
			writeToChannel(pendingPacket);
		}
	}

	private void requestPlayerId(User user, UUID userId) {
		if (requestedPlayerIds.add(userId)) {
			try {
				sendPacket(playerLogin(user, -1L));
			} catch (RuntimeException exception) {
				requestedPlayerIds.remove(userId);
				throw exception;
			}
		}
	}

	private void writeToChannel(Packet<Serverbound> packet) {
		if (!packetSupported(packet)) {
			return;
		}
		Channel currentChannel = channel;
		currentChannel.writeAndFlush(packet).addListener(future -> {
			if (!future.isSuccess()) {
				Throwable cause = future.cause();
				IntaveLogger.logger().error("Failed to send serverbound packet '" + packet.name() + "' (version " + packet.version() + ") to cloud; channel active=" + currentChannel.isActive() + ", writable=" + currentChannel.isWritable() + ": " + describeFailure(cause));
				if (cause != null) {
					cause.printStackTrace();
				}
			}
		});
	}

	private boolean packetSupported(Packet<Serverbound> packet) {
		return !protocol.packetIdsKnownFor(SERVERBOUND)
			|| protocol.packetAvailable(SERVERBOUND, packet.name());
	}

	private static Identity identityOf(User user) {
		if (!user.hasPlayer()) {
			throw new IllegalArgumentException("User does not have a player");
		}
		Player player = user.player();
		InetAddress address = null;
		if (IntavePlugin.singletonInstance().cloud().config().privacy().annotateINetAdds()) {
			address = player.getAddress().getAddress();
		}
		return new Identity(player.getUniqueId(), player.getName(), address);
	}

	private static ServerboundPlayerLogin playerLogin(User user, long requestedId) {
		return new ServerboundPlayerLogin(
			identityOf(user),
			requestedId,
			user.protocolVersion(),
			serverVersion()
		);
	}

	private static String serverVersion() {
		MinecraftVersion version = MinecraftVersion.current();
		return version.getMajor() + "." + version.getMinor() + "." + version.getBuild();
	}

	private void notifyShutdownSubscribers() {
		List<Consumer<Session>> subscribers;
		synchronized (this) {
			subscribers = new ArrayList<>(shutdownSubscribers);
			shutdownSubscribers.clear();
		}
		subscribers.forEach(subscriber -> subscriber.accept(this));
	}

	private String endpoint() {
		CloudToken token = cloudToken;
		return token == null ? "<unknown>" : token.domain() + ":" + token.port();
	}

	public static String describeFailure(Throwable throwable) {
		if (throwable == null) {
			return "unknown failure";
		}
		StringBuilder description = new StringBuilder();
		Throwable current = throwable;
		int depth = 0;
		while (current != null && depth++ < 6) {
			if (description.length() > 0) {
				description.append(" caused by ");
			}
			description.append(current.getClass().getSimpleName());
			String message = current.getMessage();
			if (message != null && !message.trim().isEmpty()) {
				description.append(": ").append(message);
			}
			current = current.getCause();
		}
		return description.toString();
	}
}
