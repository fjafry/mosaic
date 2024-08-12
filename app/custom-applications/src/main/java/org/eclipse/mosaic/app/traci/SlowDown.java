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

package org.eclipse.mosaic.app.traci;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application slows down (or speeds up) the vehicle within the specified
 * time in SUMO through
 * the interface
 * provided by MOSAIC which allows sending messages to TraCI and reacting on
 * received TraCI response.
 */
public class SlowDown extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    private String lastSentMsgId;

    /**
     * Here a byte array is assembled that follows the traci protocol. With the byte
     * array constructed
     * here we instruct sumo directly to get the heading for the
     * provided vehicle.
     * <a href="https://sumo.dlr.de/docs/TraCI/Change_Vehicle_State.html">...</a>
     *
     * @param vehicleId Vehicle to set the speed mode of
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand(String vehicleId, SlowDownParams params) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE_CHANGE = (byte) 0xc4; // Bits Traci Command (0xc4 for vehicle value change)
        final byte TRACI_SLOW_DOWN = (byte) 0x14; // Bits Traci Variable Identifier (0x14 for slow down)

        try {
            dos.writeByte(TRACI_VEHICLE_CHANGE);
            dos.writeByte(TRACI_SLOW_DOWN);
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
            dos.writeByte(0x0f); // compound type
            dos.writeInt(2); // number of parameters
            dos.writeByte(0xb); // double type
            dos.writeDouble(params.targetSpeed); // target speed
            dos.writeByte(0xb); // double type
            dos.writeDouble(params.duration); // duration
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown");
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
            getLog().infoSimTime(
                    this,
                    "Received TraCI message from Sumo. Vehicle will slow down");
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {

    }

    @Override
    public void processEvent(Event event) throws Exception {

        Object resource = event.getResource();
        if (resource instanceof SlowDownParams) {
            // Initiate emergency brake
            getLog().infoSimTime(this, "Vehicle emergency break is requested through traci");
            SlowDownParams params = (SlowDownParams) resource;

            final byte[] traciMsg = assembleTraciCommand(getOs().getId(), params); // assemble the TraCI msg for sumo
            String lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
            if (!lastSentMsgId.isEmpty()) {
                getLog().infoSimTime(
                        this,
                        "Sent TraCI message to Sumo. Vehicle will slow down");
            }
        }
    }
}
