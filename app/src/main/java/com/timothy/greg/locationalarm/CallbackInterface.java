package com.timothy.greg.locationalarm;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by timothy on 6/29/15.
 */
public interface CallbackInterface {
    void setCenter(LatLng latLng);
    void passLocation(Location location);
    void checkGPSEnabled();
}
