package org.dpppt.backend.sdk.ws.util;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.ArrayList;

public interface ServerBReportService {
    @Headers("Accept: application/json")
    @POST("v1/testServer")
    Call<Void> testServerB(@Body Integer a);

    @POST("v1/aggregateHotspots")
    Call<Void> aggregateHotspots(@Body ArrayList<Integer> randomAggregate);

    @GET("v1/")
    Call <Void> getBackend();
}
