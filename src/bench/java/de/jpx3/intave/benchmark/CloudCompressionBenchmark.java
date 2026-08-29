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

package de.jpx3.intave.benchmark;

import ac.intave.cloud.protocol.compression.CompressionAlgorithm;
import ac.intave.cloud.protocol.compression.CompressionAlgorithm.Decoder;
import ac.intave.cloud.protocol.compression.CompressionAlgorithm.Encoder;
import ac.intave.cloud.protocol.compression.ZlibCompressionAlgorithm;
import ac.intave.cloud.protocol.compression.ZstdCompressionAlgorithm;
import com.github.luben.zstd.Zstd;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Deflater;

public final class CloudCompressionBenchmark {
	private static final int PACKET_COUNT = 20_000;
	private static final int[] BATCH_SIZES = {
		1 * 1024, 2 * 1024, 4 * 1024, 8 * 1024,
		16 * 1024, 32 * 1024, 64 * 1024, 128 * 1024,
		256 * 1024, 512 * 1024, 1024 * 1024
	};
	private static final int ZSTD_LEVEL_BATCH_SIZE = 128 * 1024;
	private static final int[] ZSTD_LEVELS = {
		-5, -3, -1, 1, 3, 5, 7, 9, 12, 15, 19, 22
	};
	private static final int WARMUP_ROUNDS = 3;
	private static final int MEASUREMENT_ROUNDS = 7;
	private static final double BYTES_PER_MEBIBYTE = 1024.0D * 1024.0D;

	private static final String[] CHECKS = {
		"MOVEMENT", "ATTACK_RAYTRACE", "HEURISTICS", "CLICK_PATTERN", "PLACEMENT"
	};
	private static final String[] DETAILS = {
		"movement sample exceeded the expected physical bounds",
		"attack direction did not intersect the recorded target bounds",
		"input sequence differs from the expected human distribution",
		"click interval distribution contains a repeating low-variance pattern",
		"placement rotation and target sequence require cloud clarification"
	};

	private static volatile long sink;

	private CloudCompressionBenchmark() {
	}

	static void main(String[] args) {
		PacketCorpus corpus = PacketCorpus.create();
		List<PayloadLayout> layouts = payloadLayouts(corpus);
		List<CompressionAlgorithm> algorithms = compressionAlgorithms();

		System.out.println("Cloud JSON compression benchmark");
		System.out.println("  packets: " + formatInteger(PACKET_COUNT));
		System.out.println("  uncompressed payload: " + formatBytes(corpus.uncompressedBytes));
		System.out.println("  batch sizes: 1 KiB through 1 MiB (powers of two)");
		System.out.println("  warmup rounds: " + WARMUP_ROUNDS);
		System.out.println("  measurement rounds: " + MEASUREMENT_ROUNDS);
		System.out.println();
		System.out.printf(
			Locale.ROOT,
			"%-14s %-16s %8s %12s %10s %9s %13s %13s%n",
			"algorithm", "layout", "frames", "compressed", "ratio", "saved", "encode MiB/s", "decode MiB/s"
		);

		for (CompressionAlgorithm algorithm : algorithms) {
			for (PayloadLayout layout : layouts) {
				BenchmarkResult result = benchmark(algorithm, layout);
				printResult(result);
			}
		}

		printZstdLevelAblation(corpus);
		System.out.println();
		System.out.println("checksum: " + sink);
	}

	private static void printZstdLevelAblation(PacketCorpus corpus) {
		PayloadLayout layout = PayloadLayout.batched(corpus, ZSTD_LEVEL_BATCH_SIZE);
		System.out.println();
		System.out.println("Zstd compression-level ablation (128-KiB batches)");
		System.out.printf(
			Locale.ROOT,
			"%-11s %8s %12s %10s %9s %13s %13s%n",
			"level", "frames", "compressed", "ratio", "saved", "encode MiB/s", "decode MiB/s"
		);
		for (int level : ZSTD_LEVELS) {
			BenchmarkResult result = benchmark(new ZstdCompressionAlgorithm(level), layout);
			printZstdLevelResult(level, result);
		}
	}

