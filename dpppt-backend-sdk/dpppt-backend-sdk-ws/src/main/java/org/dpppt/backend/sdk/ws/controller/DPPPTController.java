/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.controller;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

import javax.validation.Valid;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.dpppt.backend.sdk.data.DPPPTDataService;
import org.dpppt.backend.sdk.model.BucketList;
import org.dpppt.backend.sdk.model.ExposedOverview;
import org.dpppt.backend.sdk.model.Exposee;
import org.dpppt.backend.sdk.model.ExposeeRequest;
import org.dpppt.backend.sdk.model.ExposeeRequestList;
import org.dpppt.backend.sdk.model.proto.Exposed;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.InvalidDateException;
import org.dpppt.backend.sdk.ws.util.ResponseCallback;
import org.dpppt.backend.sdk.ws.util.ServerBReportRepository;
import org.dpppt.backend.sdk.ws.util.ServerBReportService;
import org.dpppt.backend.sdk.ws.util.ValidationUtils;
import org.dpppt.backend.sdk.ws.util.ValidationUtils.BadBatchReleaseTimeException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import com.google.protobuf.ByteString;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
@Controller
@RequestMapping("/v1")
public class DPPPTController {

	private final DPPPTDataService dataService;
	private final String appSource;
	private final int exposedListCacheControl;
	private final ValidateRequest validateRequest;
	private final ValidationUtils validationUtils;
	private final long batchLength;
	private final long requestTime;
	private ArrayList<Integer> aggregate;
	private int NoOfVectorsReceived;
	private ServerBReportRepository serverBReportRepository;
	public DPPPTController(DPPPTDataService dataService, String appSource,
			int exposedListCacheControl, ValidateRequest validateRequest, ValidationUtils validationUtils, long batchLength,
			long requestTime) {
		this.dataService = dataService;
		this.appSource = appSource;
		this.exposedListCacheControl = exposedListCacheControl/1000/60;
		this.validateRequest = validateRequest;
		this.validationUtils = validationUtils;
		this.batchLength = batchLength;
		this.requestTime = requestTime;
	}

	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "")
	public @ResponseBody ResponseEntity<String> hello() {
		System.out.println("v1 Get Request received");
		ResponseCallback<Void> callback = new ResponseCallback<Void>() {
			@Override
			public void onSuccess(Void response) {
				System.out.println("Success");
			}

			@Override
			public void onError(Throwable throwable) {
				System.out.println("Error: "+throwable.getLocalizedMessage());
			}
		};
		ServerBReportRepository reportRepository = new ServerBReportRepository("http://192.168.1.10:8082/");
		reportRepository.testGetCall(new ResponseCallback<Void>() {
			@Override
			public void onSuccess(Void response) {
				callback.onSuccess(response);
			}

			@Override
			public void onError(Throwable throwable) {
				callback.onError(throwable);
			}
		});
		return ResponseEntity.ok().header("X-HELLO", "dp3t").body("Hello from DP3T WS");
	}

	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@PostMapping(value = "/exposed")
	public @ResponseBody ResponseEntity<String> addExposee(@Valid @RequestBody ExposeeRequest exposeeRequest,
			@RequestHeader(value = "User-Agent", required = true) String userAgent,
			@AuthenticationPrincipal Object principal) throws InvalidDateException {
		long now = System.currentTimeMillis();
		if (!this.validateRequest.isValid(principal)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (!validationUtils.isValidBase64Key(exposeeRequest.getKey())) {
			return new ResponseEntity<>("No valid base64 key", HttpStatus.BAD_REQUEST);
		}
		// TODO: should we give that information?
		Exposee exposee = new Exposee();
		exposee.setKey(exposeeRequest.getKey());
		long keyDate = this.validateRequest.getKeyDate(principal, exposeeRequest);
		boolean testing = true;
		exposee.setKeyDate(keyDate);
		if (!this.validateRequest.isFakeRequest(principal, exposeeRequest)) {
			System.out.println("Exposed hashes:"+exposeeRequest.getHashes());
//			dataService.upsertExposee(exposee, appSource);
			exposee.setHashes(exposeeRequest.getHashes());
/*
*/
			dataService.upsertExposeeHashes(exposee,appSource);
		}
		long after = System.currentTimeMillis();
		long duration = after - now;
		try {
			Thread.sleep(Math.max(this.requestTime - duration, 0));
		} catch (Exception ex) {

		}
		return ResponseEntity.ok().build();
	}

	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@PostMapping(value = "/exposedlist")
	public @ResponseBody ResponseEntity<String> addExposee(@Valid @RequestBody ExposeeRequestList exposeeRequests,
			@RequestHeader(value = "User-Agent", required = true) String userAgent,
			@AuthenticationPrincipal Object principal) throws InvalidDateException {
		long now = System.currentTimeMillis();
		if (!this.validateRequest.isValid(principal)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		List<Exposee> exposees = new ArrayList<>();
		for (var exposedKey : exposeeRequests.getExposedKeys()) {
			if (!validationUtils.isValidBase64Key(exposedKey.getKey())) {
				return new ResponseEntity<>("No valid base64 key", HttpStatus.BAD_REQUEST);
			}

			Exposee exposee = new Exposee();
			exposee.setKey(exposedKey.getKey());
			long keyDate = this.validateRequest.getKeyDate(principal, exposedKey);

			exposee.setKeyDate(keyDate);
			exposees.add(exposee);
		}

		if (!this.validateRequest.isFakeRequest(principal, exposeeRequests)) {
			dataService.upsertExposees(exposees, appSource);
		}

		long after = System.currentTimeMillis();
		long duration = after - now;
		try {
			Thread.sleep(Math.max(this.requestTime - duration, 0));
		} catch (Exception ex) {

		}
		return ResponseEntity.ok().build();
	}



	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "/exposedjson/{batchReleaseTime}", produces = "application/json")
	public @ResponseBody ResponseEntity<ExposedOverview> getExposedByDayDate(@PathVariable long batchReleaseTime,
			WebRequest request) throws BadBatchReleaseTimeException{
		if(!validationUtils.isValidBatchReleaseTime(batchReleaseTime)) {
			return ResponseEntity.notFound().build();
		}

		List<Exposee> exposeeList = dataService.getSortedExposedForBatchReleaseTime(batchReleaseTime, batchLength);
		ExposedOverview overview = new ExposedOverview(exposeeList);
		overview.setBatchReleaseTime(batchReleaseTime);
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(exposedListCacheControl)))
				.header("X-BATCH-RELEASE-TIME", Long.toString(batchReleaseTime)).body(overview);
	}

	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "/exposedHashes/{batchReleaseTime}", produces = "application/json")
	public @ResponseBody ResponseEntity<HashSet<String>> getExposedHashesByDayDate(@PathVariable long batchReleaseTime,
																			   WebRequest request) throws BadBatchReleaseTimeException{
		if(!validationUtils.isValidBatchReleaseTime(batchReleaseTime)) {
			return ResponseEntity.notFound().build();
		}
		HashSet<String> hashes = new HashSet<>();
		System.out.println(batchReleaseTime);
		hashes.addAll(dataService.getSortedExposedHashes());
//		hashes.addAll(dataService.getSortedExposedHashesForBatchReleaseTime(batchReleaseTime, batchLength));
		System.out.println("Collected hashes from database = "+hashes);
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(exposedListCacheControl)))
				.header("X-BATCH-RELEASE-TIME", Long.toString(batchReleaseTime)).body(hashes);
	}


	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "/testExposedHashes/{count}", produces = "application/json")
	public @ResponseBody ResponseEntity<HashSet<String>> getTestExposedHashes(@PathVariable int count, WebRequest request){
//		boolean testing = true;
//		if(testing){
//			count = 0;
//			Thread newThread = new Thread(() ->{
//				dataService.upsertTestHashes(generateXHashes(3000000,10));
//				System.out.println("Write to database completed.");
//			});
//			newThread.start();
//		}
		HashSet<String> hashes = new HashSet<>();
		hashes.addAll( dataService.getSortedExposedHashesForTest(count));
		System.out.println("Collected test hashes from database = "+hashes.size());
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(exposedListCacheControl))).body(hashes);
	}

	private static ArrayList<String> generateXHashes(int count, int hashBytes) {
		char[] hexDigits = {'0', '1', '2', '3', '4', '5',
				'6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
		Random random = new Random();
		ArrayList<String> randomHashes = new ArrayList<>();
		for (int j = 0; j < count; j++){
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < hashBytes*2; i++) {
				sb.append(hexDigits[random.nextInt(hexDigits.length)]);
			}
			randomHashes.add(sb.toString());
//			System.out.println(sb.toString());
		}
		System.out.println("Generated random hashes =" + randomHashes.size());
		return randomHashes;
	}


	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "/exposed/{batchReleaseTime}", produces = "application/x-protobuf")
	public @ResponseBody ResponseEntity<Exposed.ProtoExposedList> getExposedByBatch(@PathVariable long batchReleaseTime,
			WebRequest request) throws BadBatchReleaseTimeException {
		if(!validationUtils.isValidBatchReleaseTime(batchReleaseTime)) {
			return ResponseEntity.notFound().build();
		}

		List<Exposee> exposeeList = dataService.getSortedExposedForBatchReleaseTime(batchReleaseTime, batchLength);
		List<Exposed.ProtoExposee> exposees = new ArrayList<>();
		for (Exposee exposee : exposeeList) {
			Exposed.ProtoExposee protoExposee = Exposed.ProtoExposee.newBuilder()
					.setKey(ByteString.copyFrom(Base64.getDecoder().decode(exposee.getKey())))
					.setKeyDate(exposee.getKeyDate()).build();
			exposees.add(protoExposee);
		}
		Exposed.ProtoExposedList protoExposee = Exposed.ProtoExposedList.newBuilder().addAllExposed(exposees)
				.setBatchReleaseTime(batchReleaseTime).build();

		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(exposedListCacheControl)))
				.header("X-BATCH-RELEASE-TIME", Long.toString(batchReleaseTime)).body(protoExposee);
	}

	@CrossOrigin(origins = { "https://editor.swagger.io" })
	@GetMapping(value = "/buckets/{dayDateStr}", produces = "application/json")
	public @ResponseBody ResponseEntity<BucketList> getListOfBuckets(@PathVariable String dayDateStr) {
		OffsetDateTime day = LocalDate.parse(dayDateStr).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime currentBucket = day;
		OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
		List<Long> bucketList = new ArrayList<>();
		while (currentBucket.toInstant().toEpochMilli() < Math.min(day.plusDays(1).toInstant().toEpochMilli(),
				now.toInstant().toEpochMilli())) {
			bucketList.add(currentBucket.toInstant().toEpochMilli());
			currentBucket = currentBucket.plusSeconds(batchLength / 1000);
		}
		BucketList list = new BucketList();
		list.setBuckets(bucketList);
		return ResponseEntity.ok(list);
	}

	@ExceptionHandler({IllegalArgumentException.class, InvalidDateException.class, JsonProcessingException.class,
			MethodArgumentNotValidException.class, BadBatchReleaseTimeException.class, DateTimeParseException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<Object> invalidArguments() {
		return ResponseEntity.badRequest().build();
	}


	@PostMapping(value = "/addHotspot")
	public @ResponseBody ResponseEntity<String> addHotspot(@Valid @RequestBody ArrayList<Integer> hotspotVector) {
		System.out.println("Received vector at /v1/addHotspot");
		NoOfVectorsReceived++;
		if(aggregate ==null)
		{
			aggregate = new ArrayList<Integer>();
		}
		for(int i=0;i<hotspotVector.size();i++)
		{
			int a = hotspotVector.get(i);
			if(NoOfVectorsReceived%3==1){
				aggregate.add(a);
			}
			else
				aggregate.set(i, aggregate.get(i)+a);
			System.out.print(a+" ");

		}
		System.out.println();
		if(NoOfVectorsReceived%3==0){
			System.out.println("Random Aggregate = " + aggregate);

			// Send aggregate to server B
			sendAggregate(new ResponseCallback<Void>() {
				@Override
				public void onSuccess(Void response) {
					System.out.println("Sent aggregrate to server B");
					// TODO
					// Clear aggregate? Store?
					aggregate = null;

				}
				@Override
				public void onError(Throwable throwable) {

					System.out.println(throwable.getMessage());
					// Log error
				}
			});
		}
		return ResponseEntity.ok().build();
	}

	private void sendAggregate(ResponseCallback<Void> responseCallback){
		if(serverBReportRepository==null)
			serverBReportRepository = new ServerBReportRepository();
		serverBReportRepository.aggregateHotspots(aggregate,responseCallback);
//		OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
//
//		Retrofit retrofit = new Retrofit.Builder()
//				.baseUrl("http://192.168.1.9:8082")
//				.addConverterFactory(GsonConverterFactory.create())
//				.client(httpClient.build())
//				.build();
//		ServerBReportService reportService = retrofit.create(ServerBReportService.class);
////		Call<Void> callSync = reportService.aggregateHotspots(aggregate);
//		reportService.aggregateHotspots(aggregate).enqueue(new Callback<Void>() {
//			@Override
//			public void onResponse(Call<Void> call, Response<Void> response) {
//				if(response.isSuccessful()){
//					System.out.println("Sent aggregate to server B.");
//				}
//			}
//
//			@Override
//			public void onFailure(Call<Void> call, Throwable throwable) {
//				System.out.println(throwable.getMessage());
//			}
//		});

//		try {
//			callSync.execute();
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}

	}
	public class MyRunnable implements Runnable {

		public void run(){
			System.out.println("MyRunnable running");
		}
	}



}
