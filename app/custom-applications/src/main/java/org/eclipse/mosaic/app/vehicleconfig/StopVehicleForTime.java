/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.app.vehicleconfig;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import org.eclipse.mosaic.lib.enums.VehicleStopMode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.mosaic.rti.TIME;

/**
 * This application stops the vehicle in stopAt seconds for stopFor seconds
 */
public class StopVehicleForTime extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    static class StopVehicleConfig {
        protected final int stopAt;
        protected final int stopFor;

        public StopVehicleConfig(int stopAt, int stopFor) {
            this.stopAt = stopAt;
            this.stopFor = stopFor;
        }
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup of stop vehicle by reading from config");
        getLog().infoSimTime(
                this,
                "Attempt to get config on next process event"
        );
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
    }


    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown");
    }

    @Override
    public void processEvent(Event event) throws Exception {

        Object resource = event.getResource();
        if (resource instanceof VehicleConfig) {
            VehicleConfig config = (VehicleConfig) resource;
            getLog().infoSimTime(this, "Vehicle config read from json file");
            getLog().info("Configs stopAt equals {}", config.stopAt);
            getLog().info("Configs stopFor equals {}", config.stopFor);

            StopVehicleConfig stopVehicleConfig = new StopVehicleConfig(config.stopAt, config.stopFor);
            this.getOs().getEventManager()
                    .newEvent(getOs().getSimulationTime() + config.stopAt * TIME.SECOND, this)
                    .withResource(stopVehicleConfig)
                    .schedule();
        } else if (resource instanceof StopVehicleConfig) {
            StopVehicleConfig config = (StopVehicleConfig) resource;
            getLog().infoSimTime(this, "Will stop vehicle now");
            this.getOs().stopNow(VehicleStopMode.STOP, config.stopFor * TIME.SECOND);
        }
    }
}
