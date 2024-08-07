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
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.DistanceFilter;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.PositionModifier;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.BoundingBoxOcclusion;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application showcases the functionalities of the {@link org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule}
 * and its {@link org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.PerceptionModifier}s.
 */
public class PerceptionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    /**
     * The angle used by the perception module. [degree]
     */
    private static final double VIEWING_ANGLE = 60d;
    /**
     * The distance used by the perception module. [m]
     */
    private static final double VIEWING_RANGE = 200d;

    @Override
    public void onStartup() {
        getLog().debugSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();

        getOs().requestVehicleParametersUpdate()
                .changeColor(Color.BLUE)
                .apply();
    }

    private void enablePerceptionModule() {
        // filter to emulate occlusion
        BoundingBoxOcclusion boundingBoxOcclusion = new BoundingBoxOcclusion();
        // filter to reduce perception probability based on distance to ego vehicle
        DistanceFilter distanceFilter = new DistanceFilter(getRandom(), 0.0);
        // filter adding noise to longitudinal and lateral
        PositionModifier positionModifier = new PositionModifier(getRandom());

        SimplePerceptionConfiguration perceptionModuleConfiguration =
                new SimplePerceptionConfiguration.Builder(VIEWING_ANGLE, VIEWING_RANGE)
                        .addModifiers(boundingBoxOcclusion, distanceFilter, positionModifier)
                        .build();
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        perceiveVehicles();
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

//    private List<VehicleObject> previouslyPerceivedVehicles = new ArrayList<>();

    /**
     * Perceives vehicles in viewing range
     */
    private void perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        getLog().infoSimTime(this, "Perceived vehicles: {}",
                perceivedVehicles.stream().map(VehicleObject::getId).collect(Collectors.toList()));

//        previouslyPerceivedVehicles = perceivedVehicles;
    }
}
 