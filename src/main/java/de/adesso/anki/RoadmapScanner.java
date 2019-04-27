package de.adesso.anki;

import de.adesso.anki.messages.LocalizationPositionUpdateMessage;
import de.adesso.anki.messages.LocalizationTransitionUpdateMessage;
import de.adesso.anki.messages.SetSpeedMessage;
import de.adesso.anki.roadmap.Roadmap;
import edu.oswego.cs.CPSLab.anki.AnkiConnectionTest;
import sun.awt.windows.ThemeReader;

public class RoadmapScanner {

  private Vehicle vehicle;
  private Roadmap roadmap;

  public static int lastPos = 0;
  public static int secondLastPos = 0;
  public static int thirdLastPos = 0;
  
  private LocalizationPositionUpdateMessage lastPosition;
  
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
  }
  
  public void stopScanning() {
    //vehicle.sendMessage(new SetSpeedMessage(0, 12500));
    System.out.println("Done scanning the Map");
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

      System.out.println("vehicles last roadpieceID: " + lastPosition.getRoadPieceId());

      switchPositions();

      if (atInteresection(lastPos, secondLastPos, thirdLastPos)) {
        System.out.println("About to hit intersection!");

        vehicle.sendMessage(new SetSpeedMessage(0, -9000));
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          System.out.println("Thread interrupted :o");
        }

        vehicle.sendMessage(new SetSpeedMessage(600, 300));
      }


      if (roadmap.isComplete()) {
        this.stopScanning();
      }
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
