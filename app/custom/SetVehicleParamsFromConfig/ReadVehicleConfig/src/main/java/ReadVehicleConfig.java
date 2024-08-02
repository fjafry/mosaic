package custom.SetVehicleParamsFromConfig;

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
 import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
 import org.eclipse.mosaic.lib.util.scheduling.Event;
 import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.fed.application.app.api.Application;
import java.util.List;
 
 /**
  * This is a simple application to demonstrate a configurable application.
  * <p>
  * A configuration file(s) should be placed in "application" folder.
  * The filename can end with "_unitId" (e.g. ReadVehicleConfig.json) and will then only be used by the specified unit.
  * It allows configuring one application in different ways for different vehicles.
  * If the configuration filename doesn't include any unit id, it will be used for all unspecified units.
  */
 public class ReadVehicleConfig extends ConfigurableApplication<VehicleConfig, VehicleOperatingSystem> {
 
     /**
      * Configuration object.
      */
     private VehicleConfig config;
 
     public ReadVehicleConfig() {
         super(VehicleConfig.class, "ReadVehicleConfig");
     }
 
     @Override
     public void onStartup() {
         this.config = this.getConfiguration();
         getLog().infoSimTime(this, "Startup of ");
         List<? extends Application> applications = getOs().getApplications();
        for (Application application : applications) {
         this.getOs().getEventManager()
                 .newEvent(getOs().getSimulationTime() + 1, application)
                 .withResource(this.config)
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
         getLog().info("Wanted speed from config equals " + this.config.initialSetSpeed);
         getLog().info("Configs speedMode equals " + this.config.speedMode);
     }
 
 }
 