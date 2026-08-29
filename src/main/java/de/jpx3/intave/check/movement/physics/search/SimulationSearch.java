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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.List;
import java.util.Set;

public interface SimulationSearch {
	default TickSearch greedyFuzzyTickSearch(User user, SimulationEnvironment environment, Simulator simulator) {
		return tickSearch(user, environment, simulator, SimulationSearchOptions.GREEDY_FUZZY);
	}

	default TickSearch greedyFullTickSearch(User user, SimulationEnvironment environment, Simulator simulator) {
		return tickSearch(user, environment, simulator, SimulationSearchOptions.GREEDY_EXACT);
	}

	Set<Simulation> exhaustiveTickSearch(User user, SimulationEnvironment environment, Simulator simulator);

	TickSearch tickSearch(
		User user, SimulationEnvironment movementData,
		Simulator simulator, SimulationSearchOptions options
	);

	List<PostTickSimulation> afterTickMotionCandidates(
		User user, SimulationEnvironment environment,
		Simulator simulator, Position newPosition,
		PostTickMotionType motionType
	);
}
