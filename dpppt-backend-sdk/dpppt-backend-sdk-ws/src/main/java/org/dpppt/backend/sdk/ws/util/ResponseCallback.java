package org.dpppt.backend.sdk.ws.util;


public interface ResponseCallback<T> {

    void onSuccess(T response);

    void onError(Throwable throwable);
}
