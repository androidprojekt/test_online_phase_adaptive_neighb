package com.example.test_online_phase;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_PHONE_STATE;

public class MainActivity extends AppCompatActivity {

    Switch mySwitch;
    TextView switchTv;
    int switchKNN =0; //0 for adaptive 1 for classic KNN
    String startTime;
    String endTime;
    TextView timeTv;

    //-------------creating variables and objects needed to BLE and WIFI scan-----------------------
    private BluetoothManager mBluetoothManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothAdapter mBlueToothAdapter;
    private WifiManager wifiManager;
    private ArrayList<Transmitter> beaconList, wifiList;
    private ArrayList<Point> referencePointList;
    private WifiInfo wifiInfo;
    private List<ScanFilter> filters;
    private ScanSettings scanSettings;
    private Handler mHandler = new Handler();
    private Boolean startScanFlag = false;
    private Boolean startScanBeaconFlag=false;// flag specifying to start collecting measurements (from beacon) after clicking the button
    private Boolean startScanWifiFlag=false; // flag specifying to start collecting measurements (from ap) after clicking the button
    //----------------------------------------------------------------------------------------------

    //----------------------------------------layout------------------------------------------------
    private Button startLocalization;
    RadioGroup radiogroup;
    RadioButton radioButton;
    TextView estimateXTv, estimateYTv, errorTv, neighboursTv, nrOfNeighbours;
    EditText numberOfSamplesEditText, realX, realY;
    Context context;
    //----------------------------------------------------------------------------------------------

    //-----------------------------configuration variables------------------------------------------
    int numberOfSamples = 5; // number of needed samples to receive in online phase
    int numberOfBeacons = 2; // beacons in system
    int numberOfWifi = 1;    // number of AP's
    int finishedBeaconsIterator=0; //variable that determines whether the measurements have been collected from beacons
    int finishedWifiIterator=0; //variable that determines whether the measurements have been collected from wifi
    int xPoints = 5; // number of X coordinates
    int yPoints = 4; // number of Y coordinates
    int kNeighbours = 3; // number of nearest neighbour
    double percentRangeOfEuclideanDist=0.2; //percentage of the Euclidean distance range
    //----------------------------------------------------------------------------------------------

    //--------------------------------------JSON objects--------------------------------------------
    JSONObject objectDatabase; // main file database (with all directions)
    JSONObject objectUpDatabase; // all measurments from UP directions
    JSONObject objectDownDatabase;
    JSONObject objectLeftDatabase;
    JSONObject objectRightDatabase;
    //----------------------------------------------------------------------------------------------

    private Calendar calendar;
    private SimpleDateFormat simpleDateFormat;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        calendar = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("hh:mm:ss");

        //--------------------------VIEWS---------------------------------
        context = getApplicationContext();
        startLocalization = findViewById(R.id.startLocalizationBtnId);
        estimateXTv = findViewById(R.id.estimateOfXId);
        estimateYTv = findViewById(R.id.estimateOfYId);
        errorTv = findViewById(R.id.errorTvId);
        realX = findViewById(R.id.realXcordinateId);
        realY= findViewById(R.id.realYcordinateId);
        numberOfSamplesEditText = findViewById(R.id.numberOfSamplesId);
        radiogroup = findViewById(R.id.radioGroupId);
        neighboursTv = findViewById(R.id.neigboursTvId);
        nrOfNeighbours = findViewById(R.id.numberOfNeighbours);
        mySwitch=findViewById(R.id.switchId);
        switchTv = findViewById(R.id.switchTvId);
        timeTv = findViewById(R.id.timeTvId);
        //-----------------------------------------------------------------

        mySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b==true)
                {
                    switchKNN=1;
                    Toast.makeText(getApplicationContext(), "classic", Toast.LENGTH_SHORT).show();
                    switchTv.setText("Classic KNN");
                }
                else
                {
                    switchKNN=0;
                    Toast.makeText(getApplicationContext(), "adaptive", Toast.LENGTH_SHORT).show();
                    switchTv.setText("Adaptive KNN");
                }
            }
        });

        //--------------------------------initializing BLE and WIFI---------------------------------
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBlueToothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanner = mBlueToothAdapter.getBluetoothLeScanner(); // new solution for scanning
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        beaconList = new ArrayList<>();
        wifiList = new ArrayList<>();
        referencePointList = new ArrayList<>();
        wifiInfo = wifiManager.getConnectionInfo(); //actual connected AP

        Transmitter transmitterWifi = new Transmitter(wifiInfo.getMacAddress(),
                wifiInfo.getRssi(), "Wifi", wifiInfo.getSSID()); //create a wifi (acces point) object currently connected

        wifiList.add(transmitterWifi);
        //------------------------------------------------------------------------------------------

        //-------------------------PERMISSIONS-------------------------------
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        READ_PHONE_STATE,
                        ACCESS_FINE_LOCATION},
                1);
        //--------------------------------------------------------------------

        //--------------------Settings and filters for scanning bluetooth devices-------------------
        String[] peripheralAddresses = new String[]{"E8:D4:18:0D:DB:37", "D6:2E:C2:40:FD:03", "EF:F7:2A:DC:14:03",
                "DD:BC:33:F9:EE:56","F7:8B:72:B7:42:C4", "C1:90:8E:4B:16:E5","C6:40:D6:9C:59:7E","DB:A8:FF:3E:95:79",
        "FC:02:5B:0D:05:60"};
        //Beacon F7:8B:72:B7:42:C4 probably is defect
        filters = null;
        if (peripheralAddresses != null) {
            filters = new ArrayList<>();
            for (String address : peripheralAddresses) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build();
                filters.add(filter);
            }
        }
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0)
                .build();
        //------------------------------------------------------------------------------------------

        //---------------------------------new functionality----------------------------------------
        // when the app start's, their receiving rssi from wifi and beacons
        // later we should add a functionality in onPause, onResume, onStop etc.
        // also we should make a version in real time without button
        wifiScanner.run();
        BLEstartScan.run();
        //------------------------------------------------------------------------------------------

        startLocalization.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view) {
                int nrOfSamplesTemp = Integer.valueOf(String.valueOf(numberOfSamplesEditText.getText()));
                if(nrOfSamplesTemp!=0)
                {
                    numberOfSamples = nrOfSamplesTemp;
                    Toast.makeText(getApplicationContext(), "number of samples: "+numberOfSamples, Toast.LENGTH_SHORT).show();
                }
                else
                {
                    numberOfSamples=5;
                    Toast.makeText(getApplicationContext(), "number of samples: "+ numberOfSamples, Toast.LENGTH_SHORT).show();
                }
                startScanFlag = true;
                startScanBeaconFlag=true;
                startScanWifiFlag=true;
                calendar = Calendar.getInstance();
                startTime =simpleDateFormat.format(calendar.getTime());
                Log.d("TIME", "start time: " + simpleDateFormat.format(calendar.getTime()));
            }
        });

        try {
            // get JSONObject from JSON file
            objectDatabase = new JSONObject(loadJSONFromAsset());
            objectUpDatabase = objectDatabase.getJSONObject("UP");
            objectDownDatabase = objectDatabase.getJSONObject("DOWN");
            objectLeftDatabase = objectDatabase.getJSONObject("LEFT");
            objectRightDatabase = objectDatabase.getJSONObject("RIGHT");

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String loadJSONFromAsset() {
        //reading the main database file
        String json = null;
        try {
            InputStream is = getAssets().open("zuzia_pokoj.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    //-----------------------------------------Threads-----------------------------new version------
    private Runnable BLEstopScan = new Runnable() {

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            mBluetoothLeScanner.stopScan(scanCallback);
            mHandler.postDelayed(BLEstartScan, 1);
        }
    };

    private Runnable BLEstartScan = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            mBluetoothLeScanner.startScan(filters, scanSettings, scanCallback);
            mHandler.postDelayed(BLEstopScan, 6000);
        }
    };
    //----------------------------------------------------------------------------------------------

    private Runnable wifiScanner = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            if(startScanWifiFlag) {
                if (wifiList.get(0).isSavingSamples()) {    //the condition that all samples have been collected
                    if (wifiList.get(0).getSamplesIterator() == numberOfSamples) {
                        finishedWifiIterator++;
                        wifiList.get(0).setSavingSamples(false); //from this point on, we are not to take samples for the next click
                        double average = averageOfList(wifiList.get(0).getSamplesTab());
                        wifiList.get(0).setAverage(average);
                        Log.d("RSSI TAB", "Wifi: " + wifiList.get(0).getName() +
                                " tab of RSSI: " + wifiList.get(0).getSamplesTab());
                        Log.d("RSSI AVERAGE","average of "+wifiList.get(0).getName()+": "+ wifiList.get(0).getAverage());
                        Toast.makeText(getApplicationContext(), "receiving of wifi samples finished", Toast.LENGTH_SHORT).show();
                    } else {
                        checkWifi();
                    }
                }
            }
            mHandler.postDelayed(wifiScanner, 500); //nie wiadomo co z czasem, jaka wartosc przyjac?
        }
    };

    private void checkWifi() {
        wifiInfo = wifiManager.getConnectionInfo();
        if (wifiList.get(0).getSamplesIterator() != numberOfSamples) {
            wifiList.get(0).addToTheSamplesTab(wifiInfo.getRssi());
            wifiList.get(0).setSamplesIterator();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            final BluetoothDevice device = result.getDevice();
            Log.d("onScanResult start TIME", "scanCallback, device: "+ device);
            final int rssi = result.getRssi();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(startScanBeaconFlag) {
                        boolean newBeacon = true;
                        if (beaconList.size() != 0) { //if there is any beacon on the list
                            for (Transmitter transmitter : beaconList) {
                                if (transmitter.getMacAdress().contains(result.getDevice().getAddress())) {
                                    //If beacon from scan result exist on the beacon list
                                    newBeacon = false;
                                    if (transmitter.isSavingSamples()) {
                                        //the condition that all samples have been collected
                                        if (transmitter.getSamplesIterator() < numberOfSamples) {
                                            transmitter.addToTheSamplesTab(rssi);
                                            transmitter.setSamplesIterator();

                                        } if(transmitter.getSamplesIterator() == numberOfSamples) {
                                            Log.d("RSSI TAB","samples of "+transmitter.getMacAdress()+": "+ transmitter.getSamplesTab());
                                            double average = averageOfList(transmitter.getSamplesTab());
                                            transmitter.setAverage(average);
                                            Log.d("RSSI AVERAGE","average of "+transmitter.getMacAdress()+": "+ transmitter.getAverage());
                                            Toast.makeText(getApplicationContext(), "receiving of beacon "
                                                    +transmitter.getMacAdress()+  "samples finished", Toast.LENGTH_SHORT).show();
                                            transmitter.setSavingSamples(false);
                                            finishedBeaconsIterator++;
                                        }
                                    }
                                }
                            }
                        } if (newBeacon == true) {
                            Transmitter transmitter = new Transmitter(device.getAddress(), rssi, "Beacon");
                            beaconList.add(transmitter);
                            transmitter.addToTheSamplesTab(rssi);
                            transmitter.setSamplesIterator();
                        }
                    }
                    if (finishedBeaconsIterator==numberOfBeacons) {
                        calendar = Calendar.getInstance();
                        Log.d("TIME", "End time: " + simpleDateFormat.format(calendar.getTime()));
                        endTime =simpleDateFormat.format(calendar.getTime());
                        startScanBeaconFlag=false;
                        if (finishedWifiIterator == numberOfWifi) {
                            //-----------------------------------LOG's------------------------------
                            //sprawdzic w labie czy poprawnie beacony zebraly probki
                            //Log.d("CHECK", "values: " + wifiList.get(0).getSamplesTab());
                            //Log.d("CHECK", "values: " + beaconList.get(0).getSamplesTab());
                            //Log.d("CHECK", "values: " + beaconList.get(1).getSamplesTab());
                            //Log.d("CHECK", "values: " + beaconList.get(2).getSamplesTab()); //warning! sometimes may be out of list
                            //----------------------------------------------------------------------

                            startScanWifiFlag = false;
                            beaconList.sort(new beaconSorter()); // list of sorted euclidean distances with x,y cordinates

                            for (int i = beaconList.size(); i > numberOfBeacons; i--)
                            //removing beacons from the list above the set value
                            {
                                beaconList.remove(i - 1);
                            }

                            //---------------------------------LOG's--------------------------------
                            Log.d("SORT CHECK", "values of Wifi: " + wifiList.get(0).getSamplesTab());
                            Log.d("SORT AVERAGE", "MAC addr:" + wifiList.get(0).getName() + " average: " + wifiList.get(0).getAverage());
                            for (int i = 0; i < beaconList.size(); i++) {
                                Log.d("SORT CHECK", "MAC addr: " + beaconList.get(i).getMacAdress() + " samples: " + beaconList.get(i).getSamplesTab());
                                Log.d("SORT AVERAGE", "MAC addr:" + beaconList.get(i).getMacAdress() + " average: " + beaconList.get(i).getAverage());
                            }
                            //--------------------temporary solution--------------------------------
                            //beaconList.clear();
                            finishedBeaconsIterator = 0;
                            finishedWifiIterator = 0;
                            //wifiList.get(0).clearSamplesIterator();
                            //wifiList.get(0).clearTheSamplesTab();
                            //wifiList.get(0).setSavingSamples(true);
                            //----------------------------------------------------------------------

                            try {
                                //Toast.makeText(getApplicationContext(), "succes ", Toast.LENGTH_SHORT).show();
                                estimatePositions();
                            } catch (JSONException e) {
                                //Toast.makeText(getApplicationContext(), "exc", Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
    };

    public double averageOfList(ArrayList<Integer> list) {
        double average = 0.0;
        int sum = 0;
        for (int element : list) {
            sum += element;
        }
        average = (double) sum / (list.size());
        return average;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void estimatePositions() throws JSONException {
        ArrayList<Double> tempTab = new ArrayList<>(); // (x_a - x_b)^2
        double tempCalculation = 0.0;

        for (int x = 0; x < xPoints; x++) {
            for (int y = 0; y < yPoints; y++) {
                String str = "" + x + "," + y;
                    Log.d("Cordinate", "coordinate from database in loop: " + str);

                JSONObject tempPoint = objectUpDatabase.getJSONObject(str);
                Log.d("testDatabase ","test: " + tempPoint.getString("WIFI"));
                tempTab.clear();
                String wifiRssiTemp = tempPoint.getString("WIFI");
                double wifiRssi = Double.parseDouble(wifiRssiTemp);
                Log.d("RSSI wifi", "WIFI rssi value: " + wifiRssi);
                tempCalculation = Math.pow((wifiList.get(0).getAverage() - wifiRssi), 2);
                //Log.d("CALC","wsp: " +str + tempPoint.getString("WIFI"));
                tempTab.add(tempCalculation);

                for (Transmitter beacon : beaconList) {
                    String databaseBeaconRssiTemp = tempPoint.getString(beacon.getMacAdress()); // from database
                    double databaseBeaconRssi = Double.parseDouble(databaseBeaconRssiTemp); //from database
                    double actualBeaconRssi = beacon.getAverage(); //actual
                    Log.d("coordinate test","actual point from beacon: "+beacon.getMacAdress()+": "+str+ " " + actualBeaconRssi);
                    Log.d("coordinate test", "database point from beacon: " +beacon.getMacAdress()+": "+ str+" " + databaseBeaconRssi);
                    tempCalculation = Math.pow((databaseBeaconRssi - actualBeaconRssi), 2);
                    tempTab.add(tempCalculation);
                }
                double sum = 0.0;
                for (Double value : tempTab) {
                    sum += value;
                }
                double euclideanDistance = Math.sqrt(sum);
                Point pt = new Point(x, y, euclideanDistance);
                referencePointList.add(pt);
            }
        }
        referencePointList.sort(new euclideanSorter()); // list of sorted euclidean distances with x,y cordinates
        double maxEuclideanDistance = referencePointList.get(0).getEuclideanDistance()*(1+percentRangeOfEuclideanDist);
        // testing sorting by euclidean distance
        for (int i = 0; i < referencePointList.size(); i++) {
            Log.d("SORTEDTAB", "element: " + i + "  x: " +referencePointList.get(i).getX()+"  y: "
                    +referencePointList.get(i).getY()+"eucl distance: "
                    + referencePointList.get(i).getEuclideanDistance());
        }

        double x = 0.0;
        double y = 0.0;
        double sumOfWeights = 0.0;
        double estimateX = 0.0;
        double estimateY = 0.0;



        if(switchKNN==0)
        {
            //------------------------------------adaptive method of KNN--------------------------------
            int numberOfNeighbours = 0;
            for (Point pt : referencePointList) {
                if (pt.getEuclideanDistance() <= maxEuclideanDistance) {
                    x += pt.getX() * (1 / pt.getEuclideanDistance());
                    y += pt.getY() * (1 / pt.getEuclideanDistance());
                    sumOfWeights += 1 / pt.getEuclideanDistance();
                    numberOfNeighbours++;
                }
            }
            nrOfNeighbours.setText("Neighbours: " + String.valueOf(numberOfNeighbours));
            Log.d("Nearest Neigbours", "CHECK NEIGHBOURS: " + numberOfNeighbours);

            //------------------------------------------------------------------------------------------
        }
        else
        {
            //-------------------------------old version - standard KNN------------------------------------

        for (int i = 0; i < kNeighbours; i++) {

            Log.d("Nearest Neigbours", "x: " +referencePointList.get(i).getX()+"  y: "+ referencePointList.get(i).getY()
            +" euclidean distance: " + referencePointList.get(i).getEuclideanDistance());
            x += referencePointList.get(i).getX() * (1 / referencePointList.get(i).getEuclideanDistance());
            y += referencePointList.get(i).getY() * (1 / referencePointList.get(i).getEuclideanDistance());
            sumOfWeights += 1 / referencePointList.get(i).getEuclideanDistance();
        }

            String neigbour1 = "n1 x: " + referencePointList.get(0).getX() + " y: " + referencePointList.get(0).getY()+" dist: "+
                    referencePointList.get(0).getEuclideanDistance() + "\n";
            String neigbour2 = "n2 x: " + referencePointList.get(1).getX() + " y: " + referencePointList.get(1).getY()+" dist: "+
                    referencePointList.get(1).getEuclideanDistance()+ "\n";
            String neigbour3= "n3 x: " + referencePointList.get(2).getX() + " y: " + referencePointList.get(2).getY()+" dist: "+
                    referencePointList.get(2).getEuclideanDistance()+ "\n";
            String neigbour4= "n4 x: " + referencePointList.get(3).getX() + " y: " + referencePointList.get(3).getY()+" dist: "+
                    referencePointList.get(3).getEuclideanDistance()+ "\n";
            String neigbour5= "n5 x: " + referencePointList.get(4).getX() + " y: " + referencePointList.get(4).getY()+" dist: "+
                    referencePointList.get(4).getEuclideanDistance()+ "\n";

            neighboursTv.setText(neigbour1 + neigbour2 + neigbour3 + neigbour4 + neigbour5 );
     //---------------------------------------------------------------------------------------------


        }



        estimateX = x / sumOfWeights;
        estimateY = y / sumOfWeights;
        estimateX = Math.round(estimateX * 100.0) / 100.0; //rounded to 2 decimal places
        estimateY = Math.round(estimateY * 100.0) / 100.0; //rounded to 2 decimal places
        estimateXTv.setText("x = "+estimateX);
        estimateYTv.setText("y = "+estimateY);
        double realXvalue = Double.parseDouble(String.valueOf(realX.getText()));
        double realYvalue = Double.parseDouble(String.valueOf(realY.getText()));
        double estimateError = Math.sqrt(Math.pow(estimateX-realXvalue,2)+Math.pow(estimateY-realYvalue,2));
        errorTv.setText(" "+estimateError);
       // Log.d("Estimate of positions", "Estimate position x: " +estimateX+"  y: "+ estimateY);
        //clear data
        prepareToNewScan();
        timeTv.setText("Start: "+ startTime + " end: "+endTime);
    }
    public void checkRadioButton(View view) {
        int radioId = radiogroup.getCheckedRadioButtonId();
        radioButton= findViewById(radioId);
        kNeighbours= Integer.parseInt((String) radioButton.getText());
        Toast.makeText(getApplicationContext(), "value: " + kNeighbours, Toast.LENGTH_SHORT).show();
    }


    public static class euclideanSorter implements Comparator<Point> {
        @Override
        public int compare(Point p1, Point p2) {
            return Double.valueOf(p1.getEuclideanDistance()).compareTo(p2.getEuclideanDistance());
        }
    }

    public static class beaconSorter implements Comparator<Transmitter> {
        @Override
        public int compare(Transmitter t1, Transmitter t2) {
            if(t1.getSamplesIterator()<t2.getSamplesIterator()) {
                return 1;
            }
            else return -1;
            //return Integer.valueOf(t1.getSamplesIterator()).compareTo(t2.getSamplesIterator());
        }
    }


    public void prepareToNewScan()
    {
        wifiList.get(0).clearSamplesIterator();
        wifiList.get(0).setAverage(0.0);
        wifiList.get(0).setSavingSamples(true);
        wifiList.get(0).clearTheSamplesTab();
        for(int i=0;i<beaconList.size();i++)
        {
            beaconList.remove(i);
        }
        beaconList = new ArrayList<>();
        //beaconList.clear();
        referencePointList.clear();
    }

}