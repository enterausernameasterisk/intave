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

package de.jpx3.intave.library;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class LibraryTest {
  private static final String SHA_256 =
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Test
  void extractsChecksumFromRepositoryFormats() {
    assertEquals(SHA_256, Library.extractChecksum(SHA_256 + "\n", 64));
    assertEquals(SHA_256, Library.extractChecksum(SHA_256 + "  library.jar\n", 64));
    assertEquals(SHA_256, Library.extractChecksum("SHA256 (library.jar) = " + SHA_256, 64));
    assertNull(Library.extractChecksum("not a checksum", 64));
  }

  @Test
  void rendersDigestAtItsNativeLength() throws Exception {
    assertEquals(SHA_256, digest("SHA-256"));
    assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", digest("SHA-1"));
    assertEquals("900150983cd24fb0d6963f7d28e17f72", digest("MD5"));
  }

  private static String digest(String algorithm) throws Exception {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    digest.update("abc".getBytes(StandardCharsets.US_ASCII));
    return Library.digestHex(digest);
  }
}
