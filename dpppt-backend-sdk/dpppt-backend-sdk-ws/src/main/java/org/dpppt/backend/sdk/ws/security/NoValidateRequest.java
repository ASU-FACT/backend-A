/*
 * Created by Ubique Innovation AG
 * https://www.ubique.ch
 * Copyright (c) 2020. All rights reserved.
 */

package org.dpppt.backend.sdk.ws.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.dpppt.backend.sdk.model.ExposeeRequest;

public class NoValidateRequest implements ValidateRequest {

	@Override
	public boolean isValid(Object authObject) {
		return true;
	}

	@Override
	public long getKeyDate(Object authObject, Object others) throws InvalidDateException {
		if (others instanceof ExposeeRequest) {
			ExposeeRequest request = ((ExposeeRequest) others);
			if(request.getKeyDate() < OffsetDateTime.now().minusDays(21).toInstant().toEpochMilli()) {
				throw new InvalidDateException();
			} else if (request.getKeyDate() > OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli()) {
				throw new InvalidDateException();
			}
			return request.getKeyDate();
		}
		throw new IllegalArgumentException();
	}

	@Override
	public boolean isFakeRequest(Object authObject, Object others) {
		if (others instanceof ExposeeRequest) {
			ExposeeRequest request = ((ExposeeRequest) others);
			return request.isFake() == 1;
		}
		throw new IllegalArgumentException();
	}

}