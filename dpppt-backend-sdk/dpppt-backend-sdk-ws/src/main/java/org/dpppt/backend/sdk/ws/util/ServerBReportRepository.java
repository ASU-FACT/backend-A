package org.dpppt.backend.sdk.ws.util;
import okhttp3.OkHttpClient;
import org.springframework.lang.NonNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.validation.constraints.Null;
import java.util.ArrayList;

public class ServerBReportRepository {
    private ServerBReportService reportService;
    public ServerBReportRepository(){
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl("http://192.168.1.10:8082")
				.addConverterFactory(GsonConverterFactory.create())
				.client(httpClient.build())
				.build();
		reportService = retrofit.create(ServerBReportService.class);
    }

    public void aggregateHotspots(ArrayList<Integer> aggregate, ResponseCallback<Void> responseCallback){
        reportService.aggregateHotspots(aggregate).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if(response.isSuccessful()){
                    responseCallback.onSuccess(null);
                }
                else{
                    onFailure(call, new Exception(response.toString()));
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                responseCallback.onError(throwable);
            }
        });
    }





//    private ServerBReportService reportService;
    public ServerBReportRepository(String serverBBaseUrl){
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverBBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build();

        ServerBReportService reportService =  retrofit.create(ServerBReportService.class);
    }
    public void testServerB(ResponseCallback<Void> responseCallback){
        System.out.println("In repository test");
        reportService.testServerB(1111).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    responseCallback.onSuccess(null);
                } else {
                    //todo
                    System.err.println("Call failed.");
                }
            }

            @Override
            public void onFailure(Call<Void> call,Throwable throwable) {
                responseCallback.onError(throwable);
            }
        });
    }
    public void testGetCall(@NonNull ResponseCallback<Void> responseCallback){
        System.out.println("In BackendReportRepository Test Backend");
        if(reportService.equals(null))
            System.out.println("Null reportService");
        else {
            reportService.testServerB(1111).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    if (response.isSuccessful()) {
                        responseCallback.onSuccess(null);
                    } else {
                        onFailure(call, new Exception());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                    responseCallback.onError(throwable);
                }
            });
        }
    }

}
