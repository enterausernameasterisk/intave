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

package de.jpx3.intave.share;

import de.jpx3.intave.resource.Resource;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

public final class Certificate {
	private static final String KEY_ALGORITHM = "RSA";
	private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
	private static final String PEM_BEGIN_PREFIX = "-----BEGIN ";
	private static final String PEM_BOUNDARY_SUFFIX = "-----";
	private static final String PEM_END_PREFIX = "-----END ";

	private static final int DEFAULT_KEY_SIZE = 3072;
	private static final Duration DEFAULT_VALIDITY = Duration.ofDays(3650);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final PublicKey publicKey;
	private final PrivateKey privateKey;
	private final X509Certificate certificate;

	private Certificate(PublicKey publicKey, PrivateKey privateKey, X509Certificate certificate) {
		this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
		this.privateKey = privateKey;
		this.certificate = certificate;
	}

	/**
	 * Generates a new RSA key pair and a self-signed X.509 certificate.
	 */
	public static Certificate generate() {
		return generate("CN=Intave");
	}

	public static Certificate generate(String distinguishedName) {
		return generate(distinguishedName, DEFAULT_KEY_SIZE, DEFAULT_VALIDITY);
	}

	/**
	 * Generates a new RSA key pair and a self-signed X.509 certificate.
	 *
	 * @param distinguishedName certificate subject
	 * @param keySize           RSA key size; must be 2048, 3072, or 4096
	 * @param validity          certificate validity duration
	 */
	public static Certificate generate(String distinguishedName, int keySize, Duration validity) {
		Objects.requireNonNull(distinguishedName, "distinguishedName");
		Objects.requireNonNull(validity, "validity");

		if (distinguishedName.trim().isEmpty()) {
			throw new IllegalArgumentException("Distinguished name cannot be empty");
		}

		if (validity.isZero() || validity.isNegative()) {
			throw new IllegalArgumentException("Certificate validity must be positive");
		}

		X500Name subject;
		try {
			X500Principal principal = new X500Principal(distinguishedName);
			subject = X500Name.getInstance(principal.getEncoded());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid distinguished name", exception);
		}

		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
			initializeKeyPairGenerator(generator, keySize);
			KeyPair keyPair = generator.generateKeyPair();
			X509Certificate certificate = createSelfSignedCertificate(keyPair, subject, validity);
			return new Certificate(keyPair.getPublic(), keyPair.getPrivate(), certificate);
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not generate RSA certificate", exception);
		}
	}

	private static void initializeKeyPairGenerator(KeyPairGenerator generator, int keySize) {
		switch (keySize) {
			case 2048:
				generator.initialize(2048, SECURE_RANDOM);
				break;
			case 3072:
				generator.initialize(3072, SECURE_RANDOM);
				break;
			case 4096:
				generator.initialize(4096, SECURE_RANDOM);
				break;
			default:
				throw new IllegalArgumentException("RSA key size must be 2048, 3072, or 4096 bits");
		}
	}

	/**
	 * Loads either:
	 * <p>
	 * - an X.509 certificate
	 * - an X.509 SubjectPublicKeyInfo public key
	 * <p>
	 * This creates a verification-only instance because no private key is
	 * loaded.
	 */
	public static Certificate from(Resource publicKeyOrCertificate) {
		Objects.requireNonNull(publicKeyOrCertificate, "publicKeyOrCertificate");
		byte[] encoded = readResource(publicKeyOrCertificate);
		try {
			PublicMaterial publicMaterial = parsePublicMaterial(encoded);
			return new Certificate(publicMaterial.publicKey, null, publicMaterial.certificate);
		} catch (GeneralSecurityException exception) {
			throw new IllegalArgumentException("Resource does not contain a valid RSA public key or certificate", exception);
		}
	}

	/**
	 * Loads a certificate/public key together with its PKCS#8 private key.
	 * <p>
	 * The method verifies that both keys belong to the same key pair.
	 */
	public static Certificate from(Resource publicKeyOrCertificate, Resource privateKeyResource) {
		Objects.requireNonNull(publicKeyOrCertificate, "publicKeyOrCertificate");
		Objects.requireNonNull(privateKeyResource, "privateKeyResource");
		byte[] publicBytes = readResource(publicKeyOrCertificate);
		byte[] privateBytes = readResource(privateKeyResource);
		try {
			PublicMaterial publicMaterial = parsePublicMaterial(publicBytes);
			PrivateKey privateKey = parsePrivateKey(privateBytes);
			ensureMatchingKeyPair(publicMaterial.publicKey, privateKey);
			return new Certificate(publicMaterial.publicKey, privateKey, publicMaterial.certificate);
		} catch (GeneralSecurityException exception) {
			throw new IllegalArgumentException("Could not load the RSA key pair", exception);
		}
	}

	/**
	 * Signs data using this instance's private key.
	 *
	 * @throws IllegalStateException when no private key is loaded
	 */
	public byte[] sign(byte[] data) {
		Objects.requireNonNull(data, "data");
		PrivateKey signingKey = requirePrivateKey();
		try {
			java.security.Signature signer = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
			signer.initSign(signingKey, SECURE_RANDOM);
			signer.update(data);
			return signer.sign();
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not sign data", exception);
		}
	}

	/**
	 * Verifies a detached signature.
	 */
	public boolean verify(byte[] data, byte[] signature) {
		Objects.requireNonNull(data, "data");
		Objects.requireNonNull(signature, "signature");

		try {
			java.security.Signature verifier = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
			verifier.initVerify(publicKey);
			verifier.update(data);
			return verifier.verify(signature);
		} catch (SignatureException exception) {
			// Malformed or invalid signature.
			return false;
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not verify the RSA signature", exception);
		}
	}

	/**
	 * Signs data and returns the signature as Base64.
	 */
	public String signBase64(byte[] data) {
		return Base64.getEncoder().encodeToString(sign(data));
	}

	/**
	 * Verifies a Base64-encoded signature.
	 */
	public boolean verifyBase64(byte[] data, String signature) {
		Objects.requireNonNull(signature, "signature");
		try {
			return verify(data, Base64.getDecoder().decode(signature));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	/**
	 * Saves the public key as PEM:
	 * <p>
	 * -----BEGIN PUBLIC KEY-----
	 */
	public void savePublicKey(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		resource.write(toPem("PUBLIC KEY", publicKey.getEncoded()));
	}

	/**
	 * Saves the public key as X.509 SubjectPublicKeyInfo DER.
	 */
	public void savePublicKeyDer(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		resource.write(publicKey.getEncoded());
	}

	/**
	 * Saves the private key as unencrypted PKCS#8 PEM:
	 * <p>
	 * -----BEGIN PRIVATE KEY-----
	 * <p>
	 * The destination must be protected appropriately.
	 */
	public void savePrivateKey(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		PrivateKey key = requirePrivateKey();
		resource.write(toPem("PRIVATE KEY", key.getEncoded()));
	}

	/**
	 * Saves the private key as unencrypted PKCS#8 DER.
	 */
	public void savePrivateKeyDer(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		resource.write(requirePrivateKey().getEncoded());
	}

	/**
	 * Saves the X.509 certificate as PEM.
	 *
	 * @throws IllegalStateException if this instance was created from only a
	 *                               raw public key
	 */
	public void saveCertificate(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		try {
			resource.write(toPem("CERTIFICATE", requireCertificate().getEncoded()));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not encode certificate", exception);
		}
	}

	/**
	 * Saves the X.509 certificate as DER.
	 */
	public void saveCertificateDer(Resource resource) {
		Objects.requireNonNull(resource, "resource");
		try {
			resource.write(requireCertificate().getEncoded());
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not encode certificate", exception);
		}
	}

	public PublicKey publicKey() {
		return publicKey;
	}

	public Optional<PrivateKey> privateKey() {
		return Optional.ofNullable(privateKey);
	}

	public Optional<X509Certificate> x509Certificate() {
		return Optional.ofNullable(certificate);
	}

	public boolean canSign() {
		return privateKey != null;
	}

	public boolean hasCertificate() {
		return certificate != null;
	}

	private PrivateKey requirePrivateKey() {
		if (privateKey == null) {
			throw new IllegalStateException("No private key is available");
		}

		return privateKey;
	}

	private X509Certificate requireCertificate() {
		if (certificate == null) {
			throw new IllegalStateException("No X.509 certificate is available");
		}

		return certificate;
	}

	private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, X500Name subject, Duration validity) throws Exception {
		Instant now = Instant.now();
		Date notBefore = Date.from(now.minus(Duration.ofMinutes(5)));
		Date notAfter = roundedUpDate(now, validity);
		BigInteger serialNumber = generateSerialNumber();
		JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, serialNumber, notBefore, notAfter, subject, keyPair.getPublic());
		builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
		builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
		ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
		X509CertificateHolder holder = builder.build(contentSigner);
		X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);
		certificate.checkValidity(new Date());
		certificate.verify(keyPair.getPublic());
		return certificate;
	}

	private static Date roundedUpDate(Instant start, Duration duration) {
		try {
			Instant end = start.plus(duration);
			Instant minimumEnd = start.plusSeconds(1);
			if (end.isBefore(minimumEnd)) {
				end = minimumEnd;
			}
			// X.509 UTCTime is second-precision. Round up so every accepted
			// positive Duration creates a certificate that is initially valid.
			if (end.getNano() != 0) {
				end = Instant.ofEpochSecond(Math.addExact(end.getEpochSecond(), 1));
			}
			return Date.from(end);
		} catch (ArithmeticException | DateTimeException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Certificate validity is too large", exception);
		}
	}

	private static BigInteger generateSerialNumber() {
		BigInteger serialNumber;
		do {
			serialNumber = new BigInteger(160, SECURE_RANDOM);
		} while (serialNumber.signum() <= 0);
		return serialNumber;
	}

	private static PublicMaterial parsePublicMaterial(byte[] encoded) throws GeneralSecurityException {
		PemBlock block = decodePem(encoded);
		if ("CERTIFICATE".equals(block.label)) {
			return parseCertificate(block.der);
		}
		if ("PUBLIC KEY".equals(block.label)) {
			return new PublicMaterial(parsePublicKey(block.der), null);
		}
		if (block.label != null) {
			throw new IllegalArgumentException("Unsupported public PEM type: " + block.label);
		}
		// For DER data, first try an X.509 certificate.
		try {
			return parseCertificate(block.der);
		} catch (GeneralSecurityException certificateException) {
			try {
				return new PublicMaterial(parsePublicKey(block.der), null);
			} catch (GeneralSecurityException keyException) {
				keyException.addSuppressed(certificateException);
				throw keyException;
			}
		}
	}

	private static PublicMaterial parseCertificate(byte[] der) throws GeneralSecurityException {
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		ByteArrayInputStream inputStream = new ByteArrayInputStream(der);
		X509Certificate certificate = (X509Certificate) factory.generateCertificate(inputStream);
		if (inputStream.available() != 0) {
			throw new GeneralSecurityException("Certificate contains trailing data");
		}
		PublicKey publicKey = certificate.getPublicKey();
		if (!KEY_ALGORITHM.equalsIgnoreCase(publicKey.getAlgorithm())) {
			throw new GeneralSecurityException("Certificate does not contain an RSA public key");
		}
		return new PublicMaterial(publicKey, certificate);
	}

	private static PublicKey parsePublicKey(byte[] der) throws GeneralSecurityException {
		KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
		PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(der));
		if (!KEY_ALGORITHM.equalsIgnoreCase(publicKey.getAlgorithm())) {
			throw new GeneralSecurityException("Public key is not an RSA key");
		}
		return publicKey;
	}

	private static PrivateKey parsePrivateKey(byte[] encoded) throws GeneralSecurityException {
		PemBlock block = decodePem(encoded);
		if ("RSA PRIVATE KEY".equals(block.label)) {
			throw new IllegalArgumentException("PKCS#1 RSA private keys are not supported. " + "Use a PKCS#8 PRIVATE KEY instead.");
		}
		if (block.label != null && !"PRIVATE KEY".equals(block.label)) {
			throw new IllegalArgumentException("Unsupported private PEM type: " + block.label);
		}
		KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
		PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(block.der));
		if (!KEY_ALGORITHM.equalsIgnoreCase(privateKey.getAlgorithm())) {
			throw new GeneralSecurityException("Private key is not an RSA key");
		}
		return privateKey;
	}

	private static void ensureMatchingKeyPair(PublicKey publicKey, PrivateKey privateKey) throws GeneralSecurityException {
		byte[] challenge = new byte[32];
		SECURE_RANDOM.nextBytes(challenge);
		java.security.Signature signer = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
		signer.initSign(privateKey, SECURE_RANDOM);
		signer.update(challenge);
		byte[] signature = signer.sign();
		java.security.Signature verifier = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
		verifier.initVerify(publicKey);
		verifier.update(challenge);
		if (!verifier.verify(signature)) {
			throw new GeneralSecurityException("The public and private keys do not match");
		}
	}

	private static byte[] readResource(Resource resource) {
		if (!resource.available()) {
			throw new IllegalArgumentException("Resource is not available");
		}
		try {
			byte[] bytes = resource.readAll();
			if (bytes == null || bytes.length == 0) {
				throw new IllegalArgumentException("Resource is empty");
			}
			return bytes;
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read resource", exception);
		}
	}

	private static PemBlock decodePem(byte[] encoded) {
		Objects.requireNonNull(encoded, "encoded");
		String value = new String(encoded, StandardCharsets.US_ASCII).trim();
		if (!value.startsWith("-----BEGIN")) {
			return new PemBlock(null, encoded);
		}

		if (!value.startsWith(PEM_BEGIN_PREFIX)) {
			throw new IllegalArgumentException("Invalid PEM data");
		}

		int labelStart = PEM_BEGIN_PREFIX.length();
		int labelEnd = value.indexOf(PEM_BOUNDARY_SUFFIX, labelStart);
		if (labelEnd < 0) {
			throw new IllegalArgumentException("Invalid PEM data");
		}

		String rawLabel = value.substring(labelStart, labelEnd);
		if (!isValidPemLabel(rawLabel)) {
			throw new IllegalArgumentException("Invalid PEM data");
		}

		int bodyStart = labelEnd + PEM_BOUNDARY_SUFFIX.length();
		String endBoundary = PEM_END_PREFIX + rawLabel + PEM_BOUNDARY_SUFFIX;
		int bodyEnd = value.length() - endBoundary.length();
		if (bodyEnd < bodyStart || !value.startsWith(endBoundary, bodyEnd)) {
			throw new IllegalArgumentException("Invalid PEM data");
		}

		String label = rawLabel.trim();
		String base64 = removePemWhitespace(value, bodyStart, bodyEnd);
		try {
			return new PemBlock(label, Base64.getDecoder().decode(base64));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("PEM resource contains invalid Base64 data", exception);
		}
	}

	private static boolean isValidPemLabel(String label) {
		if (label.isEmpty()) {
			return false;
		}
		for (int index = 0; index < label.length(); index++) {
			char character = label.charAt(index);
			if ((character < 'A' || character > 'Z')
				&& (character < '0' || character > '9')
				&& character != ' ') {
				return false;
			}
		}
		return true;
	}

	private static String removePemWhitespace(String value, int start, int end) {
		StringBuilder result = new StringBuilder(end - start);
		for (int index = start; index < end; index++) {
			char character = value.charAt(index);
			if (character != ' '
				&& character != '\t'
				&& character != '\n'
				&& character != '\u000B'
				&& character != '\f'
				&& character != '\r') {
				result.append(character);
			}
		}
		return result.toString();
	}

	private static byte[] toPem(String label, byte[] der) {
		String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
		String pem = "-----BEGIN " + label + "-----\n" + base64 + "\n" + "-----END " + label + "-----\n";
		return pem.getBytes(StandardCharsets.US_ASCII);
	}

	private static final class PublicMaterial {
		private final PublicKey publicKey;
		private final X509Certificate certificate;

		private PublicMaterial(PublicKey publicKey, X509Certificate certificate) {
			this.publicKey = publicKey;
			this.certificate = certificate;
		}
	}

	private static final class PemBlock {
		private final String label;
		private final byte[] der;

		private PemBlock(String label, byte[] der) {
			this.label = label;
			this.der = der;
		}
	}
}
