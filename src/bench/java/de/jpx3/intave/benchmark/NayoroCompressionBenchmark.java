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
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

public final class NayoroCompressionBenchmark {
	private static final int WARMUP_ROUNDS = 3;
	private static final int MEASUREMENT_ROUNDS = 7;
	private static final double BYTES_PER_MEBIBYTE = 1024.0D * 1024.0D;
	private static final int DEFAULT_DISCOVERY_DEPTH = 5;
	private static final int WRITER_ZSTD_LEVEL = 19;
	private static final int WRITER_FLUSH_THRESHOLD = 128 * 1024;
	private static final int[] ZSTD_LEVELS = {
		-5, -3, -1, 1, Zstd.defaultCompressionLevel(), 5, 7, 9, 12, 15, 19, 22
	};

	private static volatile long sink;

	private NayoroCompressionBenchmark() {
	}

	static void main(String[] args) throws IOException {
		InputDiscovery discovery = InputDiscovery.from(args);
		SampleCorpus corpus = SampleCorpus.load(discovery.files);
		if (corpus.samples.isEmpty()) {
			throw new IllegalArgumentException(
				"No Nayoro samples found. Pass sample files or directories as arguments."
			);
		}

		System.out.println("Nayoro sample compression benchmark");
		System.out.println("  samples: " + formatInteger(corpus.samples.size()));
		System.out.println("  raw payload: " + formatBytes(corpus.uncompressedBytes));
		System.out.println("  discovered inputs: " + formatInteger(discovery.files.size()));
		System.out.println("  skipped non-Nayoro inputs: " + formatInteger(corpus.skippedInputs));
		System.out.println("  warmup rounds: " + WARMUP_ROUNDS);
		System.out.println("  measurement rounds: " + MEASUREMENT_ROUNDS);
		System.out.println();
		System.out.printf(
			Locale.ROOT,
			"%-21s %12s %10s %9s %13s %13s%n",
			"algorithm", "compressed", "ratio", "saved", "encode MiB/s", "decode MiB/s"
		);

		for (CompressionCase compressionCase : compressionCases()) {
			BenchmarkResult result = benchmark(compressionCase, corpus);
			printResult(result);
		}

		printWriterModeResults(corpus);

		System.out.println();
		System.out.println("checksum: " + sink);
	}

	private static void printWriterModeResults(SampleCorpus corpus) throws IOException {
		System.out.println();
		System.out.println("Zstd writer-mode benchmark (level " + WRITER_ZSTD_LEVEL + ")");
		System.out.printf(
			Locale.ROOT,
			"%-21s %12s %10s %9s %13s %13s%n",
			"mode", "compressed", "ratio", "saved", "encode MiB/s", "decode MiB/s"
		);
		for (WriterMode mode : WriterMode.values()) {
			printResult(benchmark(mode, corpus));
		}
	}

	private static List<CompressionCase> compressionCases() {
		List<CompressionCase> cases = new ArrayList<>();
		cases.add(new CompressionCase(
			"zlib 1", () -> new ZlibCompressionAlgorithm(Deflater.BEST_SPEED)
		));
		cases.add(new CompressionCase(
			"zlib (default)", () -> new ZlibCompressionAlgorithm(Deflater.DEFAULT_COMPRESSION)
		));
		cases.add(new CompressionCase(
			"zlib 9", () -> new ZlibCompressionAlgorithm(Deflater.BEST_COMPRESSION)
		));
		for (int level : ZSTD_LEVELS) {
			String name = level == Zstd.defaultCompressionLevel()
				? "zstd " + level + " (default)"
				: "zstd " + level;
			cases.add(new CompressionCase(name, () -> new ZstdCompressionAlgorithm(level)));
		}
		return Collections.unmodifiableList(cases);
	}

