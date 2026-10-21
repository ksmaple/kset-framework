package com.kset.dubbo.web;

import com.kset.common.exception.BusinessException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.handler.GlobalExceptionHandler;
import com.kset.web.response.ApiResponse;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DubboRpcExceptionHandlerTest {

    @Test
    void missingProviderReturnsStableServiceUnavailableResponse() {
        DubboRpcExceptionHandler handler = new DubboRpcExceptionHandler(new KsetWebProperties());

        ResponseEntity<ApiResponse<Void>> response = handler.handleRpcException(
                new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "internal provider details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("服务暂时不可用，请稍后重试");
    }

    @Test
    void timeoutCanUseRealHttpStatus() {
        KsetWebProperties properties = new KsetWebProperties();
        properties.getExceptionHandling().setUseHttpStatus(true);
        DubboRpcExceptionHandler handler = new DubboRpcExceptionHandler(properties);

        ResponseEntity<ApiResponse<Void>> response = handler.handleRpcException(
                new RpcException(RpcException.TIMEOUT_EXCEPTION));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(504);
        assertThat(response.getBody().getMessage()).isEqualTo("服务响应超时，请稍后重试");
    }

    @Test
    void unknownRpcFailureKeepsSystemFallback() {
        DubboRpcExceptionHandler handler = new DubboRpcExceptionHandler(new KsetWebProperties());

        ResponseEntity<ApiResponse<Void>> response = handler.handleRpcException(
                new RpcException(RpcException.BIZ_EXCEPTION, "internal detail"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("系统异常，请稍后重试");
    }

    @Test
    void outerRpcFailureWinsOverNestedBusinessException() {
        KsetWebProperties properties = new KsetWebProperties();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(properties,
                List.of(new DubboRpcExceptionHandler(properties)));
        RpcException rpcFailure = new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER);
        rpcFailure.initCause(new BusinessException("1001", "nested business error"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(rpcFailure);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
    }
}
