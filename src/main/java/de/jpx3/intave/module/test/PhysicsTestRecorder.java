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

package de.jpx3.intave.module.test;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.cloud.PhysicsRecordingUpload;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.test.record.MovementFrameState;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.module.test.record.RollingMovementRecording;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DeflaterOutputStream;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public final class PhysicsTestRecorder extends Module {
	private static final int AUTOMATIC_SEGMENT_FRAMES = 1000;
	private static final int AUTOMATIC_OVERLAP_FRAMES = 20;
	private static final int AUTOMATIC_UPLOAD_COOLDOWN_FRAMES = 1000;
	private static final double MAJOR_TELEPORT_DISTANCE_SQUARED = 8 * 8;

	private final UserLocal<AtomicBoolean> recording = UserLocal.withInitial(new AtomicBoolean(false));
	private final UserLocal<MovementRecording> recordingData = UserLocal.withInitial(MovementRecording::createFor);
	private final UserLocal<RollingMovementRecording> automaticRecording = UserLocal.withInitial(user -> new RollingMovementRecording(user.protocolVersion(), MinecraftVersion.current(), AUTOMATIC_SEGMENT_FRAMES, AUTOMATIC_OVERLAP_FRAMES, AUTOMATIC_UPLOAD_COOLDOWN_FRAMES));
	private final UserLocal<AtomicBoolean> automaticEligibility = UserLocal.withInitial(() -> new AtomicBoolean(false));

	@PacketSubscription(packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
	public void on(User user, PlayerMoveReader reader) {
		boolean manual = isRecording(user);
		boolean automatic = updateAutomaticEligibility(user);
		if (!manual && !automatic) {
			return;
		}

		Position packetPosition = reader.position();
		Rotation packetRotation = reader.rotation();
		MovementMetadata movement = user.meta().movement();
		BoundingBox boundingBox = movement.boundingBox();
		Input input = Input.none();
		if (MinecraftVersions.VER1_21_3.atOrAbove() && user.meta().protocol().sendsInputs()) {
			input = movement.input;
		}
		input = input.overrideFromPartial(Input.partialFrom(movement));
		MovementFrameState frameState = MovementFrameState.capture(user);

		if (manual) {
			MovementRecording manualRecording = recordingData.get(user);
			Position position = packetPosition;
			Rotation rotation = packetRotation;
			if (position == null && !manualRecording.firstPositionHasBeenSent()) {
				position = movement.position();
			}
			if (rotation == null && !manualRecording.firstRotationHasBeenSent()) {
				rotation = movement.rotation();
			}
			manualRecording.insertFrame(
				boundingBox, input,
				position, rotation,
				user.blockCache(),
				user.meta().abilities().attributeSnapshot(),
				movement.gliding,
				movement.pose(),
				frameState
			);
			ActionBar.sendActionBar(user.player(), manualRecording.frameCount() + " frames, " + manualRecording.actions().size() + " actions, " + manualRecording.collisionShapes().size() + " block-types");
		}

		if (automatic) {
			RollingMovementRecording rolling = automaticRecording.get(user);
			Position position = packetPosition;
			Rotation rotation = packetRotation;
			if (position == null && rolling.needsPositionSeed()) {
				position = movement.position();
			}
			if (rotation == null && rolling.needsRotationSeed()) {
				rotation = movement.rotation();
			}
			rolling.insertFrame(boundingBox, input, position, rotation, user.blockCache(), user.meta().abilities().attributeSnapshot(), movement.gliding, movement.pose(), frameState);
		}
	}

	public void saveRecordingDataTo(User user, File file) throws IOException {
		MovementRecording movementRecording = recordingData.get(user);
		movementRecording.materializeVelocities();
		Files.write(file.toPath(), compressedBytes(movementRecording), CREATE, TRUNCATE_EXISTING);
		movementRecording.clear();
	}


	public static byte[] compressedBytes(MovementRecording recording) throws IOException {
		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DeflaterOutputStream compressed = new DeflaterOutputStream(bytes)) {
				buffer.readBytes(compressed, buffer.readableBytes());
			}
			return bytes.toByteArray();
		} finally {
			buffer.release();
		}
	}

	public @Nullable VelocityCapture beginVelocity(User user, Motion motion) {
		MovementRecording manualRecording = recordingSessionOf(user);
		MovementRecording.VelocityToken manualVelocity = manualRecording == null ? null : manualRecording.beginVelocity(motion);

		MovementRecording.VelocityToken automaticVelocity = null;
		if (updateAutomaticEligibility(user)) {
			automaticVelocity = automaticRecording.get(user).applyToActive(recording -> recording.beginVelocity(motion));
		}
		return manualVelocity == null && automaticVelocity == null ? null : new VelocityCapture(manualRecording, manualVelocity, automaticVelocity);
	}

	public void completeVelocity(User user, @Nullable VelocityCapture capture) {
		if (capture == null) {
			return;
		}
		if (capture.manualRecording != null && capture.manualVelocity != null) {
			capture.manualRecording.completeVelocity(capture.manualVelocity);
		}
		if (capture.automaticVelocity != null) {
			automaticRecording.get(user).acceptOnActive(recording -> recording.completeVelocity(capture.automaticVelocity));
		}
	}

	public static final class VelocityCapture {
		private final MovementRecording manualRecording;
		private final MovementRecording.VelocityToken manualVelocity;
		private final MovementRecording.VelocityToken automaticVelocity;

		private VelocityCapture(MovementRecording manualRecording, MovementRecording.VelocityToken manualVelocity, MovementRecording.VelocityToken automaticVelocity) {
			this.manualRecording = manualRecording;
			this.manualVelocity = manualVelocity;
			this.automaticVelocity = automaticVelocity;
		}
	}

	public void physicsSetback(User user, String reason, String details, double addedViolationPoints, double violationLevelAfter) {
		if (!automaticRecordingEligible(user) || !plugin.cloud().canUploadPhysicsRecordings()) {
			return;
		}
		MovementRecording snapshot = automaticRecording.get(user).snapshotAndReset();
		if (snapshot == null) {
			return;
		}
		BackgroundExecutors.execute(() -> {
			try {
				byte[] payload = compressedBytes(snapshot);
				plugin.cloud().uploadPhysicsRecording(user, new PhysicsRecordingUpload(snapshot, reason, details, addedViolationPoints, violationLevelAfter, payload));
			} catch (IOException exception) {
				IntaveLogger.logger().error("Unable to encode physics recording " + snapshot.internalId() + ": " + exception.getMessage());
			}
		});
	}

	@BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(PlayerTeleportEvent event) {
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || event.getTo() == null) {
			return;
		}
		Location from = event.getFrom();
		Location to = event.getTo();
		boolean changedWorld = from.getWorld() != to.getWorld();
		if (changedWorld || from.distanceSquared(to) > MAJOR_TELEPORT_DISTANCE_SQUARED) {
			resetAutomaticRecording(UserRepository.userOf(event.getPlayer()));
		}
	}

	@BukkitEventSubscription(priority = EventPriority.MONITOR)
	public void on(PlayerChangedWorldEvent event) {
		resetAutomaticRecording(UserRepository.userOf(event.getPlayer()));
	}

	@BukkitEventSubscription(priority = EventPriority.MONITOR)
	public void on(PlayerRespawnEvent event) {
		resetAutomaticRecording(UserRepository.userOf(event.getPlayer()));
	}

	public @Nullable MovementRecording recordingSessionOf(User user) {
		return isRecording(user) ? recordingData.get(user) : null;
	}

	public void setRecordingStatus(User user, boolean recording) {
		this.recording.get(user).set(recording);
	}

	public boolean isRecording(User user) {
		return recording.get(user).get();
	}

	private boolean updateAutomaticEligibility(User user) {
		boolean eligible = automaticRecordingEligible(user);
		boolean wasEligible = automaticEligibility.get(user).getAndSet(eligible);
		if (wasEligible != eligible) {
			automaticRecording.get(user).reset();
		}
		return eligible;
	}

	private boolean automaticRecordingEligible(User user) {
		if (!user.hasPlayer() || user.trustFactor().atLeast(TrustFactor.BYPASS)) {
			return false;
		}
		Player player = user.player();
		if (player.hasMetadata("intave.testplayer.protocolversion")) {
			return false;
		}
		GameMode gameMode = player.getGameMode();
		return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR;
	}

	private void resetAutomaticRecording(User user) {
		automaticRecording.get(user).reset();
	}
}