	private static BenchmarkResult benchmark(
		CompressionCase compressionCase, SampleCorpus corpus
	) {
		CompressionAlgorithm algorithm = compressionCase.algorithm.get();
		EncodedCorpus encoded = encode(algorithm, corpus.samples);
		verifyRoundTrip(algorithm, corpus.samples, encoded.samples);

		warmUp(compressionCase.algorithm, corpus.samples, encoded.samples);
		long encodeNanos = medianEncodeTime(compressionCase.algorithm, corpus.samples);
		long decodeNanos = medianDecodeTime(compressionCase.algorithm, encoded.samples);

		return new BenchmarkResult(
			compressionCase.name,
			corpus.uncompressedBytes,
			encoded.compressedBytes,
			throughput(corpus.uncompressedBytes, encodeNanos),
			throughput(corpus.uncompressedBytes, decodeNanos)
		);
	}

	private static BenchmarkResult benchmark(
		WriterMode mode, SampleCorpus corpus
	) throws IOException {
		EncodedCorpus encoded = encode(mode, corpus.samples);
		verifyZstdRoundTrip(corpus.samples, encoded.samples);

		warmUp(mode, corpus.samples, encoded.samples);
		long encodeNanos = medianEncodeTime(mode, corpus.samples);
		long decodeNanos = medianZstdDecodeTime(encoded.samples);

		return new BenchmarkResult(
			mode.displayName,
			corpus.uncompressedBytes,
			encoded.compressedBytes,
			throughput(corpus.uncompressedBytes, encodeNanos),
			throughput(corpus.uncompressedBytes, decodeNanos)
		);
	}

	private static void warmUp(
		Supplier<CompressionAlgorithm> algorithmSupplier,
		List<byte[]> rawSamples,
		List<EncodedSample> encodedSamples
	) {
		for (int round = 0; round < WARMUP_ROUNDS; round++) {
			CompressionAlgorithm algorithm = algorithmSupplier.get();
			try (Encoder encoder = algorithm.newEncoder(); Decoder decoder = algorithm.newDecoder()) {
				long checksum = encodeAll(encoder, rawSamples);
				checksum = 31L * checksum + decodeAll(decoder, encodedSamples);
				sink = checksum;
			}
		}
	}

	private static void warmUp(
		WriterMode mode,
		List<byte[]> rawSamples,
		List<EncodedSample> encodedSamples
	) throws IOException {
		for (int round = 0; round < WARMUP_ROUNDS; round++) {
			long checksum = encodeAll(mode, rawSamples);
			checksum = 31L * checksum + decodeZstdAll(encodedSamples);
			sink = checksum;
		}
	}

