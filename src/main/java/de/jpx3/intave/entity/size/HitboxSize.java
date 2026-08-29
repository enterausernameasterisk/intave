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

package de.jpx3.intave.entity.size;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.StreamCodec;

import static de.jpx3.intave.codec.JsonStreamCodecs.floatField;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;

public final class HitboxSize {
  public static final StreamCodec<JsonReader, JsonWriter, HitboxSize> JSON_CODEC = object(
    values -> HitboxSize.of(values.value(0), values.value(1)),
    floatField("width", HitboxSize::width),
    floatField("height", HitboxSize::height)
  );

  private final float width;
  private final float height;

  private HitboxSize(float width, float height) {
    this.width = width;
    this.height = height;
  }

  public static HitboxSize of(float width, float height) {
    return new HitboxSize(width, height);
  }

  public static HitboxSize zero() {
    return new HitboxSize(0, 0);
  }

  public static HitboxSize playerDefault() {
    return new HitboxSize(0.6f, 1.8f);
  }

  public float width() {
    return width;
  }

  public float height() {
    return height;
  }

  @Override
  public String toString() {
    return "(" + width + ", " + height + ")";
  }

  public HitboxSize scaled(double scale) {
    return new HitboxSize(width * (float) scale, height * (float) scale);
  }
}
