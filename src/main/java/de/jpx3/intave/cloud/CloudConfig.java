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

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.cloud.protocol.CloudToken;
import de.jpx3.intave.share.Result;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CloudConfig {
  private boolean enabled;
  private CloudToken connectionSettings;
  private PrivacyMode privacyMode;
  private CloudFeatures features;

  public boolean isEnabled() {
    return this.enabled;
  }

  public CloudFeatures features() {
    return this.features;
  }

  public static CloudConfig from(YamlConfiguration configuration) {
    ConfigurationSection current = configuration.getConfigurationSection("check.cloud");
    ConfigurationSection legacy = configuration.getConfigurationSection("cloud");
    return from(current, legacy);
  }

  public static CloudConfig from(ConfigurationSection section) {
    return from(section, null);
  }

  private static CloudConfig from(
    ConfigurationSection section,
    ConfigurationSection legacySection
  ) {
    boolean requestedEnabled = booleanValue(section, legacySection, false, "enabled");
    boolean cloudStorage = booleanValue(
      section, legacySection, true, "features.storage", "features.cloud-storage"
    );
    boolean cloudTrustFactor = booleanValue(
      section, legacySection, true, "features.trustfactor", "features.cloud-trustfactor"
    );
    boolean cloudSamples = booleanValue(
      section, legacySection, true, "features.samples", "features.cloud-heuristics"
    );
    PrivacyMode privacyMode = PrivacyMode.fromString(
      stringValue(section, legacySection, "BEST_DETECTION", "privacy-mode")
    );
    String cloudToken = stringValue(section, legacySection, null, "token");
    CloudToken connectionSettings = null;
    if (requestedEnabled && usableToken(cloudToken)) {
      Result<CloudToken, String> connectionStringResult = CloudToken.fromString(cloudToken);
      if (connectionStringResult.erroneous()) {
        IntaveLogger.logger().error("Unable to read cloud token: " + connectionStringResult.error());
      } else {
        connectionSettings = connectionStringResult.result();
      }
    }
    CloudFeatures features = new CloudFeatures();
    features.cloudStorage = cloudStorage;
    features.cloudTrustFactor = cloudTrustFactor;
    features.cloudSamples = cloudSamples;
    CloudConfig config = new CloudConfig();
    config.enabled = requestedEnabled && connectionSettings != null;
    config.connectionSettings = connectionSettings;
    config.features = features;
    config.privacyMode = privacyMode;
    return config;
  }

  private static boolean booleanValue(
    ConfigurationSection section,
    ConfigurationSection legacySection,
    boolean def,
    String... paths
  ) {
    for (String path : paths) {
      if (section != null && section.contains(path)) {
        return section.getBoolean(path);
      }
    }
    for (String path : paths) {
      if (legacySection != null && legacySection.contains(path)) {
        return legacySection.getBoolean(path);
      }
    }
    return def;
  }

  private static String stringValue(
    ConfigurationSection section,
    ConfigurationSection legacySection,
    String def,
    String... paths
  ) {
    for (String path : paths) {
      if (section != null && section.contains(path)) {
        return section.getString(path);
      }
    }
    for (String path : paths) {
      if (legacySection != null && legacySection.contains(path)) {
        return legacySection.getString(path);
      }
    }
    return def;
  }

  private static boolean usableToken(String cloudToken) {
    if (cloudToken == null) {
      return false;
    }
    String trimmedToken = cloudToken.trim();
    return !trimmedToken.isEmpty() && !"ct_EXAMPLE-TOKEN".equals(trimmedToken);
  }

  public @Nullable CloudToken connectionSettings() {
    return this.connectionSettings;
  }

  public boolean hasConnectionSettings() {
    return this.connectionSettings != null;
  }

  public PrivacyMode privacy() {
    return this.privacyMode;
  }

  public static class CloudFeatures {
    private boolean cloudStorage;
    private boolean cloudTrustFactor;
    private boolean cloudSamples;

    public boolean cloudStorageEnabled() {
      return this.cloudStorage;
    }

    public boolean cloudTrustfactorEnabled() {
      return this.cloudTrustFactor;
    }

    public boolean sampleTransmission() {
      return this.cloudSamples;
    }

  }
}
