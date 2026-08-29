package de.jpx3.intave.check.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.world.placementanalysis.*;
import de.jpx3.intave.check.world.placementanalysis.clicking.Stability;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.event.block.BlockPlaceEvent;

public final class PlacementAnalysis extends Check {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  public static final String COMMON_FLAG_MESSAGE = "suspicious block-placement";

  public PlacementAnalysis(IntavePlugin plugin) {
    super("PlacementAnalysis", "placementanalysis");
    this.plugin = plugin;
    this.setupSubChecks();
    decrementer = new CheckViolationLevelDecrementer(this, 0.15);
  }

  public void setupSubChecks() {
    boolean useTimings = configuration().settings().boolBy("check-timings", configuration().settings().boolBy("check_timings", true));
    appendPlayerCheckPart(Constraint.class);
    appendPlayerCheckPart(SmartSpeed.class);

    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;

    try {
      appendPlayerCheckPart(Stability.class);

      if (useTimings) {
        appendPlayerCheckPart(Speed.class);
        appendPlayerCheckPart(Sneak.class);
      }
      appendPlayerCheckPart(Snap.class);
      appendPlayerCheckPart(SharpRotation.class);
      appendPlayerCheckPart(BlockRotation.class);
      // appendPlayerCheckPart(SneakAndPlace.class);
//      }
    } catch (Exception | Error e) {
      // classes might be missing
    }
    appendPlayerCheckPart(RotationSpeed.class);
//    appendPlayerCheckPart(PacketOrder.class);
    appendCheckPart(new Facing(this));
    appendPlayerCheckPart(RoundedRotation.class);

    appendPlayerCheckPart(AngleSnap.class);
    appendPlayerCheckPart(RotationFlick.class);
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    User user = userOf(place.getPlayer());
    decrementer.decrement(user, 0.1);
  }

  public void applyPlacementAnalysisDamageCancel(User user, String checkId) {
//    user.nerf(AttackNerfStrategy.CANCEL_FIRST_HIT, checkId);
//    user.nerf(AttackNerfStrategy.DMG_LIGHT, checkId);
  }
}
