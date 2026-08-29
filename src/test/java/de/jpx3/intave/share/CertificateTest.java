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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

final class CertificateTest {
	private static Certificate certificate;
	private static Certificate otherCertificate;

	@BeforeAll
	static void generateCertificates() {
		certificate = Certificate.generate(
			"CN=Certificate Test, O=Intave",
			2048,
			Duration.ofDays(2)
		);
		otherCertificate = Certificate.generate(
			"CN=Other Certificate",
			2048,
			Duration.ofDays(1)
		);
	}

	@Test
	void generateCreatesValidSelfSignedRsaCertificate() throws Exception {
		assertTrue(certificate.canSign());
		assertTrue(certificate.hasCertificate());
		assertTrue(certificate.privateKey().isPresent());
		assertTrue(certificate.x509Certificate().isPresent());
		assertEquals("RSA", certificate.publicKey().getAlgorithm());
		assertEquals(
			2048,
			((RSAPublicKey) certificate.publicKey())
				.getModulus()
				.bitLength()
		);

		X509Certificate x509 = certificate.x509Certificate().get();
		X500Principal expectedName =
			new X500Principal("CN=Certificate Test, O=Intave");
		assertEquals(expectedName, x509.getSubjectX500Principal());
		assertEquals(expectedName, x509.getIssuerX500Principal());
		assertArrayEquals(
			certificate.publicKey().getEncoded(),
			x509.getPublicKey().getEncoded()
		);
		assertEquals(-1, x509.getBasicConstraints());
		assertNotNull(x509.getKeyUsage());
		assertTrue(x509.getKeyUsage()[0], "digitalSignature must be enabled");
		assertFalse(x509.getKeyUsage()[1], "nonRepudiation must be disabled");
		assertTrue(x509.getSerialNumber().signum() > 0);
		assertTrue(x509.getSerialNumber().bitLength() <= 160);
		assertTrue(x509.getNotBefore().before(new Date()));
		assertTrue(x509.getNotAfter().after(new Date()));

		x509.checkValidity();
		x509.verify(certificate.publicKey());
	}

	@Test
	void signaturesAuthenticateOnlyTheOriginalDataAndKey() {
		byte[] data = "signed payload".getBytes(StandardCharsets.UTF_8);
		byte[] modifiedData =
			"modified payload".getBytes(StandardCharsets.UTF_8);

		byte[] signature = certificate.sign(data);

		assertTrue(certificate.verify(data, signature));
		assertFalse(certificate.verify(modifiedData, signature));
		assertFalse(otherCertificate.verify(data, signature));
		assertFalse(certificate.verify(data, new byte[0]));

		byte[] modifiedSignature = signature.clone();
		modifiedSignature[modifiedSignature.length / 2] ^= 1;
		assertFalse(certificate.verify(data, modifiedSignature));

		String base64Signature = certificate.signBase64(data);
		assertTrue(certificate.verifyBase64(data, base64Signature));
		assertFalse(certificate.verifyBase64(modifiedData, base64Signature));
		assertFalse(certificate.verifyBase64(data, "not base64!"));
	}

	@Test
	void certificateAndPrivateKeyRoundTripAsPemAndDer() throws Exception {
		Resource certificatePem = memoryResource();
		Resource privateKeyPem = memoryResource();
		certificate.saveCertificate(certificatePem);
		certificate.savePrivateKey(privateKeyPem);

		String certificateText = certificatePem.readAsString();
		String privateKeyText = privateKeyPem.readAsString();
		assertTrue(certificateText.startsWith(
			"-----BEGIN CERTIFICATE-----\n"
		));
		assertTrue(certificateText.endsWith(
			"\n-----END CERTIFICATE-----"
		));
		assertTrue(privateKeyText.startsWith(
			"-----BEGIN PRIVATE KEY-----\n"
		));
		assertTrue(privateKeyText.endsWith(
			"\n-----END PRIVATE KEY-----"
		));

		Certificate pemRoundTrip =
			Certificate.from(certificatePem, privateKeyPem);
		assertLoadedKeyPairMatches(pemRoundTrip);
		assertTrue(pemRoundTrip.hasCertificate());

		Certificate certificateOnly = Certificate.from(certificatePem);
		assertFalse(certificateOnly.canSign());
		assertTrue(certificateOnly.hasCertificate());
		assertArrayEquals(
			certificate.x509Certificate().get().getEncoded(),
			certificateOnly.x509Certificate().get().getEncoded()
		);
		byte[] signed = certificate.sign(new byte[]{1, 2, 3});
		assertTrue(certificateOnly.verify(new byte[]{1, 2, 3}, signed));
		assertThrows(
			IllegalStateException.class,
			() -> certificateOnly.sign(new byte[0])
		);

		Resource certificateDer = memoryResource();
		Resource privateKeyDer = memoryResource();
		certificate.saveCertificateDer(certificateDer);
		certificate.savePrivateKeyDer(privateKeyDer);

		Certificate derRoundTrip =
			Certificate.from(certificateDer, privateKeyDer);
		assertLoadedKeyPairMatches(derRoundTrip);
		assertTrue(derRoundTrip.hasCertificate());

		assertArrayEquals(
			certificate.x509Certificate().get().getEncoded(),
			derRoundTrip.x509Certificate().get().getEncoded()
		);
	}

