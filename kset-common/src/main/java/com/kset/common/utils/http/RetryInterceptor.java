package com.kset.common.utils.http;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;


@Slf4j
public class RetryInterceptor implements Interceptor {

    private int maxRetry = 10;//最大重试次数

    //    延迟
    private long delay = 10000;
    //    叠加延迟
    private long increaseDelay = 10000;

    public RetryInterceptor() {
    }

    public RetryInterceptor(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public RetryInterceptor(int maxRetry, long delay) {
        this.maxRetry = maxRetry;
        this.delay = delay;
    }

    public RetryInterceptor(int maxRetry, long delay, long increaseDelay) {
        this.maxRetry = maxRetry;
        this.delay = delay;
        this.increaseDelay = increaseDelay;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {

        RetryWrapper retryWrapper = proceed(chain);

        while (retryWrapper.isNeedReTry()) {
            retryWrapper.retryNum++;
            try {
                Thread.sleep(delay + (retryWrapper.retryNum - 1) * increaseDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted", e);
            }
            proceed(chain, retryWrapper.request, retryWrapper);
        }
        if (retryWrapper.response != null) {
            return retryWrapper.response;
        }
        if (retryWrapper.lastException != null) {
            throw retryWrapper.lastException;
        }
        return chain.proceed(chain.request());
    }

    private RetryWrapper proceed(Chain chain) throws IOException {
        Request request = chain.request();
        RetryWrapper retryWrapper = new RetryWrapper(request, maxRetry);

        proceed(chain, request, retryWrapper);

        return retryWrapper;
    }

    private void proceed(Chain chain, Request request, RetryWrapper retryWrapper) throws IOException {
        try {
            Response response = chain.proceed(request);
            closePreviousResponse(retryWrapper);
            retryWrapper.setResponse(response);
        } catch (SocketException | SocketTimeoutException e) {
            retryWrapper.lastException = e;
            log.debug("HTTP retryable failure retryNum={} maxRetry={}", retryWrapper.retryNum, retryWrapper.maxRetry, e);
        }
    }

    private void closePreviousResponse(RetryWrapper retryWrapper) {
        if (retryWrapper.response != null) {
            retryWrapper.response.close();
        }
    }

    static class RetryWrapper {
        volatile int retryNum = 0;//假如设置为3次重试的话，则最大可能请求5次（默认1次+3次重试 + 最后一次默认）
        Request request;
        Response response;
        IOException lastException;
        private int maxRetry;

        public RetryWrapper(Request request, int maxRetry) {
            this.request = request;
            this.maxRetry = maxRetry;
        }

        public void setResponse(Response response) {
            this.response = response;
        }

        Response response() {
            return this.response;
        }

        Request request() {
            return this.request;
        }

        public boolean isSuccessful() {
            return response != null && response.isSuccessful();
        }

        public boolean isNeedReTry() {
            return !isSuccessful() && retryNum < maxRetry;
        }

        public void setRetryNum(int retryNum) {
            this.retryNum = retryNum;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }
}