	private static List<PayloadLayout> payloadLayouts(PacketCorpus corpus) {
		List<PayloadLayout> layouts = new ArrayList<>(BATCH_SIZES.length + 1);
		layouts.add(PayloadLayout.onePacketPerFrame(corpus));
		for (int batchSize : BATCH_SIZES) {
			layouts.add(PayloadLayout.batched(corpus, batchSize));
		}
		return layouts;
	}

	private static List<CompressionAlgorithm> compressionAlgorithms() {
		return Arrays.asList(
			new ZlibCompressionAlgorithm(Deflater.DEFAULT_COMPRESSION),
			new ZstdCompressionAlgorithm(Zstd.defaultCompressionLevel())
		);
	}

	private static BenchmarkResult benchmark(
		CompressionAlgorithm algorithm, PayloadLayout layout
	) {
		EncodedPayload encoded = encode(algorithm, layout.blocks);
		verifyRoundTrip(algorithm, layout.blocks, encoded.blocks);

		warmUp(algorithm, layout.blocks, encoded.blocks);
		long encodeNanos = medianEncodeTime(algorithm, layout.blocks);
		long decodeNanos = medianDecodeTime(algorithm, encoded.blocks);

		return new BenchmarkResult(
			algorithm.name(), layout.name, layout.blocks.size(),
			layout.uncompressedBytes, encoded.compressedBytes,
			throughput(layout.uncompressedBytes, encodeNanos),
			throughput(layout.uncompressedBytes, decodeNanos)
		);
	}

	private static void warmUp(
		CompressionAlgorithm algorithm, List<byte[]> rawBlocks,
		List<EncodedBlock> encodedBlocks
	) {
		try (
			Encoder encoder = algorithm.newEncoder();
			Decoder decoder = algorithm.newDecoder()
		) {
			for (int round = 0; round < WARMUP_ROUNDS; round++) {
				long checksum = encodeAll(encoder, rawBlocks);
				checksum = 31L * checksum + decodeAll(decoder, encodedBlocks);
				sink = checksum;
			}
		}
	}

	private static long medianEncodeTime(
		CompressionAlgorithm algorithm, List<byte[]> blocks
	) {
		long[] samples = new long[MEASUREMENT_ROUNDS];
		try (Encoder encoder = algorithm.newEncoder()) {
			for (int round = 0; round < samples.length; round++) {
				long start = System.nanoTime();
				long checksum = encodeAll(encoder, blocks);
				samples[round] = System.nanoTime() - start;
				sink = checksum;
			}
		}
		return median(samples);
	}

	private static long medianDecodeTime(
		CompressionAlgorithm algorithm, List<EncodedBlock> blocks
	) {
		long[] samples = new long[MEASUREMENT_ROUNDS];
		try (Decoder decoder = algorithm.newDecoder()) {
			for (int round = 0; round < samples.length; round++) {
				long start = System.nanoTime();
				long checksum = decodeAll(decoder, blocks);
				samples[round] = System.nanoTime() - start;
				sink = checksum;
			}
		}
		return median(samples);
	}

	private static long encodeAll(Encoder encoder, List<byte[]> blocks) {
		long checksum = 1L;
		for (byte[] block : blocks) {
			byte[] encoded = encoder.encode(block);
			checksum = 31L * checksum + encoded.length;
			if (encoded.length > 0) {
				checksum = 31L * checksum + encoded[0];
			}
		}
		return checksum;
	}

	private static long decodeAll(Decoder decoder, List<EncodedBlock> blocks) {
		long checksum = 1L;
		for (EncodedBlock block : blocks) {
			byte[] decoded = decoder.decode(block.compressed, block.uncompressedSize);
			checksum = 31L * checksum + decoded.length;
			if (decoded.length > 0) {
				checksum = 31L * checksum + decoded[decoded.length - 1];
			}
		}
		return checksum;
	}

	private static EncodedPayload encode(
		CompressionAlgorithm algorithm, List<byte[]> blocks
	) {
		List<EncodedBlock> encoded = new ArrayList<>(blocks.size());
		long compressedBytes = 0L;
		try (Encoder encoder = algorithm.newEncoder()) {
			for (byte[] block : blocks) {
				byte[] compressed = encoder.encode(block);
				encoded.add(new EncodedBlock(compressed, block.length));
				compressedBytes += compressed.length;
			}
		}
		return new EncodedPayload(encoded, compressedBytes);
	}

