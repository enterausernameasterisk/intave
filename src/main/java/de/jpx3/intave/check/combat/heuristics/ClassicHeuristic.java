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

package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

public class ClassicHeuristic<M extends CheckCustomMetadata> extends MetaCheckPart<Heuristics, M> {
  protected final Heuristics parentCheck;
  protected final String nerfId;
  private final HeuristicsClassicType type;
  private final int violationLevelIncrease;

  protected ClassicHeuristic(Heuristics parentCheck, HeuristicsClassicType type, Class<? extends M> metaClass) {
    super(parentCheck, metaClass);
    this.parentCheck = parentCheck;
    this.type = type;
    this.violationLevelIncrease = parentCheck.classicViolationLevelMap().getOrDefault(type, 0);
    this.nerfId = type.configurationName();
  }

  protected void flag(User user, String message, String details) {
    String verboseDetails = type.displayName();
    if (!details.isEmpty()) {
      verboseDetails += ", " + details;
    }
    Violation violation = Violation.builderFor(Heuristics.class)
      .forUser(user).withMessage(message)
      .withDetails(verboseDetails)
      .withVL(violationLevelIncrease)
      .withDefaultThreshold()
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  @Override
  public boolean enabled() {
    return violationLevelIncrease >= 0;
  }
}