	private static long medianEncodeTime(
		Supplier<CompressionAlgorithm> algorithmSupplier, List<byte[]> samples
	) {
		long[] measurements = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < measurements.length; round++) {
			CompressionAlgorithm algorithm = algorithmSupplier.get();
			try (Encoder encoder = algorithm.newEncoder()) {
				long start = System.nanoTime();
				long checksum = encodeAll(encoder, samples);
				measurements[round] = System.nanoTime() - start;
				sink = checksum;
			}
		}
		return median(measurements);
	}

	private static long medianDecodeTime(
		Supplier<CompressionAlgorithm> algorithmSupplier, List<EncodedSample> samples
	) {
		long[] measurements = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < measurements.length; round++) {
			CompressionAlgorithm algorithm = algorithmSupplier.get();
			try (Decoder decoder = algorithm.newDecoder()) {
				long start = System.nanoTime();
				long checksum = decodeAll(decoder, samples);
				measurements[round] = System.nanoTime() - start;
				sink = checksum;
			}
		}
		return median(measurements);
	}

	private static long medianEncodeTime(
		WriterMode mode, List<byte[]> samples
	) throws IOException {
		long[] measurements = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < measurements.length; round++) {
			long start = System.nanoTime();
			long checksum = encodeAll(mode, samples);
			measurements[round] = System.nanoTime() - start;
			sink = checksum;
		}
		return median(measurements);
	}

	private static long medianZstdDecodeTime(
		List<EncodedSample> samples
	) throws IOException {
		long[] measurements = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < measurements.length; round++) {
			long start = System.nanoTime();
			long checksum = decodeZstdAll(samples);
			measurements[round] = System.nanoTime() - start;
			sink = checksum;
		}
		return median(measurements);
	}

	private static long encodeAll(Encoder encoder, List<byte[]> samples) {
		long checksum = 1L;
		for (byte[] sample : samples) {
			byte[] compressed = encoder.encode(sample);
			checksum = 31L * checksum + compressed.length;
			if (compressed.length > 0) {
				checksum = 31L * checksum + compressed[0];
			}
		}
		return checksum;
	}

	private static long decodeAll(Decoder decoder, List<EncodedSample> samples) {
		long checksum = 1L;
		for (EncodedSample sample : samples) {
			byte[] decompressed = decoder.decode(sample.compressed, sample.uncompressedSize);
			checksum = 31L * checksum + decompressed.length;
			if (decompressed.length > 0) {
				checksum = 31L * checksum + decompressed[decompressed.length - 1];
			}
		}
		return checksum;
	}

	private static long encodeAll(
		WriterMode mode, List<byte[]> samples
	) throws IOException {
		long checksum = 1L;
		for (byte[] sample : samples) {
			byte[] compressed = encode(mode, sample);
			checksum = 31L * checksum + compressed.length;
			if (compressed.length > 0) {
				checksum = 31L * checksum + compressed[0];
			}
		}
		return checksum;
	}

	private static long decodeZstdAll(List<EncodedSample> samples) throws IOException {
		long checksum = 1L;
		for (EncodedSample sample : samples) {
			byte[] decompressed = decodeZstd(sample.compressed);
			checksum = 31L * checksum + decompressed.length;
			if (decompressed.length > 0) {
				checksum = 31L * checksum + decompressed[decompressed.length - 1];
			}
		}
		return checksum;
	}

	private static EncodedCorpus encode(
		CompressionAlgorithm algorithm, List<byte[]> samples
	) {
		List<EncodedSample> encoded = new ArrayList<>(samples.size());
		long compressedBytes = 0L;
		try (Encoder encoder = algorithm.newEncoder()) {
			for (byte[] sample : samples) {
				byte[] compressed = encoder.encode(sample);
				encoded.add(new EncodedSample(compressed, sample.length));
				compressedBytes += compressed.length;
			}
		}
		return new EncodedCorpus(Collections.unmodifiableList(encoded), compressedBytes);
	}

	private static EncodedCorpus encode(
		WriterMode mode, List<byte[]> samples
	) throws IOException {
		List<EncodedSample> encoded = new ArrayList<>(samples.size());
		long compressedBytes = 0L;
		for (byte[] sample : samples) {
			byte[] compressed = encode(mode, sample);
			encoded.add(new EncodedSample(compressed, sample.length));
			compressedBytes += compressed.length;
		}
		return new EncodedCorpus(Collections.unmodifiableList(encoded), compressedBytes);
	}

	private static byte[] encode(WriterMode mode, byte[] sample) throws IOException {
		if (mode == WriterMode.POST_RECORDING) {
			return Zstd.compress(sample, WRITER_ZSTD_LEVEL);
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream(sample.length);
		try (
			ZstdOutputStream compressed = new ZstdOutputStream(
				new BufferedOutputStream(output, 8192), WRITER_ZSTD_LEVEL
			)
		) {
			writeStreamingSample(compressed, sample, mode);
		}
		return output.toByteArray();
	}

	private static void writeStreamingSample(
		ZstdOutputStream output, byte[] sample, WriterMode mode
	) throws IOException {
		if (mode == WriterMode.CLOSE_ONLY) {
			output.write(sample);
			return;
		}

		int lineStart = 0;
		int bytesSinceFlush = 0;
		for (int index = 0; index < sample.length; index++) {
			if (sample[index] != '\n') {
				continue;
			}
			int length = index + 1 - lineStart;
			output.write(sample, lineStart, length);
			bytesSinceFlush += length;
			lineStart = index + 1;
			if (mode == WriterMode.EVENT_FLUSH || bytesSinceFlush >= WRITER_FLUSH_THRESHOLD) {
				output.flush();
				bytesSinceFlush = 0;
			}
		}
		if (lineStart < sample.length) {
			output.write(sample, lineStart, sample.length - lineStart);
		}
	}

	private static void verifyRoundTrip(
		CompressionAlgorithm algorithm,
		List<byte[]> rawSamples,
		List<EncodedSample> encodedSamples
	) {
		try (Decoder decoder = algorithm.newDecoder()) {
			for (int index = 0; index < rawSamples.size(); index++) {
				EncodedSample encoded = encodedSamples.get(index);
				byte[] decoded = decoder.decode(encoded.compressed, encoded.uncompressedSize);
				if (!Arrays.equals(rawSamples.get(index), decoded)) {
					throw new IllegalStateException(
						algorithm.name() + " failed round-trip verification for sample " + index
					);
				}
			}
		}
	}

	private static void verifyZstdRoundTrip(
		List<byte[]> rawSamples, List<EncodedSample> encodedSamples
	) throws IOException {
		for (int index = 0; index < rawSamples.size(); index++) {
			byte[] decoded = decodeZstd(encodedSamples.get(index).compressed);
			if (!Arrays.equals(rawSamples.get(index), decoded)) {
				throw new IllegalStateException(
					"Zstd failed round-trip verification for writer-mode sample " + index
				);
			}
		}
	}

	private static void printResult(BenchmarkResult result) {
		double ratio = result.uncompressedBytes / (double) result.compressedBytes;
		double savings = 1.0D - result.compressedBytes / (double) result.uncompressedBytes;
		System.out.printf(
			Locale.ROOT,
			"%-21s %12s %9.2fx %8.1f%% %,13.1f %,13.1f%n",
			result.algorithm,
			formatBytes(result.compressedBytes),
			ratio,
			savings * 100.0D,
			result.encodeMebibytesPerSecond,
			result.decodeMebibytesPerSecond
		);
	}

	private static long median(long[] measurements) {
		Arrays.sort(measurements);
		return measurements[measurements.length / 2];
	}

	private static double throughput(long bytes, long nanos) {
		return bytes / BYTES_PER_MEBIBYTE / (nanos / 1_000_000_000.0D);
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

	private static byte[] uncompressedBytess(byte[] storedBytes) throws IOException {
		if (isZstd(storedBytes)) {
			return decodeZstd(storedBytes);
		}
		if (isZlib(storedBytes)) {
			try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(storedBytes))) {
				return readAllBytes(input);
			}
		}
		return storedBytes;
	}

	private static byte[] decodeZstd(byte[] bytes) throws IOException {
		try (InputStream input = new ZstdInputStream(new ByteArrayInputStream(bytes))) {
			return readAllBytes(input);
		}
	}

	private static boolean isZstd(byte[] bytes) {
		return bytes.length >= 4 &&
			(bytes[0] & 0xff) == 0x28 &&
			(bytes[1] & 0xff) == 0xb5 &&
			(bytes[2] & 0xff) == 0x2f &&
			(bytes[3] & 0xff) == 0xfd;
	}

	private static boolean isZlib(byte[] bytes) {
		if (bytes.length < 2) {
			return false;
		}
		int first = bytes[0] & 0xff;
		int second = bytes[1] & 0xff;
		return (first & 0x0f) == 8 && ((first << 8) + second) % 31 == 0;
	}

	private static boolean isNayoroJson(byte[] bytes) {
		int offset = 0;
		if (bytes.length >= 3 &&
			(bytes[0] & 0xff) == 0xef &&
			(bytes[1] & 0xff) == 0xbb &&
			(bytes[2] & 0xff) == 0xbf) {
			offset = 3;
		}
		while (offset < bytes.length && Character.isWhitespace((char) (bytes[offset] & 0xff))) {
			offset++;
		}
		if (offset >= bytes.length || bytes[offset] != '{') {
			return false;
		}
		int prefixLength = Math.min(bytes.length - offset, 512);
		String prefix = new String(bytes, offset, prefixLength, StandardCharsets.UTF_8);
		return prefix.contains("\"type\":\"header\"") &&
			prefix.contains("\"data\":{");
	}

	private static byte[] readAllBytes(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static boolean isSamplePath(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".sample") ||
			name.endsWith(".nayoro") ||
			name.endsWith(".jsonl") ||
			name.endsWith(".jsonl.gz") ||
			name.endsWith(".jsonl.zst");
	}

	private record InputDiscovery(List<Path> files) {
		private static InputDiscovery from(String[] arguments) throws IOException {
				Set<Path> discovered = new LinkedHashSet<>();
				if (arguments.length == 0) {
					for (Path path : defaultInputs()) {
						add(path, DEFAULT_DISCOVERY_DEPTH, false, discovered);
					}
				} else {
					for (String argument : arguments) {
						add(Paths.get(argument), Integer.MAX_VALUE, true, discovered);
					}
				}
				List<Path> files = new ArrayList<>(discovered);
				files.sort(Comparator.comparing(Path::toString));
				return new InputDiscovery(Collections.unmodifiableList(files));
			}

			private static List<Path> defaultInputs() {
				List<Path> paths = new ArrayList<>();
				paths.add(Paths.get("runs"));
				String appData = System.getenv("APPDATA");
				if (appData != null && !appData.isEmpty()) {
					paths.add(Paths.get(appData, "Intave", "Samples"));
				} else {
					paths.add(Paths.get(System.getProperty("user.home"), ".intave", "samples"));
				}
				return paths;
			}

			private static void add(
				Path input,
				int maximumDepth,
				boolean failIfMissing,
				Set<Path> output
			) throws IOException {
				Path normalized = input.toAbsolutePath().normalize();
				if (Files.isRegularFile(normalized)) {
					output.add(normalized);
					return;
				}
				if (!Files.isDirectory(normalized)) {
					if (failIfMissing) {
						throw new IllegalArgumentException("Sample input does not exist: " + normalized);
					}
					return;
				}
				try (Stream<Path> paths = Files.walk(normalized, maximumDepth)) {
					paths.filter(Files::isRegularFile)
						.filter(NayoroCompressionBenchmark::isSamplePath)
						.map(path -> path.toAbsolutePath().normalize())
						.forEach(output::add);
				}
			}
		}

	private record SampleCorpus(
		List<byte[]> samples, long uncompressedBytes, int skippedInputs
	) {
		private static SampleCorpus load(List<Path> paths) throws IOException {
				List<byte[]> samples = new ArrayList<>();
				long uncompressedBytes = 0L;
				int skippedInputs = 0;
				for (Path path : paths) {
					byte[] sample = uncompressedBytess(Files.readAllBytes(path));
					if (!isNayoroJson(sample)) {
						skippedInputs++;
						continue;
					}
					samples.add(sample);
					uncompressedBytes += sample.length;
				}
				return new SampleCorpus(
					Collections.unmodifiableList(samples), uncompressedBytes, skippedInputs
				);
			}
		}

	private record CompressionCase(String name, Supplier<CompressionAlgorithm> algorithm) {
	}

	private enum WriterMode {
		EVENT_FLUSH("stream/event flush"),
		BATCHED_FLUSH("stream/128-KiB flush"),
		CLOSE_ONLY("stream/close only"),
		POST_RECORDING("post-recording");

		private final String displayName;

		WriterMode(String displayName) {
			this.displayName = displayName;
		}
	}

	private record EncodedSample(byte[] compressed, int uncompressedSize) {
	}

	private record EncodedCorpus(List<EncodedSample> samples, long compressedBytes) {
	}

	private record BenchmarkResult(String algorithm, long uncompressedBytes, long compressedBytes,
	                               double encodeMebibytesPerSecond, double decodeMebibytesPerSecond) {
	}
}
