package com.kset.web.handler;

import com.kset.web.response.ApiResponse;
import org.springframework.http.ResponseEntity;

/** Optional starter-specific mapping used by the root-first web exception handler. */
public interface WebExceptionMapper {

    boolean supports(Exception exception);

    ResponseEntity<ApiResponse<Void>> handle(Exception exception);
}
