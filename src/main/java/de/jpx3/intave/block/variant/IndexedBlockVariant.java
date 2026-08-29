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

package de.jpx3.intave.block.variant;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.bukkit.Material;

import java.util.*;

final class IndexedBlockVariant implements BlockVariant {
  private static final StreamCodec<ByteBuf, ByteBuf, Comparable<?>> PROPERTY_CODEC = StreamCodec.of(
    (buffer, value) -> {
      if (value instanceof Boolean) {
        buffer.writeByte(0);
        buffer.writeBoolean((Boolean) value);
      } else if (value instanceof Integer) {
        buffer.writeByte(1);
        buffer.writeInt((Integer) value);
      } else if (value instanceof String) {
        buffer.writeByte(2);
        ByteBufStreamCodecs.STRING.encode(buffer, (String) value);
      } else {
        throw new IllegalArgumentException("Unsupported block variant property: " + value);
      }
    },
    buffer -> {
      switch (buffer.readUnsignedByte()) {
        case 0:
          return buffer.readBoolean();
        case 1:
          return buffer.readInt();
        case 2:
          return ByteBufStreamCodecs.STRING.decode(buffer);
        default:
          throw new IllegalStateException("Unknown block variant property type");
      }
    }
  );
  private static final StreamCodec<ByteBuf, ByteBuf, Map<String, Comparable<?>>> PROPERTIES_CODEC =
    ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.STRING, PROPERTY_CODEC);
  static final StreamCodec<ByteBuf, ByteBuf, IndexedBlockVariant> STREAM_CODEC = StreamCodec.compound(
    ByteBufStreamCodecs.INTEGER,
    IndexedBlockVariant::index,
    PROPERTIES_CODEC,
    variant -> variant.namedConfig,
    IndexedBlockVariant::new
  );

  private final Material type;
  private final int variantIndex;
  private final Map<String, Comparable<?>> namedConfig = new HashMap<>();

  IndexedBlockVariant(
    Material type,
    Map<? extends Setting<?>, Comparable<?>> nativeConfig,
    int variantIndex
  ) {
    this.type = type;
    this.variantIndex = variantIndex;
    for (Map.Entry<? extends Setting<?>, Comparable<?>> entry : nativeConfig.entrySet()) {
      Setting<?> setting = entry.getKey();
      Comparable<?> value = normalizeProperty(entry.getValue());
      String name = setting.name().toLowerCase(Locale.ROOT);
      namedConfig.put(name, value);
    }
  }

  IndexedBlockVariant(
    int variantIndex,
    Map<String, Comparable<?>> namedConfig
  ) {
    this.type = null;
    this.variantIndex = variantIndex;
    namedConfig.forEach((name, value) -> this.namedConfig.put(name, normalizeProperty(value)));
  }

  private static Comparable<?> normalizeProperty(Comparable<?> value) {
    return value instanceof Enum<?> ? ((Enum<?>) value).name() : value;
  }

  @Override
  public BlockVariant copy() {
    return this;
  }

  @Override
  public Set<String> propertyNames() {
    return namedConfig.keySet();
  }

  public <T> T propertyOf(String name) {
    //noinspection unchecked
    return (T) namedConfig.get(name);
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
    name = name.toLowerCase(Locale.ROOT);
    Comparable<?> value = namedConfig.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new IllegalStateException(type + "/" + name + " is not a enum property");
    }
    return Enum.valueOf(klass, value.toString().toUpperCase(Locale.ROOT));
  }

  @Override
  public int index() {
    return variantIndex;
  }

  @Override
  public void dumpStates() {
    namedConfig.forEach((name, value) -> System.out.println("  " + name + ": " + value));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof IndexedBlockVariant)) return false;
    IndexedBlockVariant that = (IndexedBlockVariant) obj;
    return variantIndex == that.variantIndex && namedConfig.equals(that.namedConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variantIndex, namedConfig);
  }
}
