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

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.fed.application.app.api.Application;

import java.util.List;

/**
 * A configuration file(s) should be placed in "application" folder.
 * The filename can end with "_unitId" (e.g. ReadVehicleConfig.json) and will then only be used by the specified unit.
 * It allows configuring one application in different ways for different vehicles.
 * If the configuration filename doesn't include any unit id, it will be used for all unspecified units.
 */
public class ReadVehicleConfig extends ConfigurableApplication<VehicleConfig, VehicleOperatingSystem> {

    public ReadVehicleConfig() {
        super(VehicleConfig.class, "ReadVehicleConfig");
    }

    @Override
    public void onStartup() {
        VehicleConfig config = this.getConfiguration();
        getLog().infoSimTime(this, "Startup of ");
        List<? extends Application> applications = getOs().getApplications();
        for (Application application : applications) {
            this.getOs().getEventManager()
                    .newEvent(getOs().getSimulationTime() + 1, application)
                    .withResource(config)
                    .schedule();
        }
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof VehicleConfig) {
            getLog().infoSimTime(this, "Vehicle config read from json file");
        }
    }

}
 