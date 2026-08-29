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

package de.jpx3.intave.module.nayoro;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.*;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

public class Inventory {
  private final int windowId;
  private final int size;

  private final Map<Integer, Item> items = new HashMap<>();

  public Inventory(int windowId, int size) {
    this.windowId = windowId;
    this.size = size;
  }

  public void setItems(Map<Integer, ? extends Item> items) {
    this.items.putAll(items);
  }

  public Map<Integer, Item> items() {
    return items;
  }

  public int windowId() {
    return windowId;
  }

  public int size() {
    return size;
  }

  public void clear() {
    items.clear();
  }

  public static class Item {
    public static final StreamCodec<JsonReader, JsonWriter, Item> JSON_CODEC = object(
      values -> new Item(
        values.value(0), values.value(1), values.value(2),
        values.value(3), values.value(4), values.value(5)
      ),
      stringField("type", Item::type),
      integerField("amount", Item::amount),
      field("category", ItemCategory.JSON_CODEC, Item::category, ItemCategory.OTHER),
      booleanField("glowing", Item::glowing),
      doubleField("baseQuality", Item::baseQuality),
      doubleField("enchantmentQuality", Item::enchantmentQuality)
    );
    public static final StreamCodec<JsonReader, JsonWriter, Map<Integer, Item>> SLOT_MAP_JSON_CODEC =
      slotMapJsonCodec();
    public static final StreamCodec<JsonReader, JsonWriter, java.util.List<Item>> NULLABLE_LIST_JSON_CODEC =
      listCodecOf(nullable(JSON_CODEC));

    private final String type;
    private final int amount;
    private final ItemCategory category;
    private final boolean glowing;
    private final double baseQuality;
    private final double enchantmentQuality;

    public Item(
      String type, int amount, ItemCategory category,
      boolean glowing, double baseQuality, double enchantmentQuality
    ) {
      this.type = type;
      this.amount = amount;
      this.category = category;
      this.glowing = glowing;
      this.baseQuality = baseQuality;
      this.enchantmentQuality = enchantmentQuality;
    }

    public String type() {
      return type;
    }

    public int amount() {
      return amount;
    }

    public ItemCategory category() {
      return category;
    }

    public boolean glowing() {
      return glowing;
    }

    public double baseQuality() {
      return baseQuality;
    }

    public double enchantmentQuality() {
      return enchantmentQuality;
    }

    @Override
    public String toString() {
      if ("AIR".equalsIgnoreCase(type)) {
        return "Item{AIR}";
      }
      return "Item{" +
        "type='" + type + '\'' +
        ", amount=" + amount +
        ", category=" + category +
        ", glowing=" + glowing +
        ", baseQuality=" + baseQuality +
        ", enchantmentQuality=" + enchantmentQuality +
        '}';
    }

    public static Item fromItem(ItemStack item) {
      if (item == null) {
        return new Item("AIR", 0, ItemCategory.OTHER, false, 0.0, 0.0);
      }
      double[] strength = strengthOf(item);
      return new Item(
        item.getType().name(), item.getAmount(), ItemCategory.of(item),
        isGlowing(item), strength[0], strength[1]
      );
    }

    private static StreamCodec<JsonReader, JsonWriter, Map<Integer, Item>> slotMapJsonCodec() {
      return JsonStreamCodecs.of(
        (writer, items) -> {
          writer.beginObject();
          for (Map.Entry<Integer, Item> entry : new TreeMap<>(items).entrySet()) {
            writer.name(Integer.toString(entry.getKey()));
            JSON_CODEC.encode(writer, entry.getValue());
          }
          writer.endObject();
        },
        reader -> {
          Map<Integer, Item> items = new LinkedHashMap<>();
          reader.beginObject();
          while (reader.hasNext()) {
            if (items.size() >= 1024) {
              throw new IllegalStateException("Window item map exceeds maximum size of 1024");
            }
            int slot;
            try {
              slot = Integer.parseInt(reader.nextName());
            } catch (NumberFormatException exception) {
              throw new IOException("Invalid inventory slot", exception);
            }
            items.put(slot, JSON_CODEC.decode(reader));
          }
          reader.endObject();
          return items;
        }
      );
    }
  }

  public enum ItemCategory {
    SWORD(0, new double[]{6.4, 6.4, 8.0, 9.6, 11.2, 12.8}),
    PICKAXE(1, new double[]{2.4, 2.4, 3.6, 4.8, 6, 7.2, 8.4}),
    SHOVEL(2, new double[]{2.5, 2.5, 3.5, 4.5, 5.5, 10.4}),
    AXE(3, new double[]{5.6, 7.0, 7.2, 8.1, 9.0, 10.0}),
    BOW(4),

