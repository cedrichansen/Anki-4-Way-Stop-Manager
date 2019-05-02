package de.adesso.anki;

import de.adesso.anki.messages.LightsPatternMessage;
import de.adesso.anki.messages.LocalizationPositionUpdateMessage;
import de.adesso.anki.messages.LocalizationTransitionUpdateMessage;
import de.adesso.anki.messages.SetSpeedMessage;
import de.adesso.anki.roadmap.Roadmap;
import edu.oswego.cs.CPSLab.anki.FourWayStop.VehicleInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;

public class RoadmapScanner {

    private Vehicle vehicle;
    private Roadmap roadmap;
    private LocalizationPositionUpdateMessage lastPosition;

    //Here are the variables we added
    public static int lastPos = 0;
    public static int secondLastPos = 0;
    public static int thirdLastPos = 0;
    private ArrayList<VehicleInfo.IntersectionMessage> otherVehicles = new ArrayList<>();
    private ServerSocket master;
    private final int PORT = 9000;
    private Socket connectionToMaster;
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

    public void reset() {
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

            boolean crossedIntersection = false;
            vehicle.sendMessage(new SetSpeedMessage(0, 15000));
            try {
                info.timestamp = Instant.now();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.out.println("Thread interrupted :o");
            }


            if (atInteresection(lastPos, secondLastPos, thirdLastPos)) {

                while (!crossedIntersection) {


                    if (vehicleWhoIsUpNext == null) {
                        vehicleWhoIsUpNext = vehicle.getAdvertisement().toString();
                        System.out.println(vehicleWhoIsUpNext);
                    }

                    if (isMaster(vehicleWhoIsUpNext)) {
                        System.out.println("I am master!");

                        boolean listening = true;

                        while (listening) {
                            Socket slave;
                            try {
                                master.setSoTimeout(2500);
                                System.out.println("Waiting for any responses");
                                slave = master.accept();
                                System.out.println("Found a connectionToMaster!");

                                BufferedReader in = new BufferedReader(new InputStreamReader(slave.getInputStream()));
                                String fromSlave = in.readLine();
                                System.out.println(fromSlave);


                                String[] info = fromSlave.split("=-=-=-=-=");
                                VehicleInfo.IntersectionMessage otherCarAtStop = new VehicleInfo.IntersectionMessage(info[0], info[1].substring(0, info[1].indexOf("EndOfCar")));
                                otherVehicles.add(otherCarAtStop);
                                Collections.sort(otherVehicles);

                                PrintWriter out = new PrintWriter(slave.getOutputStream(), true);

                                System.out.println("Next master will be: " + otherVehicles.get(0).toString());
                                //tell the cars who is going next
                                out.println(otherVehicles.get(0).toString());
                                out.close();
                                in.close();
                            } catch (IOException e) {
                                listening = false;
                            }
                        }

                        //reset the arraylist
                        otherVehicles = new ArrayList<>();


                        try {
                            try {
                                Thread.sleep(100);
                                vehicle.sendMessage(new SetSpeedMessage(600, 300));
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("passing on master to next car");
                            isMaster = false;
                            master.close();
                            crossedIntersection = true;

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("I am the connectionToMaster :( ");
                        try {
                            //TODO: Send info and wait for a signal to be master
                            //TODO : blink the lights to say that they are salve
                            //TODO: when we receive message to be master, break out of this and then we will now be master
                            //TODO: reassign the vehicleWhoIsUpNext field with whoever goes next so we do not try to reconnect
                            //TODO: wait 2 seconds once we receive that we are master signal and at this point listen to other broadcasts

                            connectionToMaster = new Socket("localhost", PORT);
                            System.out.println("Connected to master");
                            PrintWriter out = new PrintWriter(connectionToMaster.getOutputStream(), true);

                            VehicleInfo.IntersectionMessage myInfo = new VehicleInfo.IntersectionMessage(vehicle.getAdvertisement().toString(), info.timestamp.toString());
                            System.out.println(myInfo);
                            out.println(myInfo);

                            BufferedReader in = new BufferedReader(new InputStreamReader(connectionToMaster.getInputStream()));
                            String fromMaster = in.readLine();
                            System.out.println("Car order (as decided by master): " + fromMaster);

                            String[] nextMasterStr = fromMaster.split("=-=-=-=-=");

                            String carModel = nextMasterStr[0];
                            String carTimeStamp = removeSuffix(nextMasterStr[1], "EndOfCar");

                            System.out.println("next car model: " + carModel);
                            System.out.println("next car Timestamp" + carTimeStamp);
                            //on the next line, we dont care about the timestamp, we only care that the
                            VehicleInfo.IntersectionMessage nextMaster = new VehicleInfo.IntersectionMessage(carModel, carTimeStamp);
                            vehicleWhoIsUpNext = nextMaster.model;
                            System.out.println("Next vehicle to connect is " + vehicleWhoIsUpNext);

                        } catch (IOException e) {
                            //port already taken..., try another port, ORRRR master changed
                            //This method will be retriggered
                            System.out.println("Master was passed, reconnecting");
                            System.out.println("Vehcile who is next is " + vehicleWhoIsUpNext);
                            System.out.println("Will I be going next? " + vehicleWhoIsUpNext.equals(vehicle.getAdvertisement().toString()));
                        }

                    }
                }

            }


            if (roadmap.isComplete()) {
                this.stopScanning();
            }
        }
    }


    public boolean isMaster(String vehicleName) {


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
    protected boolean atInteresection(int a, int b, int c) {
        int[] ids = {a, b, c};

        int[][] cases = {
                {34, 33, 23},
                {23, 48, 17},
                {20, 18, 18},
                {18, 18, 20}
        };

        for (int[] cas : cases) {
            boolean same = (cas[0] == ids[0])
                    && (cas[1] == ids[1])
                    && (cas[2] == ids[2]);
            if (same) return true;
        }
        return false;
    }


    public void switchPositions() {

        int last = lastPosition.getRoadPieceId();

        thirdLastPos = secondLastPos;
        secondLastPos = lastPos;
        lastPos = last;
    }

    public static String removeSuffix(final String s, final String suffix) {
        if (s != null && suffix != null && s.endsWith(suffix)) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
    }


}
