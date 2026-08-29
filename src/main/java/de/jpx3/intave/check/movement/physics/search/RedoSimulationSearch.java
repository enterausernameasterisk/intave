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
import de.jpx3.intave.check.movement.physics.evaluation.EvaluationTag;
import de.jpx3.intave.check.movement.physics.evaluation.SimulationEvaluator;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RedoSimulationSearch implements SimulationSearch {
	private final SimulationSearch delegate;
	private final SimulationEvaluator evaluator;

	public RedoSimulationSearch(SimulationSearch delegate, SimulationEvaluator evaluator) {
		this.delegate = delegate;
		this.evaluator = evaluator;
	}

	@Override
	public Set<Simulation> exhaustiveTickSearch(User user, SimulationEnvironment environment, Simulator simulator) {
		return delegate.exhaustiveTickSearch(user, environment, simulator);
	}

	@Override
	public TickSearch tickSearch(
		User user, SimulationEnvironment simulationEnvironment,
		Simulator simulator, SimulationSearchOptions options
	) {
		if (!options.allowFuzziness()) {
			return delegate.tickSearch(user, simulationEnvironment, simulator, options);
		}
		SimulationEnvironment branchEnvironment = simulationEnvironment.mutableView();
		TickSearch firstSearch = delegate.greedyFuzzyTickSearch(user, branchEnvironment, simulator);
		Simulation firstSimulation = firstSearch.simulation();

		double difference = firstSimulation.offsetDifference();
		if (difference < 0.0005 || firstSimulation.isFromExhaustiveSearch()) {
			return firstSearch;
		}

		Motion offsetMotion = firstSimulation.offsetMotion();
		SimulationEnvironment resultEnvironment = firstSimulation.environment();
		Set<EvaluationTag> unusedEvalTags = new HashSet<>();
		double horizontalVL = evaluator.calculateHorizontalViolationIncrease(
			user, resultEnvironment, offsetMotion.motionX, offsetMotion.motionZ, false, false, unusedEvalTags
		);
		if (horizontalVL > 0) {
			TickSearch search = delegate.greedyFullTickSearch(user, simulationEnvironment, simulator);
			Simulation simulation = search.simulation();
			TickSearch combinedSearch = search.withAdditionalSearch(firstSearch);
			simulation.setSimulationCount(combinedSearch.simulationCount());
			simulation.appendPurple("redo:H("+firstSimulation.blueDetails()+")");
			return combinedSearch;
		}
		double verticalVL = evaluator.calculateVerticalViolationIncrease(
			user, resultEnvironment, offsetMotion.motionY, false, false, unusedEvalTags
		);
		if (verticalVL > 0) {
			TickSearch search = delegate.greedyFullTickSearch(user, simulationEnvironment, simulator);
			Simulation simulation = search.simulation();
			TickSearch combinedSearch = search.withAdditionalSearch(firstSearch);
			simulation.setSimulationCount(combinedSearch.simulationCount());
			simulation.appendPurple("redo:V("+firstSimulation.blueDetails()+")");
			return combinedSearch;
		}
		return firstSearch;
	}

	@Override
	public List<PostTickSimulation> afterTickMotionCandidates(User user, SimulationEnvironment environment, Simulator simulator, Position newPosition, PostTickMotionType motionType) {
		return delegate.afterTickMotionCandidates(user, environment, simulator, newPosition, motionType);
	}

	public static SimulationSearch of(SimulationSearch search, SimulationEvaluator simulationEvaluator) {
		return new RedoSimulationSearch(search, simulationEvaluator);
	}
}