    HELMET(5),
    CHESTPLATE(6),
    LEGGINGS(7),
    BOOTS(8),

    BLOCK(9),

    OTHER(10)

    ;

    public static final StreamCodec<JsonReader, JsonWriter, ItemCategory> JSON_CODEC =
      enumCodec(ItemCategory.class);

    private final int id;
    private final double[] attackDamage;

    ItemCategory(int id, double[] attackDamage) {
      this.id = id;
      this.attackDamage = attackDamage;
    }

    ItemCategory(int id) {
      this.id = id;
      this.attackDamage = new double[]{1, 1, 1, 1, 1, 1};
    }

    public int id() {
      return id;
    }

    public double attackDamage(int slot) {
      if (slot < 0 || slot >= attackDamage.length) {
        return 0.0;
      }
      return attackDamage[slot];
    }

    public static ItemCategory of(ItemStack item) {
      if (item == null) {
        return ItemCategory.OTHER;
      }
      if (isBlock(item)) {
        return ItemCategory.BLOCK;
      } else if (isArmor(item)) {
        EquipmentSlot slot = EquipmentSlot.of(item.getType());
        if (slot == null) {
          System.out.println("Unknown slot for " + item.getType().name());
          return ItemCategory.OTHER;
        }
        return slot.category();
      } else if (isSword(item)) {
        return ItemCategory.SWORD;
      } else if (isTool(item)) {
        String name = item.getType().name();
        if (name.endsWith("_PICKAXE")) {
          return ItemCategory.PICKAXE;
        } else if (name.endsWith("_SHOVEL")) {
          return ItemCategory.SHOVEL;
        } else if (name.endsWith("_AXE")) {
          return ItemCategory.AXE;
        }
      }
      return ItemCategory.OTHER;
    }

    private static final Map<Integer, ItemCategory> cache = new HashMap<>();

    public static ItemCategory of(int id) {
      ItemCategory category = cache.get(id);
      if (category == null) {
        for (ItemCategory value : values()) {
          if (value.id() == id) {
            category = value;
            break;
          }
        }
        cache.put(id, category);
      }
      return category;
    }

    public boolean isArmor() {
      return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    public boolean isTool() {
      return this == PICKAXE || this == SHOVEL || this == AXE;
    }

    public boolean isSword() {
      return this == SWORD;
    }

    public boolean isBlock() {
      return this == BLOCK;
    }

    private static boolean isSword(ItemStack item) {
      return item.getType().name().endsWith("_SWORD");
    }

    private static boolean isTool(ItemStack item) {
      String name = item.getType().name();
      return name.endsWith("_PICKAXE") ||
        name.endsWith("_SHOVEL") ||
        name.endsWith("_AXE");
    }

    private static boolean isArmor(ItemStack item) {
      String name = item.getType().name();
      return name.endsWith("_HELMET") ||
        name.endsWith("_CHESTPLATE") ||
        name.endsWith("_LEGGINGS") ||
        name.endsWith("_BOOTS");
    }

    private static final Set<Material> IS_BLOCK_CACHE = new HashSet<>();
    private static final Set<Material> IS_NOT_BLOCK_CACHE = new HashSet<>();

    private static boolean isBlock(ItemStack item) {
      Material type = item.getType();
      if (IS_BLOCK_CACHE.contains(type)) {
        return true;
      } else if (IS_NOT_BLOCK_CACHE.contains(type)) {
        return false;
      }
      boolean isBlock = type.isBlock();
      (isBlock ? IS_BLOCK_CACHE : IS_NOT_BLOCK_CACHE).add(type);
      return isBlock;
    }
  }

  private static final Set<Material> CAN_BE_ENCHANTED = new HashSet<>();

  public static void populateCache() {
    for (Material value : Material.values()) {
      if (!value.isBlock()) {
        for (Enchantment enchantment : Enchantment.values()) {
          if (enchantment.canEnchantItem(new ItemStack(value))) {
            CAN_BE_ENCHANTED.add(value);
            break;
          }
        }
      }
    }
  }

  public static boolean isGlowing(ItemStack item) {
    return CAN_BE_ENCHANTED.contains(item.getType()) && item.hasItemMeta() && item.getItemMeta().hasEnchants();
  }

