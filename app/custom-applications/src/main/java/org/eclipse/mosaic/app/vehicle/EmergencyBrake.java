/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.SensorType;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.v2x.etsi.DenmContent;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class implements an application for vehicles.
 * In case the vehicle sensors detect an obstacle the vehicle will perform an
 * emergency brake.
 * If the emergency brake endures a specified minimum time duration a DENMessage
 * is sent out.
 */
public class EmergencyBrake extends ConfigurableApplication<EmergencyBrakeConfig, VehicleOperatingSystem>
        implements VehicleApplication {

    // Keep status of the emergency brake performed on obstacle
    private boolean emergencyBrake = false;
    private long stoppedAt = Long.MIN_VALUE;
    EmergencyBrakeConfig config;

    /**
     * Initializes an instance of the {@link EmergencyBrakeApp}.
     */
    public EmergencyBrake() {
        super(EmergencyBrakeConfig.class, "EmergencyBrakeConfig");
    }

    @Override
    public void onStartup() {
        config = this.getConfiguration();
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof EmergencyBrakeTrigger) {
            // Initiate emergency brake if obstacle is detected
            if (!emergencyBrake) {
                stoppedAt = getOs().getSimulationTime();
                getOs().changeSpeedWithForcedAcceleration(config.targetSpeed,
                        config.deceleration);
                emergencyBrake = true;
                getLog().infoSimTime(this, "Performing emergency brake caused by detected obstacle");
            }
        }

    }
}
