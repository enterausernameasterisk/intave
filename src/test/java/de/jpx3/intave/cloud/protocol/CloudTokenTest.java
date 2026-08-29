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
import de.jpx3.intave.share.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

final class CloudTokenTest {
  private static Certificate signingCertificate;

  @BeforeAll
  static void generateSigningCertificate() {
    signingCertificate = Certificate.generate(
      "CN=Shard Connection Test",
      2048,
      Duration.ofDays(1)
    );
  }

  @Test
  void constructorExposesConnectionDetails() {
    byte[] token = "shard-secret".getBytes(StandardCharsets.UTF_8);

    CloudToken connection = new CloudToken(
      "shard.intave.cloud",
      2024,
      token
    );

    assertEquals("shard.intave.cloud", connection.domain());
    assertEquals(2024, connection.port());
    assertArrayEquals(token, connection.token());
  }

  @Test
  void cloudTokenRoundTripsConnectionDetails() {
    CloudToken expected = new CloudToken(
      "shard.intave.cloud",
      2024,
      "shard-sëcret".getBytes(StandardCharsets.UTF_8)
    );

    String cloudToken = expected.toString(signingCertificate);
    Result<CloudToken, String> result = CloudToken.fromString(
      cloudToken,
      signingCertificate
    );

    assertTrue(cloudToken.startsWith("ct_"));
    assertTrue(result.successful());
    CloudToken actual = result.result();
    assertEquals(expected.domain(), actual.domain());
    assertEquals(expected.port(), actual.port());
    assertArrayEquals(expected.token(), actual.token());
  }

  @Test
  void toCloudTokenCompressesSignedDetailsBeforeBase64Encoding()
    throws DataFormatException {
    CloudToken connection = new CloudToken(
      "shard.intave.cloud",
      2024,
      "secret".getBytes(StandardCharsets.UTF_8)
    );

    String cloudToken = connection.toString(signingCertificate);
    byte[] compressed = Base64.getDecoder().decode(cloudToken.substring(3));
    byte[] signedDetails = decompress(compressed);

    assertTrue(
      new String(signedDetails, StandardCharsets.UTF_8)
        .startsWith("shard.intave.cloud;2024;secret:")
    );
  }

  @Test
  void toCloudTokenRejectsReservedDelimiters() {
    CloudToken connection = new CloudToken(
      "shard;intave.cloud",
      2024,
      "secret".getBytes(StandardCharsets.UTF_8)
    );

    assertThrows(
      IllegalStateException.class,
      () -> connection.toString(signingCertificate)
    );
  }

  @Test
  void toCloudTokenRequiresSigningCertificate() {
    CloudToken connection = new CloudToken(
      "shard.intave.cloud",
      2024,
      "secret".getBytes(StandardCharsets.UTF_8)
    );

    assertThrows(
      NullPointerException.class,
      () -> connection.toString(null)
    );
  }

  @Test
  void fromRejectsTokenWithoutCloudTokenPrefix() {
    assertError(
      "not-a-cloud-token",
      "Token does not start with ct_"
    );
  }

  @Test
  void fromRejectsTokenWithWrongNumberOfOuterParts() {
    assertError(
      "ct_first_second",
      "Token does not have 2 parts"
    );
  }

  @Test
  void fromRejectsInvalidBase64Payload() {
    assertError(
      "ct_%",
      "Token is not valid base 64"
    );
  }

  @Test
  void fromRejectsInvalidCompressedPayload() {
    assertError(
      "ct_" + Base64.getEncoder().encodeToString(
        "not compressed".getBytes(StandardCharsets.UTF_8)
      ),
      "Token is not valid compressed data"
    );
  }

  @Test
  void fromRejectsDecodedPayloadWithoutSignature() {
    assertError(
      cloudToken("shard.intave.cloud;2024;secret"),
      "Decoded token does not have 4 parts"
    );
  }

  @Test
  void fromRejectsDecodedPayloadWithMalformedConnectionDetails() {
    assertError(
      cloudToken("shard.intave.cloud;2024:signature"),
      "Decoded token does not have 3 parts"
    );
  }

  @Test
  void fromRejectsInvalidSignature() {
    assertError(
      cloudToken("shard.intave.cloud;2024;secret:invalid-signature"),
      "Token signature is invalid"
    );
  }

  private static String cloudToken(String payload) {
    return "ct_" + Base64.getEncoder().encodeToString(
      compress(payload.getBytes(StandardCharsets.UTF_8))
    );
  }

  private static byte[] compress(byte[] input) {
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
    try {
      deflater.setInput(input);
      deflater.finish();
      byte[] buffer = new byte[512];
      ByteArrayOutputStream output = new ByteArrayOutputStream();
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
      byte[] buffer = new byte[512];
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    } finally {
      inflater.end();
    }
  }

  private static void assertError(String token, String expectedError) {
    Result<CloudToken, String> result = CloudToken.fromString(token);
    assertTrue(result.erroneous());
    assertEquals(expectedError, result.error());
  }
}
