package de.jpx3.intave.module.violation;

import java.util.Arrays;
import java.util.List;

public enum ViolationVerboseMode {
  ALL {
    @Override
    public boolean doVerbose(ViolationContext context) {
      return true;
    }
  },
  MITIGATED {
    @Override
    public boolean doVerbose(ViolationContext context) {
      return context.violationLevelPassedPreventionActivation();
    }
  },
  SELECTED {
    @Override
    public boolean doVerbose(ViolationContext context) {
      String checkName = context.violation().check().name();
      return MITIGATED.doVerbose(context) && SELECTED_CHECKS.contains(checkName.toLowerCase());
    }
  };

  private static final List<String> SELECTED_CHECKS = Arrays.asList("cloud", "heuristics", "placementanalysis", "attackraytrace", "protocolscanner");

  public abstract boolean doVerbose(ViolationContext context);
}
