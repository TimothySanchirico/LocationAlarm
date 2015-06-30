package com.example.timothy.locationalarm;


import android.app.Activity;
import android.app.AlarmManager;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, CallbackInterface {

    private static final String TAG = "MainActivity"; // Tag for Logs
    private static long INTERVAL = 5000; // Longest time in between updates (5 seconds)
    private static final long FASTEST_INTERVAL = 1000; // Shortest time in between updates (1 second)

    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;
    Button submitAdr;
    EditText addr;
    Location mCurrentLocation;
    String mLastUpdateTime;
    Location mDestination;
    Double latitude, longitude;
    int thresholdDistance;
    TextView distance;
    boolean alarmRan = false;
    private Button setDest;
    boolean firstLocationUpdate = true;
    GoogleMap myMap;
    private final int SETTINGS_RESULT = 25;
    SharedPreferences sharedPrefs;
    BackgroundService service;
    private boolean bound = false;
    HashMap<String, JSONObject> jsonObjects = new HashMap<String, JSONObject>();

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service1) {
            Log.i(TAG, "service connected ..........");
            // cast the IBinder and get MyService instance
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service1;
            service = binder.getService();
            bound = true;
            service.setCallBackInterface(MainActivity.this); // register
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.map_activity);

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        mDestination = null;

        thresholdDistance = 1000; //in meters

        distance = (TextView) findViewById(R.id.mapText);
        addr = (EditText) findViewById(R.id.address);
        submitAdr = (Button) findViewById(R.id.submitAdd);
        submitAdr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(view.getId() == R.id.submitAdd) {
                    if(addr.getText() != null) {
                        getLocationFromAddress(addr.getText().toString());


                    }
                }
            }
        });



    }

    @Override
    public void onMapReady(GoogleMap map) {
        LatLng sydney = new LatLng(-33.867, 151.206);

        map.setMyLocationEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 13));

        map.addMarker(new MarkerOptions()
                .title("Sydney")
                .snippet("The most populous city in Australia.")
                .position(sydney));
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(final LatLng latLng) {

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Set Destination")
                        .setMessage("Set this location as destination?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                setUpDestination(latLng);


                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();
            }
        });
        myMap = map;
    }

    public void setUpDestination(LatLng latLng){
        mDestination = new Location("");
        mDestination.setLatitude(latLng.latitude);
        mDestination.setLongitude(latLng.longitude);
        myMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Destination"));
    }

    public void getLocationFromAddress(String strAddress) {

        Geocoder coder = new Geocoder(this);
        List<Address> address;
       mDestination = null;

        try {
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
               return;
            }
            Address location = address.get(0);
            location.getLatitude();
            location.getLongitude();

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
           setUpDestination(latLng);


        }
        catch (IOException ex){
            Log.i("IO ERR", "in strng to ltlng");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SETTINGS_RESULT) {
            thresholdDistance = Integer.parseInt(sharedPrefs.getString("distancePreference", "1000"));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent i = new Intent(getApplicationContext(), UserSettings.class);
            startActivityForResult(i, SETTINGS_RESULT);
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    public void onStart() {
        super.onStart();

        Intent intent = new Intent(this, BackgroundService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "onStart() executed.........");
    }

    @Override
    public void onStop() {
        super.onStop();
    }






    @Override
    protected void onPause() {
        super.onPause();

    }





    public void checkGPSEnabled() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public boolean checkDistance() {
        if (mDestination != null) {
            if (mCurrentLocation.distanceTo(mDestination) < thresholdDistance) {
                return true;
            }
        }
        return false;

    }

    public void runAlarm() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 5);

        //Create a new PendingIntent and add it to the AlarmManager
        Intent intent = new Intent(this, AlarmReciever.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                12345, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am =
                (AlarmManager) getSystemService(Activity.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                pendingIntent);
    }

    public void setDistanceText() {
        if (mDestination == null) {
            distance.setText("Please select a destination");
        } else {
            distance.setText(Float.toString(mCurrentLocation.distanceTo(mDestination)) + "m from destination");
        }

    }

    public void setCenter(LatLng latLng){
        myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13));
    }

    public void passLocation(Location location){
        Log.i("MAIN ACT: ","new location received");
        mCurrentLocation = location;
        String lat = String.valueOf(mCurrentLocation.getLatitude());
        String lng = String.valueOf(mCurrentLocation.getLongitude());
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        if (checkDistance() && !alarmRan) {
            alarmRan = true;
            runAlarm();

        }

        setDistanceText();

    }
}

