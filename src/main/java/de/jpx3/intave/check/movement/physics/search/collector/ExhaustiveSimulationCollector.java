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

package de.jpx3.intave.check.movement.physics.search.collector;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collector;

public final class ExhaustiveSimulationCollector {
	private final User user;
	private final SimulationEnvironment environment;
	private final Position lastReportedPosition;
	private final int maxFlyingSimulations;
	private final BestSimulationSet bestSimulations;
	private final ToDoubleFunction<Simulation> distanceFunction;
	private Set<Simulation> flyingSimulations;
	private int simulationsDone;

	private ExhaustiveSimulationCollector(
		User user,
		SimulationEnvironment environment,
		Position lastReportedPosition,
		int maxFlyingSimulations,
		BestSimulationSet bestSimulations,
		ToDoubleFunction<Simulation> distanceFunction
	) {
		this.user = user;
		this.environment = environment;
		this.lastReportedPosition = lastReportedPosition;
		this.maxFlyingSimulations = maxFlyingSimulations;
		this.bestSimulations = bestSimulations;
		this.distanceFunction = distanceFunction;
	}

	private void add(Simulation simulation) {
		bestSimulations.add(simulation, distanceFunction.applyAsDouble(simulation));
		if (resultsInFlyingPacket(simulation)) {
			addFlyingSimulation(simulation);
		}
		simulationsDone++;
	}

	public int simulationsDone() {
		return simulationsDone;
	}

	public List<Simulation> flyingSimulations() {
		return flyingSimulations == null ? Collections.emptyList() : new ArrayList<>(flyingSimulations);
	}

	private ExhaustiveSimulationCollector mergedWith(ExhaustiveSimulationCollector other) {
		if (other.flyingSimulations != null) {
			other.flyingSimulations.forEach(this::addFlyingSimulation);
		}
		simulationsDone += other.simulationsDone;
		return this;
	}

	private void addFlyingSimulation(Simulation simulation) {
		if (flyingSimulations == null) {
			flyingSimulations = new HashSet<>();
		}
		flyingSimulations.add(simulation.reusableCopy());
		if (flyingSimulations.size() > maxFlyingSimulations) {
			Simulation worstFlyingSimulation = null;
			double worstDistance = Double.MIN_VALUE;
			for (Simulation flyingSimulation : flyingSimulations) {
				Position flyingPosition = flyingSimulation.environment().lastPosition()
					.add(flyingSimulation.result().offsetMotion());
				double distance = lastReportedPosition.distance(flyingPosition);
				if (worstFlyingSimulation == null || distance > worstDistance) {
					worstFlyingSimulation = flyingSimulation;
					worstDistance = distance;
				}
			}
			flyingSimulations.remove(worstFlyingSimulation);
		}
	}

	private boolean resultsInFlyingPacket(Simulation simulation) {
		double flyingLimit = user.meta().protocol().flyingPacketUncertaintyRadius();
		Position simulatedPosition = simulation.postTickPosition();
		return lastReportedPosition.distance(simulatedPosition) < flyingLimit;
	}

	public static Collector<Simulation, ExhaustiveSimulationCollector, ExhaustiveSimulationCollector> forEnvironment(
		User user,
		SimulationEnvironment environment,
		Position lastReportedPosition,
		int maxFlyingSimulations,
		BestSimulationSet bestSimulations,
		ToDoubleFunction<Simulation> distanceFunction
	) {
		return Collector.of(
			() -> new ExhaustiveSimulationCollector(
				user, environment, lastReportedPosition, maxFlyingSimulations, bestSimulations, distanceFunction
			),
			ExhaustiveSimulationCollector::add,
			ExhaustiveSimulationCollector::mergedWith
		);
	}

}
