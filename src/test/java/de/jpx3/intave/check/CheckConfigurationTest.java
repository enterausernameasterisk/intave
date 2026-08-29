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

package de.jpx3.intave.check;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CheckConfigurationTest {
  @Test
  void heuristicsUsesLegacyClassicSettingsAsFallback() {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("classic.attack-accuracy", 7);
    configuration.set("classic.thresholds.20", "legacy-command");

    CheckConfiguration.CheckSettings settings = settingsFor(configuration, "heuristics");

    assertEquals(7, settings.intBy("attack-accuracy"));
    assertEquals(
      Collections.singletonList("legacy-command"),
      settings.defaultThresholds().get(20)
    );
    assertTrue(settings.has("attack-accuracy"));
    assertTrue(settings.has("thresholds"));
  }

  @Test
  void currentHeuristicsSettingsTakePrecedenceOverLegacySettings() {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("attack-accuracy", 4);
    configuration.set("thresholds.20", "current-command");
    configuration.set("classic.attack-accuracy", 7);
    configuration.set("classic.thresholds.20", "legacy-command");

    CheckConfiguration.CheckSettings settings = settingsFor(configuration, "heuristics");

    assertEquals(4, settings.intBy("attack-accuracy"));
    assertEquals(
      Collections.singletonList("current-command"),
      settings.defaultThresholds().get(20)
    );
  }

  @Test
  void placementAnalysisUsesLegacyOnPremiseThresholdsAsFallback() {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("cloud-thresholds.on-premise.200", "legacy-command");

    CheckConfiguration.CheckSettings settings = settingsFor(
      configuration,
      "placementanalysis"
    );

    assertEquals(
      Collections.singletonList("legacy-command"),
      settings.defaultThresholds().get(200)
    );
  }

  private static CheckConfiguration.CheckSettings settingsFor(
    YamlConfiguration configuration,
    String configurationKey
  ) {
    Map<String, Object> mappings = new HashMap<>();
    for (String key : configuration.getKeys(true)) {
      mappings.put(key, configuration.get(key));
    }
    return new CheckConfiguration.CheckSettings(
      mappings,
      configurationKey,
      configurationKey
    );
  }
}
