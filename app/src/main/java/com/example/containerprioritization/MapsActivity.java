package com.example.containerprioritization;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.containerprioritization.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.snackbar.Snackbar;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback ,GoogleMap.OnMapLongClickListener{
    private GoogleMap mMap;
    Retrofit retrofit;
    DirectionsApiService directionsApiService;
    private ActivityMapsBinding binding;
    //used to initiate another Activity, Intent, or permission request.
    ActivityResultLauncher<String> permissionLauncher;
    // used for receive the location data
    LocationManager locationManager;
    //used for capture location changes in real time, listened for with LocationManager.
    LocationListener locationListener;
    //used for travelling in the map without this we can not travelling in map easy
    SharedPreferences sharedPreferences;
    boolean info;
    private double latitude,longitude;
    private double methane,weight;
    private int fullnessRate;
    private float temprature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        registerLauncher();
        sharedPreferences= MapsActivity.this.getSharedPreferences("com.example.containerprioritization",MODE_PRIVATE);
        info=false;
        retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        directionsApiService = retrofit.create(DirectionsApiService.class);
        // Write a message to the database
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
       // listen the db values
        database.child("container1").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {

                    latitude = snapshot.child("latitude").getValue(Double.class);
                    longitude = snapshot.child("longitude").getValue(Double.class);
                    fullnessRate = snapshot.child("fullnessRate").getValue(Integer.class);
                    temprature = snapshot.child("temperature").getValue(Float.class);
                    methane=snapshot.child("methane").getValue(Double.class);
                    weight=snapshot.child("weight").getValue(Double.class);

                    float markerColor=0;

                    if (fullnessRate >= 75 || temprature >60 || methane >=800) {
                        markerColor = BitmapDescriptorFactory.HUE_RED;
                    } else if ( (fullnessRate>50 && fullnessRate<75) || (methane>300 && methane<800)) {
                        markerColor = BitmapDescriptorFactory.HUE_YELLOW;
                    } else if(fullnessRate<50 || methane<300 || temprature<60) {
                        markerColor = BitmapDescriptorFactory.HUE_GREEN;
                    }
                    LatLng containerLocation = new LatLng(latitude, longitude);
                    mMap.addMarker(new MarkerOptions().position(containerLocation)
                            .title("Container1 "+" Fullness rate: "+fullnessRate+" Temperature: "+temprature+" Methane rate: "+methane+" Weight: "+weight))
                            .setIcon(BitmapDescriptorFactory.defaultMarker(markerColor));

                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to read value.", error.toException());
            }
        });
    }
    // location manager request location updates, location listener triggered, on location changed
    //method we can move the camera
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(MapsActivity.this);
        locationManager=(LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener=new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {

                //check if the request update runs before
                info=sharedPreferences.getBoolean("info",false);
                if (!info){
                    LatLng userLocation=new LatLng(location.getLatitude(),location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation,10));

                    sharedPreferences.edit().putBoolean("info",true).apply();
                }
            }
        };
        // user location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            //optional for showing why permission needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                Snackbar.make(binding.getRoot(),"Permission needed for location",Snackbar.LENGTH_INDEFINITE).setAction("Give permission", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //request permission
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                }).show();
            }else{
                // request permission
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }else{
                // request accepted by user so we can access the location
                // in real apps , the min time and min distance can be changed
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);

                //last known location provides faster zooming in app , but not mandatory it is optional
                Location lastLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation!=null){
                    LatLng userLastLocation=new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLastLocation,10));
                }

                // blue circle at user location
                mMap.setMyLocationEnabled(true);
        }
    }
    public void registerLauncher(){
        permissionLauncher=registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean o) {
                if (o){
                    // permission granted
                    if (ContextCompat.checkSelfPermission(MapsActivity.this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);

                    Location lastLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (lastLocation!=null){
                            LatLng userLastLocation=new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLastLocation,10));
                        }
                }else{
                    // permission denied
                    Toast.makeText(MapsActivity.this,"Permission needed",Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void backToDash(View view){
        binding.todashboard.setOnClickListener(v -> {
            Intent intent = new Intent(MapsActivity.this, Dashboard.class);
            startActivity(intent);
        });
    }

    public void drawRoute(View view) {
        String origin = "LAT,LONG";
        String destination = "LAT,LONG";
        String waypoints = "optimize:true|38.368803,27.201837|38.371344,27.201862|38.372544,27.196044|38.36,27.2";
        String apiKey = "YOUR_API_KEY";

        Call<DirectionsResponse> call = directionsApiService.getDirections(origin, destination, waypoints, apiKey);

        call.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String encodedPoints = response.body().routes.get(0).overviewPolyline.points;

                    List<LatLng> decodedPath = decodePoly(encodedPoints);

                    mMap.addPolyline(new PolylineOptions().addAll(decodedPath).color(Color.BLUE).width(10));
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Log.e("DirectionsAPI", "Error: " + t.getMessage());
            }
        });
    }

    public List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng(
                    ((double) lat / 1E5),
                    ((double) lng / 1E5)
            );
            poly.add(p);
        }
        return poly;
    }




    // add a marker with long click
    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {

        mMap.clear();
        mMap.addMarker(new MarkerOptions().title("your marker").position(latLng));
    }
}