package org.eclipse.mosaic.app.traci;

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
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
 * This application stops the vehicle at the specified position in SUMO through
 * the interface
 * provided by MOSAIC which allows sending messages to TraCI and reacting on
 * received TraCI response.
 */
public class CreateObstacle extends ConfigurableApplication<ObstacleConfig, VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    public CreateObstacle() {
        super(ObstacleConfig.class, "ObstacleConfig");
    }

    static class StopVehicleConfig {
        protected final String edgeId;
        protected final double endPos;
        protected final int laneIndex;
        protected final double duration;
        protected final int stopFlags;

        public StopVehicleConfig(String edgeId, double endPos, int laneIndex, double duration, int stopFlags) {
            this.edgeId = edgeId;
            this.endPos = endPos;
            this.laneIndex = laneIndex;
            this.duration = duration;
            this.stopFlags = stopFlags;
        }
    }

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
    private byte[] assembleTraciCommand(String vehicleId, StopVehicleConfig stopConfig) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE_CHANGE = (byte) 0xc4; // Bits Traci Command (0xc4 for vehicle value change)
        final byte TRACI_SET_STOP = (byte) 0x12; // Bits Traci Variable Identifier (0x12 for stop)

        try {
            dos.writeByte(TRACI_VEHICLE_CHANGE);
            dos.writeByte(TRACI_SET_STOP);
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
            dos.writeByte(0x0f); // compound type
            dos.writeInt(5); // number of parameters
            dos.writeByte(0xc); // string type
            dos.writeInt(stopConfig.edgeId.length()); // Length of edgeId
            dos.write(stopConfig.edgeId.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(0xb); // double type
            dos.writeDouble(stopConfig.endPos); // endPos
            dos.writeByte(0x8); // byte type
            dos.writeByte((byte) stopConfig.laneIndex); // laneIndex
            dos.writeByte(0xb); // double type
            dos.writeDouble(stopConfig.duration); // duration
            dos.writeByte(0x8); // byte type
            dos.writeByte((byte) stopConfig.stopFlags); // stopFlags
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup of create obstacle");
        ObstacleConfig config = this.getConfiguration();
        getLog().infoSimTime(this, "Startup of ");

        this.getOs().getEventManager()
                .newEvent(getOs().getSimulationTime() + 1, this)
                .withResource(config)
                .schedule();
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
                    "Received TraCI message from Sumo. Stop of vehicle is set");
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {

    }

    @Override
    public void processEvent(Event event) throws Exception {

        Object resource = event.getResource();
        if (resource instanceof ObstacleConfig) {
            ObstacleConfig config = (ObstacleConfig) resource;

            StopVehicleConfig stopVehicleConfig = new StopVehicleConfig(config.edgeId, config.endPos, config.laneIndex,
                    config.duration, config.stopFlags);

            getLog().infoSimTime(this, "Will stop vehicle");
            final byte[] traciMsg = assembleTraciCommand(getOs().getId(), stopVehicleConfig); // assemble the TraCI msg
                                                                                              // for sumo
            String lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
            if (!lastSentMsgId.isEmpty()) {
                getLog().infoSimTime(
                        this,
                        "set sumo stop");
            }
        }
    }
}
