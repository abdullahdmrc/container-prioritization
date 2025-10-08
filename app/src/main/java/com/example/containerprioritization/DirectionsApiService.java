package com.example.containerprioritization;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
public interface DirectionsApiService {

    @GET("directions/json")
    Call<DirectionsResponse> getDirections(
            @Query("origin") String origin,
            @Query("destination") String destination,
            @Query("waypoints") String waypoints,
            @Query("key") String apiKey
    );

}