	private static void verifyRoundTrip(
		CompressionAlgorithm algorithm, List<byte[]> rawBlocks,
		List<EncodedBlock> encodedBlocks
	) {
		try (Decoder decoder = algorithm.newDecoder()) {
			for (int index = 0; index < rawBlocks.size(); index++) {
				EncodedBlock encoded = encodedBlocks.get(index);
				byte[] decoded = decoder.decode(encoded.compressed, encoded.uncompressedSize);
				if (!Arrays.equals(rawBlocks.get(index), decoded)) {
					throw new IllegalStateException(
						algorithm.name() + " failed round-trip verification for frame " + index
					);
				}
			}
		}
	}

	private static void printResult(BenchmarkResult result) {
		double ratio = result.uncompressedBytes / (double) result.compressedBytes;
		double savings = 1.0D - result.compressedBytes / (double) result.uncompressedBytes;
		System.out.printf(
			Locale.ROOT,
			"%-14s %-16s %,8d %12s %9.2fx %8.1f%% %,13.1f %,13.1f%n",
			result.algorithm, result.layout, result.frames,
			formatBytes(result.compressedBytes), ratio, savings * 100.0D,
			result.encodeMebibytesPerSecond, result.decodeMebibytesPerSecond
		);
	}

	private static void printZstdLevelResult(int level, BenchmarkResult result) {
		double ratio = result.uncompressedBytes / (double) result.compressedBytes;
		double savings = 1.0D - result.compressedBytes / (double) result.uncompressedBytes;
		String levelName = level == Zstd.defaultCompressionLevel() ? level + " (default)" : Integer.toString(level);
		System.out.printf(
			Locale.ROOT,
			"%-11s %,8d %12s %9.2fx %8.1f%% %,13.1f %,13.1f%n",
			levelName, result.frames, formatBytes(result.compressedBytes),
			ratio, savings * 100.0D,
			result.encodeMebibytesPerSecond, result.decodeMebibytesPerSecond
		);
	}

	private static long median(long[] values) {
		Arrays.sort(values);
		return values[values.length / 2];
	}

	private static double throughput(long bytes, long nanos) {
		return bytes / BYTES_PER_MEBIBYTE / (nanos / 1_000_000_000.0D);
	}

	private static String jsonPacket(int index) {
		int player = index & 511;
		int checkIndex = index % CHECKS.length;
		double x = 128.0D + (index % 10_000) * 0.03125D;
		double y = 64.0D + (index & 15) * 0.0625D;
		double z = -96.0D - (index % 8_000) * 0.015625D;
		return new StringBuilder(512)
			.append('{')
			.append("\"id\":").append(index).append(',')
			.append("\"timestamp\":").append(1_750_000_000_000L + index * 50L).append(',')
			.append("\"requestId\":\"00000000-0000-0000-0000-")
			.append(decimalWithLeadingZeroes(index, 12)).append("\",")
			.append("\"player\":{")
			.append("\"id\":").append(player).append(',')
			.append("\"name\":\"Player").append(player).append("\",")
			.append("\"clientVersion\":\"1.21.4\"},")
			.append("\"check\":\"").append(CHECKS[checkIndex]).append("\",")
			.append("\"violationLevel\":").append(index % 250).append(',')
			.append("\"position\":{")
			.append("\"x\":").append(x).append(',')
			.append("\"y\":").append(y).append(',')
			.append("\"z\":").append(z).append("},")
			.append("\"rotation\":{")
			.append("\"yaw\":").append((index * 13.37D) % 360.0D).append(',')
			.append("\"pitch\":").append((index % 181) - 90).append("},")
			.append("\"environment\":{")
			.append("\"world\":\"world\",")
			.append("\"gameMode\":\"SURVIVAL\",")
			.append("\"onGround\":").append((index & 1) == 0).append("},")
			.append("\"details\":{")
			.append("\"message\":\"").append(DETAILS[checkIndex]).append("\",")
			.append("\"sample\":").append(index & 127).append(',')
			.append("\"tags\":[\"cloud\",\"analysis\",\"json\"]}}")
			.toString();
	}

	private static String decimalWithLeadingZeroes(int value, int width) {
		String decimal = Integer.toString(value);
		StringBuilder result = new StringBuilder(width);
		for (int index = decimal.length(); index < width; index++) {
			result.append('0');
		}
		return result.append(decimal).toString();
	}

