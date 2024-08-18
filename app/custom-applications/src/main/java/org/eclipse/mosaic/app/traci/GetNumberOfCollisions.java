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

import org.apache.commons.lang3.Validate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application gets number of collisions in SUMO through the
 * interface traci
 */
public class GetNumberOfCollisions extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    private String lastSentMsgId;
    private boolean collisionDetected = false;

    /**
     * Here a byte array is assembled that follows the traci protocol. With the byte
     * array constructed
     * here we instruct sumo directly to get the heading for the
     * provided vehicle.
     * <a href="https://sumo.dlr.de/docs/TraCI/Vehicle_Value_Retrieval.html">...</a>
     *
     * @param vehicleId Vehicle to set the speed mode of
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_SIM_RETRIEVAL = (byte) 0xab; // Bits Traci Command (0xab for sim value retrieval)
        final byte TRACI_GET_COLLISION_AMOUNT = (byte) 0x80; // Bits Traci Variable Identifier (0x81 for number of
                                                             // collisions)
        String simId = "rand";
        try {
            dos.writeByte(TRACI_SIM_RETRIEVAL);
            dos.writeByte(TRACI_GET_COLLISION_AMOUNT);
            dos.writeInt(simId.length());
            dos.write(simId.getBytes(StandardCharsets.UTF_8));
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
        final byte[] traciMsg = assembleTraciCommand(); // assemble the TraCI msg for sumo

        lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
    }

    /**
     * This method prints out the response given by Sumo, in this case the number of
     * collisions
     *
     * @param sumoTraciResult the response container.
     */
    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
            int numCollisions = decodeGetHeading(sumoTraciResult.getTraciCommandResult());
            if (numCollisions > 0) {
                collisionDetected = true;
                getLog().infoSimTime(
                        this,
                        "Number of collisions is {}",
                        numCollisions);
            }
        }
    }

    private byte[] readBytes(final DataInputStream in, final int bytes) throws IOException {
        final byte[] result = new byte[bytes];

        in.read(result, 0, bytes);

        return result;
    }

    private String readString(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        final byte[] bytes = readBytes(in, length);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int decodeGetHeading(final byte[] msg) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(msg);
        final DataInputStream dis = new DataInputStream(bais);

        try {
            int response = dis.readUnsignedByte(); // should be 0xbb for response sim variable
            Validate.isTrue(response == 0xbb);
            int command = dis.readUnsignedByte(); // should be 0x80 for number of collisions
            Validate.isTrue(command == 0x80);
            readString(dis); // vehicle for which the response is
            int variableType = dis.readUnsignedByte(); // type of response, should be 0x9 for int
            Validate.isTrue(variableType == 0x9);
            int collisions = dis.readInt(); // the actual value, angle in degrees here

            return collisions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {

    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Collision detected: {}", collisionDetected);
        getLog().infoSimTime(this, "Shutdown");
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }
}
