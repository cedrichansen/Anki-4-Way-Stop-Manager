package de.adesso.anki;

import de.adesso.anki.messages.LightsPatternMessage;
import de.adesso.anki.messages.LocalizationPositionUpdateMessage;
import de.adesso.anki.messages.LocalizationTransitionUpdateMessage;
import de.adesso.anki.messages.SetSpeedMessage;
import de.adesso.anki.roadmap.Roadmap;
import edu.oswego.cs.CPSLab.anki.AnkiConnectionTest;
import edu.oswego.cs.CPSLab.anki.FourWayStop.VehicleInfo;
import sun.awt.windows.ThemeReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;

public class RoadmapScanner {

  private Vehicle vehicle;
  private Roadmap roadmap;
  private LocalizationPositionUpdateMessage lastPosition;

  //Here are the variables we added
  public static int lastPos = 0;
  public static int secondLastPos = 0;
  public static int thirdLastPos = 0;
  private  ArrayList<VehicleInfo> otherVehicles = new ArrayList<>();
  private ServerSocket master;
  private final int PORT = 9000;
  private Socket slave;
  VehicleInfo info = new VehicleInfo();
  private static boolean atIntersection;
  private static boolean isMaster;
  private String vehicleWhoIsUpNext;

  private LightsPatternMessage.LightConfig masterLights = new LightsPatternMessage.LightConfig(LightsPatternMessage.LightChannel.TAIL,
          LightsPatternMessage.LightEffect.STEADY, 0, 0, 0);
  private LightsPatternMessage masterLightPattern = new LightsPatternMessage();


  private LightsPatternMessage.LightConfig slaveLights = new LightsPatternMessage.LightConfig(LightsPatternMessage.LightChannel.ENGINE_GREEN,
          LightsPatternMessage.LightEffect.FLASH, 0, 0, 0);

  private LightsPatternMessage slaveLightPattern = new LightsPatternMessage();

  private LightsPatternMessage.LightConfig regularLights = new LightsPatternMessage.LightConfig(LightsPatternMessage.LightChannel.TAIL,
          LightsPatternMessage.LightEffect.FADE, 0, 0, 0);

  private LightsPatternMessage regularLightsPattern = new LightsPatternMessage();




  public RoadmapScanner(Vehicle vehicle) {
    this.vehicle = vehicle;
    this.roadmap = new Roadmap();
  }
  
  public void startScanning() {
    vehicle.addMessageListener(
        LocalizationPositionUpdateMessage.class,
        (message) -> handlePositionUpdate(message)
    );
    
    vehicle.addMessageListener(
        LocalizationTransitionUpdateMessage.class,
        (message) -> handleTransitionUpdate(message)
    );
    
    vehicle.sendMessage(new SetSpeedMessage(500, 12500));


    initializeLightConfig();


  }

  private void initializeLightConfig() {
    masterLightPattern.add(masterLights);
    slaveLightPattern.add(slaveLights);
    regularLightsPattern.add(regularLights);
  }

  public void stopScanning() {
    //vehicle.sendMessage(new SetSpeedMessage(0, 12500));
//    System.out.println("Done scanning the Map");
  }
  
  public boolean isComplete() {
    return roadmap.isComplete();
  }
  
  public Roadmap getRoadmap() {
    return roadmap;
  }
  
  public void reset(){
	  this.roadmap = new Roadmap();
	  this.lastPosition = null;
  }

  private void handlePositionUpdate(LocalizationPositionUpdateMessage message) {
    lastPosition = message;
  }

  protected void handleTransitionUpdate(LocalizationTransitionUpdateMessage message) {
    if (lastPosition != null) {
      roadmap.add(
          lastPosition.getRoadPieceId(),
          lastPosition.getLocationId(),
          lastPosition.isParsedReverse()
      );

      //System.out.println("vehicles last roadpieceID: " + lastPosition.getRoadPieceId());

      switchPositions();

      if (atInteresection(lastPos, secondLastPos, thirdLastPos)) {

        vehicle.sendMessage(new SetSpeedMessage(0, -15000));
        try {
          info.timestamp = Instant.now();
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          System.out.println("Thread interrupted :o");
        }

        if (vehicleWhoIsUpNext == null) {
          vehicleWhoIsUpNext = vehicle.getAdvertisement().toString();
          System.out.println(vehicleWhoIsUpNext);
        }

        if (isMaster(vehicleWhoIsUpNext)) {
          System.out.println("I am master!");

          try {
            try {
              Thread.sleep(100);
              vehicle.sendMessage(new SetSpeedMessage(600, 300));
              Thread.sleep(100);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            System.out.println("passing on master to next car");
            // TODO: tell everyone we are no longer master and assign timestamp master
            //call function right here
            isMaster = false;
            master.close();

          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          System.out.println("I am the slave :( ");
          try {
            //TODO: Send info and wait for a signal to be master
            //TODO : blink the lights to say that they are salve
            //TODO: when we receive message to be master, break out of this and then we will now be master
            //TODO: reassign the vehicleWhoIsUpNext field with whoever goes next so we do not try to reconnect
            //TODO: wait 2 seconds once we receive that we are master signal and at this point listen to other broadcasts
            //TODO: maintain the list of cars to go next


            slave = new Socket("localhost", PORT);
            PrintWriter out = new PrintWriter (slave.getOutputStream(), true);
            out.println(info.timestamp);


          } catch (IOException e) {
            //port already taken..., try another port, ORRRR master changed
            //This method will be retriggered
            System.out.println("Master was passed, reconnecting");
          }

        }

      }


      if (roadmap.isComplete()) {
        this.stopScanning();
      }
    }
  }



  public boolean isMaster(String vehicleName){



    if (vehicleName.equals(vehicle.getAdvertisement().toString())) {
      try {
        master = new ServerSocket(9000);
        isMaster = true;
        System.out.println("Yay we are master");
        return true;
      } catch (IOException e) {
        System.out.println("Someone else is already master");
        isMaster = false;
        return false;
      }
    } else {
      return false;
    }


  }







  //a is the most recent peice, and c is the least recent
  protected boolean atInteresection(int a, int b, int c){
    int [] ids = {a, b, c};

    int [][] cases = {
            {34,33,23},
            {23,48,17},
            {20,18,18},
            {18,18,20}
    };

    for(int [] cas : cases){
      boolean same = (cas[0] == ids[0])
              && (cas[1] == ids[1])
              && (cas[2] == ids[2]);
      if(same) return true;
    }
    return false;
  }


  public void switchPositions(){

    int last = lastPosition.getRoadPieceId();

    thirdLastPos = secondLastPos;
    secondLastPos=lastPos;
    lastPos = last;
  }

}
