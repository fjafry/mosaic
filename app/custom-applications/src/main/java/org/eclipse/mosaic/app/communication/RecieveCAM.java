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

 package org.eclipse.mosaic.app.communication;

 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
 import org.eclipse.mosaic.fed.application.app.AbstractApplication;
 import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
 import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
 import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
 import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
 import org.eclipse.mosaic.lib.enums.AdHocChannel;
 import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
 import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
 import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
 import org.eclipse.mosaic.lib.util.scheduling.Event;
 
 import java.io.IOException;
 import javax.annotation.Nonnull;
 import javax.annotation.Nullable;
 
 /**
  * This is a simple application that reads received V2X messages and logs either the user tagged value if the received message was a CAM
  * or the message type and source name if it wasn't a CAM.
  */
 public class RecieveCAM extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {
 
     /**
      * We should enable ad hoc module here to be able to receive messages that were sent per ad hoc
      */
     @Override
     public void onStartup() {
         getLog().infoSimTime(this, "Set up");
         getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                 .addRadio().channel(AdHocChannel.CCH).power(50).create()
         );
     }
 
     @Override
     public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
         V2xMessage msg = receivedV2xMessage.getMessage();
 
         if (msg instanceof Cam) {
             
                 getLog().infoSimTime(this, "CAM message arrived, from vehicle: {}, at position: {}. generated at time: {}",
                         ((Cam) msg).getUnitID(),
                            ((Cam) msg).getPosition(),
                            ((Cam) msg).getGenerationTime()
                 );
             
 
         } else {
             getLog().infoSimTime(this, "Arrived message was not a CAM, but a {} msg from {}", msg.getSimpleClassName(), msg.getRouting().getSource().getSourceName());
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
     }
 
     @Override
     public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
 
     }
 }
 