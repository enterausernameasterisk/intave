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
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collector;

public final class MergingSimulationCollector {
	private final User user;
	private final SimulationEnvironment environment;
	private final Motion targetOffsetMotion;
	private final Position lastReportedPosition;
	private final int maxFlyingSimulations;
	private Simulation best = Simulation.invalid();
	private Set<Simulation> possibleFlyingSimulations = null;
	private int simulationsDone;

	private MergingSimulationCollector(
		User user,
		SimulationEnvironment environment,
		int maxFlyingSimulations,
		Motion targetOffsetMotion,
		Position lastReportedPosition
	) {
		this.user = user;
		this.environment = environment;
		this.maxFlyingSimulations = maxFlyingSimulations;
		this.targetOffsetMotion = targetOffsetMotion;
		this.lastReportedPosition = lastReportedPosition;
	}

	private void add(Simulation simulation) {
		if (resultsInFlyingPacket(simulation)) {
			addFlyingSimulation(simulation);
		}
		best = selectBest(best, simulation);
		simulationsDone++;
	}

	public int simulationsDone() {
		return simulationsDone;
	}

	public Simulation bestSimulation() {
		return best;
	}

	public List<Simulation> flyingSimulations() {
		return possibleFlyingSimulations == null ? Collections.emptyList() : new ArrayList<>(possibleFlyingSimulations);
	}

	private MergingSimulationCollector mergedWith(MergingSimulationCollector other) {
		MergingSimulationCollector merged = new MergingSimulationCollector(
			user,
			environment,
			maxFlyingSimulations,
			targetOffsetMotion,
			lastReportedPosition
		);
		merged.add(best);
		merged.add(other.best);

		if (possibleFlyingSimulations != null) {
			possibleFlyingSimulations.forEach(merged::add);
		}
		if (other.possibleFlyingSimulations != null) {
			other.possibleFlyingSimulations.forEach(merged::add);
		}
		merged.simulationsDone = simulationsDone + other.simulationsDone;
		return merged;
	}

	private void addFlyingSimulation(Simulation simulation) {
		if (possibleFlyingSimulations == null) {
			possibleFlyingSimulations = new HashSet<>();
		}
		possibleFlyingSimulations.add(simulation.reusableCopy());
		if (possibleFlyingSimulations.size() > maxFlyingSimulations) {
			Simulation worstFlying = Simulation.invalid();
			for (Simulation sim : possibleFlyingSimulations) {
				worstFlying = selectWorseFlying(worstFlying, sim, lastReportedPosition);
			}
			possibleFlyingSimulations.remove(worstFlying);
		}
	}

	private boolean resultsInFlyingPacket(Simulation simulation) {
		double flyingLimit = user.meta().protocol().flyingPacketUncertaintyRadius();
		Position simulatedPosition = simulation.postTickPosition();
		return lastReportedPosition.distance(simulatedPosition) < flyingLimit;
	}

	private Simulation selectBestFlying(Simulation a, Simulation b, Position receivedPosition) {
		if (a == Simulation.invalid()) {
			return b;
		}
		if (b == Simulation.invalid()) {
			return a;
		}
		Position positionA = a.environment().lastPosition().add(a.result().offsetMotion());
		Position positionB = b.environment().lastPosition().add(b.result().offsetMotion());
		double distanceA = receivedPosition.distance(positionA);
		double distanceB = receivedPosition.distance(positionB);
		if (distanceA < distanceB) {
			return a;
		} {
			return b;
		}
	}

	private Simulation selectWorseFlying(Simulation a, Simulation b, Position receivedPosition) {
		if (a == Simulation.invalid()) {
			return b;
		}
		if (b == Simulation.invalid()) {
			return a;
		}
		return selectBestFlying(a, b, receivedPosition) == a ? b : a;
	}

	private Simulation selectBest(Simulation current, Simulation simulation) {
		return current.select(simulation);
	}

	public static Collector<Simulation, MergingSimulationCollector, MergingSimulationCollector> forEnvironment(
		User user, SimulationEnvironment environment, int maxFlyingSimulations
	) {
		return forEnvironmentWithCustomTargets(
			user, environment, environment.sentOffsetMotion(), environment.lastPosition(), maxFlyingSimulations
		);
	}

	public static Collector<Simulation, MergingSimulationCollector, MergingSimulationCollector> forEnvironmentWithCustomTargets(
		User user, SimulationEnvironment environment,
		@NotNull Motion targetOffset,
		@NotNull Position lastReportedPosition,
		int maxFlyingSimulations
	) {
		return Collector.of(
			() -> new MergingSimulationCollector(
				user,
				environment,
				maxFlyingSimulations,
				targetOffset,
				lastReportedPosition
			),
			MergingSimulationCollector::add,
			MergingSimulationCollector::mergedWith
		);
	}
}
