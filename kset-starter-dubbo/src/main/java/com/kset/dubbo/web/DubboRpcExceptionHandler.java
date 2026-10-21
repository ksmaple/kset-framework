package com.kset.dubbo.web;

import com.kset.dubbo.exception.DubboFailureType;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import com.kset.web.handler.WebExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public class DubboRpcExceptionHandler implements WebExceptionMapper {

    private final KsetWebProperties.ExceptionHandling properties;

    public DubboRpcExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties.getExceptionHandling();
    }

    public ResponseEntity<ApiResponse<Void>> handleRpcException(RpcException exception) {
        DubboFailureType failureType = DubboFailureType.from(exception);
        if (failureType == DubboFailureType.UNKNOWN) {
            log.error("Unhandled Dubbo RPC exception", exception);
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.fail(properties.getSystemCode(), "系统异常，请稍后重试"));
        }
        log.warn("Dubbo call failed type={} code={}", failureType, exception.getCode());
        return switch (failureType) {
            case NO_PROVIDER, UNAVAILABLE -> response(HttpStatus.SERVICE_UNAVAILABLE,
                    ApiResponse.fail(503, "服务暂时不可用，请稍后重试"));
            case TIMEOUT -> response(HttpStatus.GATEWAY_TIMEOUT,
                    ApiResponse.fail(504, "服务响应超时，请稍后重试"));
            case UNKNOWN -> throw new IllegalStateException("Unknown failure already handled");
        };
    }

    @Override
    public boolean supports(Exception exception) {
        return exception instanceof RpcException;
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        return handleRpcException((RpcException) exception);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }
}
