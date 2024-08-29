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
 * This application gets the heading of the vehicle in SUMO through the interface traci
 */
public class GetSumoHeading extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, MosaicApplication {

    private String lastSentMsgId;

    static class HeadingResponse {
        protected final String vehicleId;
        protected final double angle;

        public HeadingResponse(String vehicleId, double angle) {
            this.vehicleId = vehicleId;
            this.angle = angle;
        }
    }

    /**
     * Here a byte array is assembled that follows the traci protocol. With the byte array constructed
     * here we instruct sumo directly to get the heading for the
     * provided vehicle. <a href="https://sumo.dlr.de/docs/TraCI/Vehicle_Value_Retrieval.html">...</a>
     *
     * @param vehicleId Vehicle to set the speed mode of
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand(String vehicleId) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE_RETRIEVAL = (byte) 0xa4;  // Bits Traci Command (0xa4 for vehicle value retrieval)
        final byte TRACI_GET_ANGLE = (byte) 0x43; // Bits Traci Variable Identifier (0x43 for angle)

        try {
            dos.writeByte(TRACI_VEHICLE_RETRIEVAL);
            dos.writeByte(TRACI_GET_ANGLE);
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
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
        final byte[] traciMsg = assembleTraciCommand(getOs().getId()); // assemble the TraCI msg for sumo

        lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
    }

    /**
     * This method prints out the response given by Sumo, in this case the heading
     * of the vehicle this application runs on.
     *
     * @param sumoTraciResult the response container.
     */
    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
            final HeadingResponse headingResponse = decodeGetHeading(sumoTraciResult.getTraciCommandResult());
            getLog().infoSimTime(
                    this,
                    "Heading of vehicle {} is : {} degrees",
                    headingResponse.vehicleId,
                    headingResponse.angle
            );
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

    private HeadingResponse decodeGetHeading(final byte[] msg) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(msg);
        final DataInputStream dis = new DataInputStream(bais);

        try {
            int response = dis.readUnsignedByte(); // should be 0xb4 for response vehicle variable
            Validate.isTrue(response == 0xb4);
            int command = dis.readUnsignedByte(); // should be 0x43 for angle
            Validate.isTrue(command == 0x43);
            String vehicleId = readString(dis); // vehicle for which the response is
            int variableType = dis.readUnsignedByte(); // type of response, should be 0x0b for double
            Validate.isTrue(variableType == 0xb);
            double angle = dis.readDouble(); // the actual value, angle in degrees here

            return new HeadingResponse(vehicleId, angle);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    }
}
 