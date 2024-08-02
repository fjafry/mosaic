package custom.SetVehicleParamsFromConfig;

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

import custom.SetVehicleParamsFromConfig.VehicleConfig;
/**
 * This application sets the speed mode for the vehicle in SUMO through the interface
 * provided by MOSAIC which allows sending messages to TraCI and reacting on received TraCI response.
 */
public class SetSumoSpeedMode extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, MosaicApplication {

    private String lastSentMsgId;
    /**
     * The "meat" of this application. Here a byte array is assembled
     * that follows the traci protocol. With the byte array constructed
     * here we instruct sumo directly to set the speed mode for the
     * provided vehicle. https://sumo.dlr.de/docs/TraCI/Change_Vehicle_State.html#speed_mode_0xb3
     *
     * @param vehicleId Vehicle to set the speed mode of
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand(String vehicleId, int speedMode) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE = (byte) 0xc4;
        final byte TRACI_SET_SPEED = (byte) 0xb3;

        try {
            dos.writeByte(TRACI_VEHICLE); // Bits Traci Command (0xc4 for vehicle value change)
            dos.writeByte(TRACI_SET_SPEED); // Bits Traci Variable Identifier (0xb3 for Speed mode)
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
            dos.writeByte(0x09);//tell that we will be sending int
            dos.writeInt(speedMode); //sumo speed mode.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }


   @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup of set sumo speed mode from config");
        getLog().infoSimTime(
                    this,
                    "Attempt to set sumo speed mode on next process event"
            );
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
    }


    @Override
     public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
         if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
             getLog().infoSimTime(
                     this,
                     "Received TraCI message from Sumo. Speed mode of vehicle is set"
             );
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
            getLog().info("Configs speedMode equals " + config.speedMode);
            final byte[] traciMsg = assembleTraciCommand(getOs().getId(), config.speedMode); // assemble the TraCI msg for sumo
            
            String lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
            
            if(lastSentMsgId.length() >0){
                getLog().infoSimTime(
                    this,
                    "set sumo speed mode. integer value {}", 
                    config.speedMode
                    );
                }
        }
    }
}
