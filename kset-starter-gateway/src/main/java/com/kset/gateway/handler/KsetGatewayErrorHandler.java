package com.kset.gateway.handler;

import com.kset.common.trace.TraceHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@Slf4j
public class KsetGatewayErrorHandler implements ErrorWebExceptionHandler {

    private final String traceHeader;

    public KsetGatewayErrorHandler() {
        this(TraceHeaders.TRACE_ID_HEADER);
    }

    public KsetGatewayErrorHandler(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        HttpStatusCode status = statusFor(error);
        String traceId = GatewayErrorResponse.traceId(exchange, traceHeader);
        log.warn("Gateway request failed traceId={} status={} errorType={}",
                traceId, status.value(), error.getClass().getName());
        return GatewayErrorResponse.write(exchange, status, traceHeader);
    }

    private HttpStatusCode statusFor(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException responseStatusException) {
                return responseStatusException.getStatusCode();
            }
            if (cause instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (cause instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

}
