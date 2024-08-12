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
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CalculateTTC extends ConfigurableApplication<TTCconfig, VehicleOperatingSystem>
        implements VehicleApplication {

    public CalculateTTC() {
        super(TTCconfig.class, "TTCconfig");
    }

    private double ttc = 0.0;
    private double minTTC = Double.MAX_VALUE;
    private TTCconfig config;

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
        getLog().infoSimTime(this, "minTTC: {}", minTTC);

    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        List<VehicleObject> targetVehicles = perceiveVehicles();
        if (targetVehicles.isEmpty()) {
            return;
        }
        calculateTTC(targetVehicles.get(0));
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    private List<VehicleObject> perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        // getLog().infoSimTime(this, "Perceived vehicles: {}",
        // perceivedVehicles.stream().map(VehicleObject::getId)
        // .collect(Collectors.toList()));
        List<VehicleObject> targetVehicles = perceivedVehicles.stream().filter(v -> config.vehicleId.equals(v.getId()))
                .collect(Collectors.toList());

        return targetVehicles;
    }

    private void calculateTTC(VehicleObject targetVehicle) {
        Vector3d r1 = getVectorFromCartesianPoint(getOs().getPosition().toCartesian());
        Vector3d r2 = getVectorFromCartesianPoint(targetVehicle.getPosition().toCartesian());
        Vector3d v1 = getVectorFromSpeedAndHeading(getOs().getVehicleData().getSpeed(),
                getOs().getVehicleData().getHeading());
        Vector3d v2 = getVectorFromSpeedAndHeading(targetVehicle.getSpeed(), targetVehicle.getHeading());

        Vector3d d = r1.subtract(r2);
        Vector3d v_rel = v2.subtract(v1);
        if (v_rel.magnitude() == 0) {
            return;
        }
        double a = v_rel.dot(v_rel);
        double b = d.dot(v_rel);
        b = b < 0 ? -b : b;
        ttc = b / a;

        if (ttc > 0 && ttc < minTTC) {
            minTTC = ttc;
        }
        getLog().infoSimTime(this, "TTC: {}", ttc);
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
