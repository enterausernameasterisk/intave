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

package de.jpx3.intave.cloud.protocol;

import de.jpx3.intave.share.Certificate;
import de.jpx3.intave.share.CertificateStore;
import de.jpx3.intave.share.Result;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;

public final class CloudToken {
	private static final int MAX_DECOMPRESSED_TOKEN_BYTES = 16 * 1024;

	private final String domain;
	private final int port;
	private final byte[] authtoken;

	CloudToken(String domain, int port, byte[] authtoken) {
		this.domain = domain;
		this.port = port;
		this.authtoken = authtoken;
	}

	public String domain() {
		return domain;
	}

	public int port() {
		return port;
	}

	public byte[] token() {
		return authtoken;
	}

	public String toString(Certificate signingCertificate) {
		String tokenString = new String(authtoken, StandardCharsets.UTF_8);
		if (!Arrays.equals(authtoken, tokenString.getBytes(StandardCharsets.UTF_8))) {
			throw new IllegalStateException("Token is not valid UTF-8");
		}
		if (domain.contains(":") || domain.contains(";")) {
			throw new IllegalStateException("Domain contains a cloud token delimiter");
		}
		if (tokenString.contains(":") || tokenString.contains(";")) {
			throw new IllegalStateException("Token contains a cloud token delimiter");
		}

		byte[] connectionDetails = (domain + ";" + port + ";" + tokenString).getBytes(StandardCharsets.UTF_8);
		byte[] signature = signingCertificate.sign(connectionDetails);
		byte[] signedConnectionDetails = new byte[connectionDetails.length + 1 + signature.length];
		System.arraycopy(connectionDetails, 0, signedConnectionDetails, 0, connectionDetails.length);
		signedConnectionDetails[connectionDetails.length] = ':';
		System.arraycopy(signature, 0, signedConnectionDetails, connectionDetails.length + 1, signature.length);
		return "ct_" + getEncoder().encodeToString(compress(signedConnectionDetails));
	}

	public static Result<CloudToken, String> fromString(String cloudToken) {
		return fromString(cloudToken, CertificateStore.CLOUD_CERTIFICATE);
	}

	static Result<CloudToken, String> fromString(String cloudToken, Certificate certificate) {
		Objects.requireNonNull(certificate, "certificate");
		if (!cloudToken.startsWith("ct_")) {
			return Result.error("Token does not start with ct_");
		}
		String[] parts = cloudToken.split("_");
		if (parts.length != 2) {
			return Result.error("Token does not have 2 parts");
		}
		String secondPart = parts[1];
		byte[] decoded;
		try {
			byte[] compressed = getDecoder().decode(secondPart);
			decoded = decompress(compressed);
		} catch (IllegalArgumentException e) {
			return Result.error("Token is not valid base 64");
		} catch (DataFormatException e) {
			return Result.error("Token is not valid compressed data");
		}
		int signatureSeparator = indexOf(decoded, (byte) ':');
		if (signatureSeparator < 0) {
			return Result.error("Decoded token does not have 4 parts");
		}
		byte[] connectionDetails = Arrays.copyOfRange(decoded, 0, signatureSeparator);
		byte[] certificateSignature = Arrays.copyOfRange(decoded, signatureSeparator + 1, decoded.length);
		String domainPortAndToken = new String(connectionDetails, StandardCharsets.UTF_8);
		String[] domainPortAndTokenParts = domainPortAndToken.split(";", -1);
		if (domainPortAndTokenParts.length != 3) {
			return Result.error("Decoded token does not have 3 parts");
		}
		if (!certificate.verify(connectionDetails, certificateSignature)) {
			return Result.error("Token signature is invalid");
		}
		String domain = domainPortAndTokenParts[0];
		int port;
		try {
			port = Integer.parseInt(domainPortAndTokenParts[1]);
		} catch (NumberFormatException e) {
			return Result.error("Port is not a valid integer");
		}
		byte[] token = domainPortAndTokenParts[2].getBytes(StandardCharsets.UTF_8);
		return Result.ok(new CloudToken(domain, port, token));
	}

	private static byte[] compress(byte[] input) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		try {
			deflater.setInput(input);
			deflater.finish();
			ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
			byte[] buffer = new byte[512];
			while (!deflater.finished()) {
				int count = deflater.deflate(buffer);
				output.write(buffer, 0, count);
			}
			return output.toByteArray();
		} finally {
			deflater.end();
		}
	}

	private static byte[] decompress(byte[] input) throws DataFormatException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(input);
			ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
			byte[] buffer = new byte[512];
			while (!inflater.finished()) {
				int count = inflater.inflate(buffer);
				if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					throw new DataFormatException("Compressed cloud token is incomplete");
				}
				if (output.size() + count > MAX_DECOMPRESSED_TOKEN_BYTES) {
					throw new DataFormatException("Compressed cloud token is too large");
				}
				output.write(buffer, 0, count);
			}
			if (inflater.getRemaining() != 0) {
				throw new DataFormatException("Compressed cloud token has trailing data");
			}
			return output.toByteArray();
		} finally {
			inflater.end();
		}
	}

	private static int indexOf(byte[] bytes, byte value) {
		for (int index = 0; index < bytes.length; index++) {
			if (bytes[index] == value) {
				return index;
			}
		}
		return -1;
	}

}
