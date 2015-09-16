package com.timothy.greg.locationalarm;


import android.app.Activity;
import android.app.AlarmManager;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, CallbackInterface, OnItemClickListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

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
        setContentView(com.timothy.greg.locationalarm.R.layout.map_activity);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        checkGPSEnabled();
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(com.timothy.greg.locationalarm.R.id.map);
        mapFragment.getMapAsync(this);


        mDestination = null;

        thresholdDistance = 1000; //in meters

        distance = (TextView) findViewById(com.timothy.greg.locationalarm.R.id.mapText);
        addr = (EditText) findViewById(com.timothy.greg.locationalarm.R.id.autocomplete);
        submitAdr = (Button) findViewById(com.timothy.greg.locationalarm.R.id.submitAdd);
        submitAdr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(view.getId() == com.timothy.greg.locationalarm.R.id.submitAdd) {
                    if(addr.getText() != null) {
                        if (TextUtils.isEmpty(addr.getText().toString())){
                            //Check if entry is blank
                            return;
                        }
                        else{
                            getLocationFromAddress(addr.getText().toString());
                        }

                    }
                }
            }
        });
        //Some AutoComplete Text View Setup
        AutoCompleteTextView autoCompView = (AutoCompleteTextView) findViewById(com.timothy.greg.locationalarm.R.id.autocomplete);

        autoCompView.setAdapter(new GooglePlacesAutocompleteAdapter(this, com.timothy.greg.locationalarm.R.layout.list_item));
        autoCompView.setOnItemClickListener(this);


    }

    //AutoComplete BELOW
    public void onItemClick(AdapterView adapterView, View view, int position, long id) {
        String str = (String) adapterView.getItemAtPosition(position);
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    private static final String PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";
    private static final String TYPE_AUTOCOMPLETE = "/autocomplete";
    private static final String OUT_JSON = "/json";

    private static final String API_KEY = "AIzaSyCX6qvOP8CkQl1eUE7wkEmegaS-noXd0vw";

    public ArrayList<String> autocomplete (String input) {
        ArrayList<String> resultList = null;

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();

        try {
            StringBuilder sb = new StringBuilder(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON);
            sb.append("?key=" + API_KEY);
            sb.append("&types=address");
            sb.append("&input=" + URLEncoder.encode(input, "utf8"));
            sb.append("&radius=650000");

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Log.e(TAG, "Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            // Log.d(TAG, jsonResults.toString());

            // Create a JSON object hierarchy from the results
            JSONObject jsonObj = new JSONObject(jsonResults.toString());
            JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

            // Extract the Place descriptions from the results
            resultList = new ArrayList<String>(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                resultList.add(predsJsonArray.getJSONObject(i).getString("description"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Cannot process JSON results", e);
        }

        return resultList;
    }

    class GooglePlacesAutocompleteAdapter extends ArrayAdapter<String> implements Filterable {
        private ArrayList<String> resultList;

        public GooglePlacesAutocompleteAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public int getCount() {
            return resultList.size();
        }

        @Override
        public String getItem(int index) {
            return resultList.get(index);
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        resultList = autocomplete(constraint.toString());

                        // Assign the data to the FilterResults
                        filterResults.values = resultList;
                        filterResults.count = resultList.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
            return filter;
        }
    }
    //AutoComplete ABOVE

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: " + connectionResult.toString());
    }

    @Override
    public void onMapReady(GoogleMap map) {

        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LatLng startCenter;
        if(lastLocation == null){
            startCenter = new LatLng(0,0);
        }
        else {
            startCenter = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        }
        map.setMyLocationEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(startCenter, 13));


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
        getMenuInflater().inflate(com.timothy.greg.locationalarm.R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.timothy.greg.locationalarm.R.id.action_settings) {
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
            distance.setText("0m from destination");
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

        setDistanceText();

        if (checkDistance() && !alarmRan) {
            alarmRan = true;
            runAlarm();

            //Reset Destination
            mDestination = null;
            alarmRan = false;
        }



    }

    @Override
    public void onConnected(Bundle bundle) {

    }
}

