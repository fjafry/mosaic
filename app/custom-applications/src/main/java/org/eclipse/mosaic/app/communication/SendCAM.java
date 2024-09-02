package org.eclipse.mosaic.app.communication;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import java.io.IOException;
import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application sends a CAM (Cooperative Awareness Message) with an
 * additional information (user tagged value)
 * by using the {@link CamBuilder#userTaggedValue(byte[])}) method.
 * In this way an additional byte field can be sent via CAM, nevertheless this
 * is often connected with some serious work.
 * You may also want to safely serialize / deserialize objects.
 * <p>
 * The CAMs will be sent by an ad hoc module so that only vehicles with an
 * enabled ad hoc module can receive it.
 **/
public class SendCAM extends ConfigurableApplication<CamConfig, VehicleOperatingSystem>
        implements VehicleApplication, CommunicationApplication {

    public SendCAM() {
        super(CamConfig.class, "CamConfig");
    }

    private long camInterval = 100 * TIME.MILLI_SECOND;
    private long sendLastCamAt = 0;

    /**
     * Setting up the communication module and scheduling next event for the next
     * second.
     */
    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .camMinimalPayloadLength(200L)
                .addRadio().channel(AdHocChannel.CCH).power(50).create());
        getLog().infoSimTime(this, "Set up");
        // sendCam(); Don't do this here! Sending CAMs only makes
        // sense when we have access to vehicle info of sender, which is not ready at
        // the set up stage.

        CamConfig config = this.getConfiguration();

        getOs().getEventManager().newEvent(config.sendFirstCamAt * TIME.SECOND, this)
                .withResource("FirstCam")
                .schedule();
        sendLastCamAt = config.sendLastCamAt * TIME.SECOND;

        if (config.frequency > 0) {
            camInterval = (long) (((double) 1 / config.frequency) * TIME.SECOND);
        }
        getLog().infoSimTime(this, "CAM will be sent every {} ns", camInterval);
    }

    /**
     * Sending CAM and scheduling next events every second.
     */
    @Override
    public void processEvent(Event event) {
        Object resource = event.getResource();
        if (resource instanceof String) {
            String resourceString = (String) resource;
            if (resourceString.equals("FirstCam")) {
                sendCam();
                getOs().getEventManager().newEvent(getOs().getSimulationTime() + camInterval, this)
                        .withResource("NextCam")
                        .schedule();
            } else if (resourceString.equals("NextCam")) {
                sendCam();
                if (getOs().getSimulationTime() + camInterval < sendLastCamAt) {
                    getOs().getEventManager().newEvent(getOs().getSimulationTime() + camInterval, this)
                            .withResource("NextCam")
                            .schedule();
                }
            }
        }
    }

    private void sendCam() {
        int msgId = getOs().getAdHocModule().sendCam();
        getLog().info("Sending CAM with msgId: {} at simulation time: {}", msgId, getOs().getSimulationTime());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
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

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

}
