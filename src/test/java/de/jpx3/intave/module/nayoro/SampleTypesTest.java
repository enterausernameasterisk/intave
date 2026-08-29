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

import ac.intave.samples.share.Block;
import ac.intave.samples.share.BlockUpdate;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class SampleTypesTest {
  @Test
  void blockIncludesNamePropertiesAndNormalizedCollisionShape() throws Exception {
    BlockState state = blockState("north");

    Block converted = SampleTypes.block(state, 10, 64, 20);

    assertEquals("OAK_STAIRS", converted.name());
    assertEquals("north", converted.properties().get("facing"));
    assertEquals("false", converted.properties().get("waterlogged"));
    assertEquals(
      new ac.intave.samples.share.BoundingBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0),
      converted.boundingBoxes().getFirst()
    );
  }

  @Test
  void dirtyBlocksUseReducedRadiusAndSendAirTombstones() {
    int blockX = 7;
    int blockY = 61;
    int blockZ = 17;
    AtomicReference<BlockState> state = new AtomicReference<>(BlockState.stone());
    BlockCache blockCache = mutableBlockCacheAt(blockX, blockY, blockZ, state);
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    List<BlockUpdate> initial = tracker.dirtyNearbyBlocks(blockCache, playerBox);
    List<BlockUpdate> unchanged = tracker.dirtyNearbyBlocks(blockCache, playerBox);
    state.set(BlockState.empty());
    List<BlockUpdate> removed = tracker.dirtyNearbyBlocks(blockCache, playerBox);

    assertEquals(1, initial.size());
    assertEquals("STONE", initial.getFirst().block().name());
    assertEquals(blockX, initial.getFirst().position().x());
    assertEquals(blockY, initial.getFirst().position().y());
    assertEquals(blockZ, initial.getFirst().position().z());
    assertTrue(unchanged.isEmpty());
    assertEquals(1, removed.size());
    assertSame(Block.AIR, removed.getFirst().block());
    assertEquals(0, tracker.recordedBlockCount());
  }

  @Test
  void dirtyBlockScanIsBoundedToReducedRadius() {
    AtomicInteger accesses = new AtomicInteger();
    BlockCache blockCache = (BlockCache) Proxy.newProxyInstance(
      BlockCache.class.getClassLoader(),
      new Class<?>[]{BlockCache.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("stateAt")) {
          accesses.incrementAndGet();
          return BlockState.empty();
        }
        throw new UnsupportedOperationException(method.getName());
      }
    );
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    new NearbyBlockTracker().dirtyNearbyBlocks(blockCache, playerBox);

    assertEquals(392, accesses.get());
  }

  @Test
  void propertyOnlyChangesAreDirty() throws Exception {
    int blockX = 10;
    int blockY = 64;
    int blockZ = 20;
    AtomicReference<BlockState> state = new AtomicReference<>(blockState("north"));
    BlockCache blockCache = mutableBlockCacheAt(blockX, blockY, blockZ, state);
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    tracker.dirtyNearbyBlocks(blockCache, playerBox);
    state.set(blockState("south"));
    List<BlockUpdate> updates = tracker.dirtyNearbyBlocks(blockCache, playerBox);

    assertEquals(1, updates.size());
    assertEquals("south", updates.getFirst().block().properties().get("facing"));
  }

  @Test
  void emptyScansDoNotRetainAirBlocks() {
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BlockCache blockCache = mutableBlockCacheAt(
      Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
      new AtomicReference<>(BlockState.empty())
    );
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    List<BlockUpdate> updates = tracker.dirtyNearbyBlocks(blockCache, playerBox);

    assertTrue(updates.isEmpty());
    assertEquals(0, tracker.recordedBlockCount());
  }

  private static BlockState blockState(String facing) throws Exception {
    BlockState state = new BlockState(
      BlockShapes.emptyShape(),
      BoundingBox.fromBounds(10.25, 64.0, 20.5, 10.75, 65.0, 21.0),
      Material.OAK_STAIRS,
      7
    );
    Map<String, Comparable<?>> properties = new HashMap<>();
    properties.put("waterlogged", false);
    properties.put("facing", facing);
    setResolvedProperties(state, properties);
    return state;
  }

  private static BlockCache mutableBlockCacheAt(
    int blockX,
    int blockY,
    int blockZ,
    AtomicReference<BlockState> state
  ) {
    return (BlockCache) Proxy.newProxyInstance(
      BlockCache.class.getClassLoader(),
      new Class<?>[]{BlockCache.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("stateAt")) {
          int x = (Integer) arguments[0];
          int y = (Integer) arguments[1];
          int z = (Integer) arguments[2];
          return x == blockX && y == blockY && z == blockZ
            ? state.get()
            : BlockState.empty();
        }
        throw new UnsupportedOperationException(method.getName());
      }
    );
  }

  private static void setResolvedProperties(
    BlockState state, Map<String, Comparable<?>> properties
  ) throws Exception {
    Field field = BlockState.class.getDeclaredField("properties");
    field.setAccessible(true);
    field.set(state, properties);
  }
}
