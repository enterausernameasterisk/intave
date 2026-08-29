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

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CloudConfigTest {
  @Test
  void legacyCloudSectionRemainsDisabled() {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("cloud.enabled", false);
    configuration.set("cloud.features.cloud-storage", false);
    configuration.set("cloud.features.cloud-trustfactor", false);
    configuration.set("cloud.features.cloud-heuristics", false);

    CloudConfig cloudConfig = CloudConfig.from(configuration);

    assertFalse(cloudConfig.isEnabled());
    assertFalse(cloudConfig.features().cloudStorageEnabled());
    assertFalse(cloudConfig.features().cloudTrustfactorEnabled());
    assertFalse(cloudConfig.features().sampleTransmission());
  }

  @Test
  void currentCloudPathsTakePrecedenceOverLegacyPaths() {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("cloud.features.cloud-storage", false);
    configuration.set("cloud.features.cloud-trustfactor", false);
    configuration.set("cloud.features.cloud-heuristics", false);
    configuration.set("check.cloud.features.storage", true);
    configuration.set("check.cloud.features.trustfactor", true);
    configuration.set("check.cloud.features.samples", true);

    CloudConfig cloudConfig = CloudConfig.from(configuration);

    assertTrue(cloudConfig.features().cloudStorageEnabled());
    assertTrue(cloudConfig.features().cloudTrustfactorEnabled());
    assertTrue(cloudConfig.features().sampleTransmission());
  }

  @Test
  void missingAndExampleTokensFailClosed() {
    YamlConfiguration missingToken = new YamlConfiguration();
    missingToken.set("check.cloud.enabled", true);
    YamlConfiguration exampleToken = new YamlConfiguration();
    exampleToken.set("check.cloud.enabled", true);
    exampleToken.set("check.cloud.token", "ct_EXAMPLE-TOKEN");

    CloudConfig missingTokenConfig = CloudConfig.from(missingToken);
    CloudConfig exampleTokenConfig = CloudConfig.from(exampleToken);

    assertFalse(missingTokenConfig.isEnabled());
    assertFalse(missingTokenConfig.hasConnectionSettings());
    assertFalse(exampleTokenConfig.isEnabled());
    assertFalse(exampleTokenConfig.hasConnectionSettings());
  }
}
