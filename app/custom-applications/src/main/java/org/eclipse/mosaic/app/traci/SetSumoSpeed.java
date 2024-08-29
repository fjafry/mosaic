package org.eclipse.mosaic.app.traci;

import org.eclipse.mosaic.app.vehicleconfig.VehicleConfig;
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
 * This application sets the speed mode for the vehicle in SUMO through the
 * interface
 * provided by MOSAIC which allows sending messages to TraCI and reacting on
 * received TraCI response.
 */
public class SetSumoSpeed extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    private String lastSentMsgId;

    /**
     * Here a byte array is assembled that follows the traci protocol. With the byte
     * array constructed
     * here we instruct sumo directly to get the heading for the
     * provided vehicle.
     * <a href="https://sumo.dlr.de/docs/TraCI/Change_Vehicle_State.html">...</a>
     *
     * @param vehicleId Vehicle to set the speed of
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand(String vehicleId, double speed) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE_CHANGE = (byte) 0xc4; // Bits Traci Command (0xc4 for vehicle value change)
        final byte TRACI_SET_SPEED = (byte) 0x40; // Bits Traci Variable Identifier (0x40 for speed)

        try {
            dos.writeByte(TRACI_VEHICLE_CHANGE);
            dos.writeByte(TRACI_SET_SPEED);
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
            dos.writeByte(0xb);// tell that we will be sending double
            dos.writeDouble(speed); // sumo speed mode int bitset.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup of set sumo speed from config");
        getLog().infoSimTime(
                this,
                "Attempt to set sumo speed on next process event");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
            getLog().infoSimTime(
                    this,
                    "Received TraCI message from Sumo. Speed of vehicle is set");
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {

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
            getLog().info("Configs speed equals {}", config.initialSetSpeed);
            final byte[] traciMsg = assembleTraciCommand(getOs().getId(), config.initialSetSpeed); // assemble the TraCI
                                                                                                   // msg
            // for sumo

            String lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);

            if (!lastSentMsgId.isEmpty()) {
                getLog().infoSimTime(
                        this,
                        "set sumo speed. integer value {}",
                        config.initialSetSpeed);
            }
        }
    }
}
