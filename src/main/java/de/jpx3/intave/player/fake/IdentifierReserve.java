package de.jpx3.intave.player.fake;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 *
 * Two acquisition strategies are supported:
 *  - Legacy (<26.2): a static counter field on the NMS Entity class
 *  - Modern (26.2+): Entity ids are handed out by Level#getNextEntityId(),
 *    Mojang removed the static counter field entirely.
 * We probe for the legacy field first and transparently fall back to the
 * method-based strategy if it's not present, so this keeps working
 * regardless of which mechanism the running server version uses.
 */
public final class IdentifierReserve {
  private static final int REQUIRED_ID_POOL_SIZE = 25;
  private static final Queue<Integer> availableIds = new ConcurrentLinkedDeque<>();

  private static final Field ENTITY_COUNT_FIELD;
  private static final boolean ATOMIC_INTEGER_FIELD;
  private static final Method GET_NEXT_ENTITY_ID_METHOD;

  static {
    Field legacyField = null;
    boolean atomicField = false;
    Method modernMethod = null;

    try {
      legacyField = Lookup.serverField("Entity", "entityCount");
      atomicField = legacyField.getType() == AtomicInteger.class;
    } catch (Throwable legacyLookupFailure) {
      try {
        Class<?> worldClass = Lookup.serverClass("World");
        modernMethod = worldClass.getMethod("getNextEntityId");
        if (!modernMethod.isAccessible()) {
          modernMethod.setAccessible(true);
        }
      } catch (Throwable modernLookupFailure) {
        modernLookupFailure.printStackTrace();
      }
    }

    ENTITY_COUNT_FIELD = legacyField;
    ATOMIC_INTEGER_FIELD = atomicField;
    GET_NEXT_ENTITY_ID_METHOD = modernMethod;
  }

  public static void setup() {
    refreshIfRequired();
  }

  public static int acquireNew() {
    refreshIfRequired();
    Integer poll = availableIds.poll();
    return poll != null ? poll : reserveEntityId();
  }

  private static void refreshIfRequired() {
    if (availableIds.size() < REQUIRED_ID_POOL_SIZE) {
      if (Bukkit.isPrimaryThread()) {
        refillEntityIds();
      } else {
        Synchronizer.synchronize(IdentifierReserve::refillEntityIds);
      }
    }
  }

  private static void refillEntityIds() {
    int missing = (REQUIRED_ID_POOL_SIZE - availableIds.size());
    if (missing > 0) {
      Arrays.stream(reserveEntityIds(missing)).forEach(availableIds::add);
    }
  }

  private static int[] reserveEntityIds(int amount) {
    return IntStream.range(0, amount).map(i -> reserveEntityId()).toArray();
  }

  private static int reserveEntityId() {
    try {
      if (ENTITY_COUNT_FIELD != null) {
        if (ATOMIC_INTEGER_FIELD) {
          AtomicInteger atomicInteger = (AtomicInteger) ENTITY_COUNT_FIELD.get(null);
          return atomicInteger.getAndIncrement();
        } else {
          int newId = ENTITY_COUNT_FIELD.getInt(null);
          ENTITY_COUNT_FIELD.setInt(null, newId + 1);
          return newId;
        }
      } else if (GET_NEXT_ENTITY_ID_METHOD != null) {
        Object worldServer = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        return (int) GET_NEXT_ENTITY_ID_METHOD.invoke(worldServer);
      }
    } catch (Throwable exception) {
      exception.printStackTrace();
    }
    return 0;
  }
}