	private static String formatBytes(long bytes) {
		if (bytes >= 1024L * 1024L) {
			return String.format(Locale.ROOT, "%.2f MiB", bytes / BYTES_PER_MEBIBYTE);
		}
		if (bytes >= 1024L) {
			return String.format(Locale.ROOT, "%.2f KiB", bytes / 1024.0D);
		}
		return bytes + " B";
	}

	private static String formatInteger(int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static final class PacketCorpus {
		private final List<byte[]> packets;
		private final long uncompressedBytes;

		private PacketCorpus(List<byte[]> packets, long uncompressedBytes) {
			this.packets = packets;
			this.uncompressedBytes = uncompressedBytes;
		}

		private static PacketCorpus create() {
			List<byte[]> packets = new ArrayList<>(PACKET_COUNT);
			long bytes = 0L;
			for (int index = 0; index < PACKET_COUNT; index++) {
				byte[] json = jsonPacket(index).getBytes(StandardCharsets.UTF_8);
				if (json.length > 0xFFFF) {
					throw new IllegalStateException("Generated JSON packet exceeds the protocol string limit");
				}
				byte[] framedPacket = ByteBuffer.allocate(Byte.BYTES + Short.BYTES + json.length + Byte.BYTES)
					.put((byte) (index % CHECKS.length))
					.putShort((short) json.length)
					.put(json)
					.put((byte) -1)
					.array();
				packets.add(framedPacket);
				bytes += framedPacket.length;
			}
			return new PacketCorpus(Collections.unmodifiableList(packets), bytes);
		}
	}

	private static final class PayloadLayout {
		private final String name;
		private final List<byte[]> blocks;
		private final long uncompressedBytes;

		private PayloadLayout(String name, List<byte[]> blocks, long uncompressedBytes) {
			this.name = name;
			this.blocks = blocks;
			this.uncompressedBytes = uncompressedBytes;
		}

		private static PayloadLayout onePacketPerFrame(PacketCorpus corpus) {
			return new PayloadLayout("per-packet", corpus.packets, corpus.uncompressedBytes);
		}

		private static PayloadLayout batched(PacketCorpus corpus, int maximumBatchSize) {
			List<byte[]> batches = new ArrayList<>();
			ByteArrayOutputStream batch = new ByteArrayOutputStream(maximumBatchSize);
			for (byte[] packet : corpus.packets) {
				if (batch.size() > 0 && batch.size() + packet.length > maximumBatchSize) {
					batches.add(batch.toByteArray());
					batch.reset();
				}
				batch.write(packet, 0, packet.length);
			}
			if (batch.size() > 0) {
				batches.add(batch.toByteArray());
			}
			return new PayloadLayout(
				(maximumBatchSize / 1024) + "-KiB batches",
				Collections.unmodifiableList(batches), corpus.uncompressedBytes
			);
		}
	}

	private static final class EncodedBlock {
		private final byte[] compressed;
		private final int uncompressedSize;

		private EncodedBlock(byte[] compressed, int uncompressedSize) {
			this.compressed = compressed;
			this.uncompressedSize = uncompressedSize;
		}
	}

	private static final class EncodedPayload {
		private final List<EncodedBlock> blocks;
		private final long compressedBytes;

		private EncodedPayload(List<EncodedBlock> blocks, long compressedBytes) {
			this.blocks = blocks;
			this.compressedBytes = compressedBytes;
		}
	}

	private static final class BenchmarkResult {
		private final String algorithm;
		private final String layout;
		private final int frames;
		private final long uncompressedBytes;
		private final long compressedBytes;
		private final double encodeMebibytesPerSecond;
		private final double decodeMebibytesPerSecond;

		private BenchmarkResult(
			String algorithm, String layout, int frames,
			long uncompressedBytes, long compressedBytes,
			double encodeMebibytesPerSecond, double decodeMebibytesPerSecond
		) {
			this.algorithm = algorithm;
			this.layout = layout;
			this.frames = frames;
			this.uncompressedBytes = uncompressedBytes;
			this.compressedBytes = compressedBytes;
			this.encodeMebibytesPerSecond = encodeMebibytesPerSecond;
			this.decodeMebibytesPerSecond = decodeMebibytesPerSecond;
		}
	}
}