  public static double[] strengthOf(ItemStack item) {
    ItemCategory category = ItemCategory.of(item);
    if (category.isArmor()) {
      return armorValueOf(item);
    } else if (category.isTool() || category.isSword()) {
      return toolDamage(category, item);
    }
    return new double[]{1.0, 1.0};
  }

  private static double[] armorValueOf(ItemStack item) {
    EquipmentSlot slot = EquipmentSlot.of(item.getType());
    ArmorMaterial material = ArmorMaterial.of(item.getType());
    if (slot == null || material == null) {
      return new double[]{0.0, 0.0};
    }
    int protection = item.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
    return new double[]{material.damageReductionAmount(slot), (1.0 - (protection / 25f))};
  }

  private static double[] toolDamage(ItemCategory itemCategory, ItemStack item) {
    int sharpness = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
    int slot = 0;
    String typeName = item.getType().name();
    if (typeName.startsWith("NETHERITE")) {
      slot = 5;
    } else if (typeName.startsWith("IRON")) {
      slot = 4;
    } else if (typeName.startsWith("DIAMOND")) {
      slot = 3;
    } else if (typeName.startsWith("STONE")) {
      slot = 2;
    } else if (typeName.startsWith("GOLD")) {
      slot = 1;
    } else if (typeName.startsWith("WOOD")) {
      slot = 0;
    }
    return new double[]{itemCategory.attackDamage(slot), sharpness > 0 ? 0 : (1f+ sharpness * 0.5f)};
  }

  public enum ArmorMaterial {
    LEATHER(5, new int[]{1, 2, 3, 1}, 15, 0.0f),
    CHAIN(15, new int[]{1, 4, 5, 2}, 12, 0.0f),
    IRON(15, new int[]{2, 5, 6, 2}, 9, 0.0f),
    GOLD(7, new int[]{1, 3, 5, 2}, 25, 0.0f),
    DIAMOND(33, new int[]{3, 6, 8, 3}, 10, 2.0f),
    TURTLE(25, new int[]{2, 5, 6, 2}, 9, 0.0f),
    NETHERITE(37, new int[]{3, 6, 8, 3}, 15, 3.0f)

    ;

    private final int maxDamageFactor;
    private final int[] damageReductionAmountArray;
    private final int enchantability;
    private final float toughness;

    ArmorMaterial(int maxDamageFactor, int[] damageReductionAmountArray, int enchantability, float toughness) {
      this.maxDamageFactor = maxDamageFactor;
      this.damageReductionAmountArray = damageReductionAmountArray;
      this.enchantability = enchantability;
      this.toughness = toughness;
    }

    private static final Map<Material, ArmorMaterial> cache = new HashMap<>();

    public static ArmorMaterial of(Material type) {
      ArmorMaterial material = cache.get(type);
      if (material == null) {
        try {
          String typeName = type.name();
          String materialName = typeName.substring(0, typeName.indexOf('_'));
          material = ArmorMaterial.valueOf(materialName);
        } catch (Exception ignored) {}
        cache.put(type, material);
      }
      return material;
    }

    public int maxDamageFactor() {
      return maxDamageFactor;
    }

    public int damageReductionAmount(EquipmentSlot slot) {
      return damageReductionAmountArray[slot.index()];
    }
  }

  public enum EquipmentSlot {
    HELMET(3, ItemCategory.HELMET),
    CHESTPLATE(2, ItemCategory.CHESTPLATE),
    LEGGINGS(1, ItemCategory.LEGGINGS),
    BOOTS(0, ItemCategory.BOOTS),
    ;

    private final int index;
    private final ItemCategory category;

    EquipmentSlot(int index, ItemCategory category) {
      this.index = index;
      this.category = category;
    }

    public int index() {
      return index;
    }

    public ItemCategory category() {
      return category;
    }

    private static final Map<Material, EquipmentSlot> cache = new HashMap<>();

    public static EquipmentSlot of(Material type) {
      EquipmentSlot slot = cache.get(type);
      if (slot == null) {
        try {
          String typeName = type.name();
          String slotName = typeName.substring(typeName.indexOf('_') + 1);
          slot = EquipmentSlot.valueOf(slotName);
        } catch (Exception ignored) {}
        cache.put(type, slot);
      }
      return slot;
    }
  }

  @Override
  public String toString() {
    return "Inventory{" +
      "windowId=" + windowId +
      ", size=" + size +
      ", items=" + items +
      '}';
  }
}