	@Test
	void rawPublicKeysRoundTripAsPemAndDerForVerificationOnly() {
		byte[] data = "verification only".getBytes(StandardCharsets.UTF_8);
		byte[] signature = certificate.sign(data);

		Resource publicKeyPem = memoryResource();
		certificate.savePublicKey(publicKeyPem);
		Certificate pemRoundTrip = Certificate.from(publicKeyPem);
		assertVerificationOnly(pemRoundTrip, data, signature);

		Resource publicKeyDer = memoryResource();
		certificate.savePublicKeyDer(publicKeyDer);
		Certificate derRoundTrip = Certificate.from(publicKeyDer);
		assertVerificationOnly(derRoundTrip, data, signature);
	}

	@Test
	void loadingRejectsMismatchedAndNonRsaKeys() throws Exception {
		Resource publicKey = memoryResource();
		Resource otherPrivateKey = memoryResource();
		certificate.savePublicKey(publicKey);
		otherCertificate.savePrivateKey(otherPrivateKey);

		IllegalArgumentException mismatch = assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(publicKey, otherPrivateKey)
		);
		assertTrue(
			hasMessageInChain(mismatch, "do not match"),
			() -> "Unexpected exception: " + mismatch
		);

		KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
		ecGenerator.initialize(256);
		KeyPair ecKeyPair = ecGenerator.generateKeyPair();

		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(ecKeyPair.getPublic().getEncoded()))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(
				publicKey,
				resource(ecKeyPair.getPrivate().getEncoded())
			)
		);
	}

	@Test
	void loadingRejectsEmptyMalformedUnsupportedAndTrailingData()
		throws Exception {
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(memoryResource())
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource("not a key"))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(
				"-----BEGIN PUBLIC KEY-----\n" +
					"%%%\n" +
					"-----END PUBLIC KEY-----"
			))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(
				"-----BEGIN PUBLIC KEY-----\n" +
					"AA==\n" +
					"-----END CERTIFICATE-----"
			))
		);

		Resource publicKeyPem = memoryResource();
		certificate.savePublicKey(publicKeyPem);
		String pemWithTrailingText =
			publicKeyPem.readAsString() + "\nuntrusted trailing text";
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(pemWithTrailingText))
		);

		Resource certificateDer = memoryResource();
		certificate.saveCertificateDer(certificateDer);
		byte[] validDer = certificateDer.readAll();
		byte[] derWithTrailingByte =
			Arrays.copyOf(validDer, validDer.length + 1);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(derWithTrailingByte))
		);

		Resource privateKey = memoryResource();
		certificate.savePrivateKey(privateKey);
		String pkcs1Label = privateKey.readAsString()
			.replace("BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY")
			.replace("END PRIVATE KEY", "END RSA PRIVATE KEY");
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(publicKeyPem, resource(pkcs1Label))
		);
	}

	@Test
	void loadingReportsUnavailableAndUnreadableResources() {
		Resource unavailable = exceptionalResource(false);
		Resource unreadable = exceptionalResource(true);

		IllegalArgumentException unavailableFailure = assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(unavailable)
		);
		assertEquals(
			"Resource is not available",
			unavailableFailure.getMessage()
		);

		IllegalArgumentException readFailure = assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(unreadable)
		);
		assertEquals("Could not read resource", readFailure.getMessage());
		assertInstanceOf(IOException.class, readFailure.getCause());
	}

	@Test
	void publicApiValidatesNullAndInvalidArguments() {
		assertThrows(
			NullPointerException.class,
			() -> Certificate.generate(null)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(" \t ")
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate("not a distinguished name")
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(
				"CN=Weak",
				1024,
				Duration.ofDays(1)
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(
				"CN=Unsupported",
				2050,
				Duration.ofDays(1)
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(
				"CN=Expired",
				2048,
				Duration.ZERO
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(
				"CN=Expired",
				2048,
				Duration.ofSeconds(-1)
			)
		);
		assertThrows(
			NullPointerException.class,
			() -> Certificate.generate("CN=Null", 2048, null)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.generate(
				"CN=Overflow",
				2048,
				Duration.ofSeconds(Long.MAX_VALUE)
			)
		);

		assertThrows(
			NullPointerException.class,
			() -> Certificate.from((Resource) null)
		);
		assertThrows(
			NullPointerException.class,
			() -> Certificate.from(null, memoryResource())
		);
		assertThrows(
			NullPointerException.class,
			() -> Certificate.from(memoryResource(), null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.sign(null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.verify(null, new byte[0])
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.verify(new byte[0], null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.verifyBase64(new byte[0], null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.savePublicKey(null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.savePrivateKey(null)
		);
		assertThrows(
			NullPointerException.class,
			() -> certificate.saveCertificate(null)
		);
	}

	@Test
	void subSecondValidityIsRoundedUpAndInitiallyValid() throws Exception {
		Certificate shortLived = Certificate.generate(
			"CN=Short Lived",
			2048,
			Duration.ofNanos(1)
		);

		shortLived.x509Certificate().get().checkValidity();
	}

	@Test
	void loadingRejectsLargeMalformedPemInput() {
		String malformedPem = "-----BEGIN " + " ".repeat(100_000) + "-----";

		assertThrows(
			IllegalArgumentException.class,
			() -> Certificate.from(resource(malformedPem))
		);
	}

	private static void assertLoadedKeyPairMatches(Certificate loaded) {
		byte[] data = "round trip".getBytes(StandardCharsets.UTF_8);

		assertTrue(loaded.canSign());
		assertArrayEquals(
			certificate.publicKey().getEncoded(),
			loaded.publicKey().getEncoded()
		);
		assertArrayEquals(
			certificate.privateKey().get().getEncoded(),
			loaded.privateKey().get().getEncoded()
		);
		assertTrue(loaded.verify(data, loaded.sign(data)));
	}

	private static void assertVerificationOnly(
		Certificate loaded,
		byte[] data,
		byte[] signature
	) {
		assertFalse(loaded.canSign());
		assertFalse(loaded.hasCertificate());
		assertFalse(loaded.privateKey().isPresent());
		assertFalse(loaded.x509Certificate().isPresent());
		assertArrayEquals(
			certificate.publicKey().getEncoded(),
			loaded.publicKey().getEncoded()
		);
		assertTrue(loaded.verify(data, signature));
		assertThrows(
			IllegalStateException.class,
			() -> loaded.sign(data)
		);
		assertThrows(
			IllegalStateException.class,
			() -> loaded.savePrivateKey(memoryResource())
		);
		assertThrows(
			IllegalStateException.class,
			() -> loaded.savePrivateKeyDer(memoryResource())
		);
		assertThrows(
			IllegalStateException.class,
			() -> loaded.saveCertificate(memoryResource())
		);
		assertThrows(
			IllegalStateException.class,
			() -> loaded.saveCertificateDer(memoryResource())
		);
	}

	private static boolean hasMessageInChain(
		Throwable throwable,
		String expected
	) {
		for (
			Throwable current = throwable;
			current != null;
			current = current.getCause()
		) {
			if (
				current.getMessage() != null &&
					current.getMessage().contains(expected)
			) {
				return true;
			}
		}
		return false;
	}

	private static Resource resource(String value) {
		return resource(value.getBytes(StandardCharsets.US_ASCII));
	}

	private static Resource resource(byte[] value) {
		Resource resource = memoryResource();
		resource.write(value);
		return resource;
	}

	private static Resource memoryResource() {
		return new Resource() {
			private byte[] data = new byte[0];

			@Override
			public boolean available() {
				return true;
			}

			@Override
			public long lastModified() {
				return 0;
			}

			@Override
			public void write(InputStream inputStream) {
				try {
					ByteArrayOutputStream output =
						new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int read;

					while ((read = inputStream.read(buffer)) != -1) {
						output.write(buffer, 0, read);
					}

					data = output.toByteArray();
				} catch (IOException exception) {
					throw new IllegalStateException(exception);
				}
			}

			@Override
			public InputStream read() {
				return new ByteArrayInputStream(data);
			}

			@Override
			public void delete() {
				data = new byte[0];
			}
		};
	}

	private static Resource exceptionalResource(boolean available) {
		return new Resource() {
			@Override
			public boolean available() {
				return available;
			}

			@Override
			public long lastModified() {
				return 0;
			}

			@Override
			public void write(InputStream inputStream) {
				throw new UnsupportedOperationException();
			}

			@Override
			public InputStream read() {
				return new InputStream() {
					@Override
					public int read() throws IOException {
						throw new IOException("read failed");
					}
				};
			}

			@Override
			public void delete() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
