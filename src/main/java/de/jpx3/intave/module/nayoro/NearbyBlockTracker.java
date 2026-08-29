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
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;

final class NearbyBlockTracker {
  private static final double NEARBY_BLOCK_RADIUS = 3.0D;

  private final Long2ObjectMap<RecordedBlock> recordedBlocks =
    new Long2ObjectOpenHashMap<>();

  public List<BlockUpdate> dirtyNearbyBlocks(
    BlockCache blockCache,
    BoundingBox playerBoundingBox
  ) {
    BoundingBox region = playerBoundingBox.grow(NEARBY_BLOCK_RADIUS);
    int minX = floor(region.minX);
    int minY = floor(region.minY);
    int minZ = floor(region.minZ);
    int maxX = floor(region.maxX);
    int maxY = floor(region.maxY);
    int maxZ = floor(region.maxZ);
    List<BlockUpdate> updates = new ArrayList<>();
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockState state = blockCache.stateAt(x, y, z);
          long positionKey = BlockPosition.toLong(x, y, z);
          RecordedBlock previous = recordedBlocks.get(positionKey);
          if (state.type() == Material.AIR) {
            if (previous != null) {
              recordedBlocks.remove(positionKey);
              updates.add(update(x, y, z, Block.AIR));
            }
            continue;
          }
          if (previous != null && previous.state == state) {
            continue;
          }

          Block current = SampleTypes.block(state, x, y, z);
          if (previous != null && current.equals(previous.block)) {
            // Cache refreshes may replace BlockState instances without changing their
            // serialized form. Remember the new identity for subsequent fast-path scans.
            recordedBlocks.put(positionKey, new RecordedBlock(state, previous.block));
            continue;
          }
          recordedBlocks.put(positionKey, new RecordedBlock(state, current));
          updates.add(update(x, y, z, current));
        }
      }
    }
    return updates;
  }

  int recordedBlockCount() {
    return recordedBlocks.size();
  }

  private static BlockUpdate update(int x, int y, int z, Block block) {
    return new BlockUpdate(
      new ac.intave.samples.share.BlockPosition(x, y, z), block
    );
  }

  private static final class RecordedBlock {
    private final BlockState state;
    private final Block block;

    private RecordedBlock(BlockState state, Block block) {
      this.state = state;
      this.block = block;
    }
  }
}
