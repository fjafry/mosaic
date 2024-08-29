/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CriticalityMetrics extends ConfigurableApplication<MetricsConfig, VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    public CriticalityMetrics() {
        super(MetricsConfig.class, "MetricsConfig");
    }

    private double ttc = 0.0;
    private double minTTC = Double.MAX_VALUE;
    private long minTTCtime = 0;
    private MetricsConfig config;

    // trigger is the time when emergency maneuver is called
    // reaction is the actual time when the driver reacts (trigger + reaction time)
    private double ActualTTCAtReaction;
    private double ActualTTCAtTrigger;
    private double PlainTTCAtTrigger;
    private double reactionTime;
    private long emergencyTriggeredAt;

    /**
     * The angle used by the perception module. [degree]
     */
    private static final double VIEWING_ANGLE = 360d;
    /**
     * The distance used by the perception module. [m]
     */
    private static final double VIEWING_RANGE = 300d;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();
        config = this.getConfiguration();
    }

    private void enablePerceptionModule() {
        SimplePerceptionConfiguration perceptionModuleConfiguration = new SimplePerceptionConfiguration.Builder(
                VIEWING_ANGLE, VIEWING_RANGE)
                .build();
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {
        getLog().info("minTTC: {} at time: {}", minTTC, minTTCtime);
        getLog().info("Actual TTC at reaction: {}", ActualTTCAtReaction);
        getLog().info("Actual TTC at trigger: {}", ActualTTCAtTrigger);
        getLog().info("Plain TTC at trigger: {}", PlainTTCAtTrigger);
        getLog().info("Reaction time: {}", reactionTime);
        getLog().info("Emergency triggered at: {}", emergencyTriggeredAt);
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        List<VehicleObject> targetVehicles = perceiveVehicles();
        if (targetVehicles.size() <= 1) {
            return;
        }
        calculateTTC(targetVehicles);
    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof String) {
            String resourceString = (String) resource;
            if (resourceString.equals("StoreTTC")) {
                ActualTTCAtReaction = ttc;
            }
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {
        if (applicationInteraction instanceof MetricsInteraction) {
            final MetricsInteraction metricsInteraction = (MetricsInteraction) applicationInteraction;
            getLog().infoSimTime(this, "MosaicInteractionHandlingApp received MetricsInteraction: {}",
                    metricsInteraction.toString());
            ActualTTCAtTrigger = ttc;
            PlainTTCAtTrigger = metricsInteraction.getPlainTTC();
            reactionTime = metricsInteraction.getReactionTime();
            emergencyTriggeredAt = metricsInteraction.getTriggerTime();
            this.getOs().getEventManager()
                    .newEvent(getOs().getSimulationTime() + (long) (reactionTime * TIME.SECOND),
                            this)
                    .withResource("StoreTTC")
                    .schedule();
        }
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
    }

    private List<VehicleObject> perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();

        List<VehicleObject> targetVehicles = perceivedVehicles.stream()
                .filter(v -> (v.getId().equals(config.driverVehicleId)) || (v.getId().equals(config.vruVehicleId)))
                .collect(Collectors.toList());

        return targetVehicles;
    }

    private void calculateTTC(List<VehicleObject> targetVehicles) {
        VehicleObject ego = targetVehicles.stream().filter(v -> v.getId().equals(config.driverVehicleId)).findFirst()
                .get();
        VehicleObject vru = targetVehicles.stream().filter(v -> v.getId().equals(config.vruVehicleId)).findFirst()
                .get();
        Vector3d r1 = getVectorFromCartesianPoint(ego.getPosition().toCartesian());
        Vector3d r2 = getVectorFromCartesianPoint(vru.getPosition().toCartesian());
        Vector3d v1 = getVectorFromSpeedAndHeading(ego.getSpeed(), ego.getHeading());
        Vector3d v2 = getVectorFromSpeedAndHeading(vru.getSpeed(), vru.getHeading());

        Vector3d d = r1.subtract(r2);
        Vector3d v_rel = v2.subtract(v1);
        if (v_rel.magnitude() == 0) {
            return;
        }
        double a = v_rel.dot(v_rel);
        double b = d.dot(v_rel);
        ttc = b / a;

        if (ttc > 0 && ttc < minTTC) {
            minTTC = ttc;
            minTTCtime = getOs().getSimulationTime();
        }
        getLog().debugSimTime(this, "TTC: {}", ttc);
    }

    private Vector3d getVectorFromSpeedAndHeading(double speed, double heading) {
        // heading is in degrees from true north clockwise
        double y = speed * Math.cos(Math.toRadians(heading));
        double x = speed * Math.sin(Math.toRadians(heading));
        return new Vector3d(x, y, 0);
    }

    private Vector3d getVectorFromCartesianPoint(CartesianPoint point) {
        return new Vector3d(point.getX(), point.getY(), 0);
    }
}
