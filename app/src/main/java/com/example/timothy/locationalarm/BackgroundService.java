package com.example.timothy.locationalarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
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

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy on 6/29/15.
 */
public class BackgroundService extends Service implements
        com.google.android.gms.location.LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener

{


    private static long INTERVAL = 5000; // Longest time in between updates (5 seconds)
    private static final long FASTEST_INTERVAL = 1000; // Shortest time in between updates (1 second)

    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;

    Location mCurrentLocation;
    String mLastUpdateTime;
    Location mDestination;
    private final static String TAG = "SERVICE";
    int thresholdDistance;
    boolean firstLocationUpdate = true;

    SharedPreferences sharedPrefs;
    CallbackInterface callbackInterface;

    private final IBinder binder = new LocalBinder();

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate() called.............");
        checkGPSEnabled();
        if (!isGooglePlayServicesAvailable()) {
            stopSelf();
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        createLocationRequest();


        thresholdDistance = 1000; //in meters
        mGoogleApiClient.connect();



    }


    protected void createLocationRequest() {
        Log.i(TAG, "createLocationRequest() called.............");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(TAG, "onStart() called.............");
        super.onStart(intent, startId);


    }


    private boolean isGooglePlayServicesAvailable() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == status) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "onConnected() called.............");

        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
        Log.i(TAG, "startLocationUpdates() called.............");
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: " + connectionResult.toString());
    }

    @Override
    public void onLocationChanged(Location location) {
        if (firstLocationUpdate) {
            LatLng cameraHere = new LatLng(location.getLatitude(), location.getLongitude());
            if(callbackInterface != null){
                callbackInterface.setCenter(cameraHere);
            }

            firstLocationUpdate = false;
        }
        mCurrentLocation = location;
        if(callbackInterface != null){
            callbackInterface.passLocation(location);
        }

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }


    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }


    public void checkGPSEnabled() {
        callbackInterface.checkGPSEnabled();
    }
    

    public class LocalBinder extends Binder {
        BackgroundService getService() {
            // Return this instance of MyService so clients can call public methods
            return BackgroundService.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallBackInterface(CallbackInterface call) {
        Log.i(TAG, "setCallBackInterface() called........");
        callbackInterface = call;
    }
}
