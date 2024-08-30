package org.eclipse.mosaic.app.communication;

import org.eclipse.mosaic.app.vehicle.EmergencyBrakeTrigger;
import org.eclipse.mosaic.app.vehicleconfig.VehicleConfig;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is a simple application that reads received V2X messages and logs either
 * the user tagged value if the received message was a CAM
 * or the message type and source name if it wasn't a CAM.
 */
public class ReceiveCAM extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication, CommunicationApplication {

    private double emergencyBrakeMinTTC = 3.0;
    private boolean emergencyBrakeTrigger = false;

    /**
     * We should enable ad hoc module here to be able to receive messages that were
     * sent per ad hoc
     */
    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Set up");
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio().channel(AdHocChannel.CCH).power(50).create());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage msg = receivedV2xMessage.getMessage();

        if (msg instanceof Cam) {
            Cam camMsg = (Cam) msg;
            getLog().infoSimTime(this,
                    "CAM message arrived, from vehicle: {}, at position: {}. transmission delay: {}",
                    camMsg.getUnitID(),
                    camMsg.getPosition(),
                    (getOs().getSimulationTime() - camMsg.getGenerationTime()));
            checkEmergencyBrake(camMsg.getPosition().toCartesian());

        } else {
            getLog().infoSimTime(this, "Arrived message was not a CAM, but a {} msg from {}", msg.getSimpleClassName(),
                    msg.getRouting().getSource().getSourceName());
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Tear down");
    }

    /**
     * from EventProcessor interface
     **/
    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof VehicleConfig) {
            VehicleConfig config = (VehicleConfig) resource;
            getLog().infoSimTime(this, "Vehicle config read from json file");
            getLog().info("Configs emergencyBrakeMinTTC is {} s", config.emergencyBrakeMinTTC);
            emergencyBrakeMinTTC = config.emergencyBrakeMinTTC;
        }
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

    private double calculateTTC(VehicleData egoVehicle, CartesianPoint targetPoint) {
        double ttc = 0.0;
        double distance = egoVehicle.getPosition().toCartesian().distanceTo(targetPoint);
        double egoSpeed = egoVehicle.getSpeed();
        if (egoSpeed > 0) {
            ttc = distance / egoSpeed;
        }
        getLog().debugSimTime(this, "TTC: {}", ttc);
        return ttc;
    }

    private void checkEmergencyBrake(CartesianPoint otherPosition) {
        if (!emergencyBrakeTrigger) {
            double ttc = calculateTTC(getOs().getVehicleData(), otherPosition);
            if (ttc <= emergencyBrakeMinTTC) {
                // should apply emergency brake
                emergencyBrakeTrigger = true;
                getLog().infoSimTime(this, "Should apply emergency break now. TTC value is {}", ttc);
                List<? extends Application> applications = getOs().getApplications();
                for (Application application : applications) {
                    String appName = application.getClass().getSimpleName();
                    if (appName.equals("EmergencyManeuver")) {
                        getLog().infoSimTime(this, "Found EmergencyManeuver, scheduling event");
                        EmergencyBrakeTrigger trigger = new EmergencyBrakeTrigger(ttc, otherPosition);
                        this.getOs().getEventManager()
                                .newEvent(getOs().getSimulationTime() + 1, application)
                                .withResource(trigger)
                                .schedule();
                    }
                }
            }
        }
    }
}
