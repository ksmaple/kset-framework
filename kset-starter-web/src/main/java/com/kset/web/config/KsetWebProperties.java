package com.kset.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kset.web")
public class KsetWebProperties {

    private final Oplog oplog = new Oplog();
    private final RequestLogging requestLogging = new RequestLogging();
    private final Response response = new Response();
    private final ExceptionHandling exceptionHandling = new ExceptionHandling();

    public Oplog getOplog() {
        return oplog;
    }

    public RequestLogging getRequestLogging() {
        return requestLogging;
    }

    public Response getResponse() {
        return response;
    }

    public ExceptionHandling getExceptionHandling() {
        return exceptionHandling;
    }

    public static class Oplog {
        private boolean enabled = true;
        private String userIdHeader = "X-User-Id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserIdHeader() {
            return userIdHeader;
        }

        public void setUserIdHeader(String userIdHeader) {
            this.userIdHeader = userIdHeader;
        }
    }

    public static class RequestLogging {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Response {
        private boolean traceIdEnabled = true;

        public boolean isTraceIdEnabled() {
            return traceIdEnabled;
        }

        public void setTraceIdEnabled(boolean traceIdEnabled) {
            this.traceIdEnabled = traceIdEnabled;
        }
    }

    public static class ExceptionHandling {
        /**
         * Whether framework exception responses should use real HTTP error status.
         * Default false keeps HTTP 200 and returns error status through ApiResponse.code.
         */
        private boolean useHttpStatus = false;
        /**
         * Business code used when BusinessException does not carry a numeric errorCode.
         */
        private int defaultBusinessCode = 400;
        /**
         * Business code used for validation errors.
         */
        private int validationCode = 400;
        /**
         * Business code used for unknown system errors.
         */
        private int systemCode = 500;
        /**
         * Business code used when request method is not supported.
         */
        private int methodNotAllowedCode = 405;
        /**
         * Business code used when requested resource is not found.
         */
        private int notFoundCode = 404;
        /**
         * Business code used when request media type is not supported.
         */
        private int unsupportedMediaTypeCode = 415;
        /**
         * Business code used when request body cannot be read.
         */
        private int badRequestCode = 400;
        /**
         * Whether numeric BusinessException#errorCode should be parsed as ApiResponse code.
         */
        private boolean parseBusinessErrorCode = true;

        public boolean isUseHttpStatus() {
            return useHttpStatus;
        }

        public void setUseHttpStatus(boolean useHttpStatus) {
            this.useHttpStatus = useHttpStatus;
        }

        public int getDefaultBusinessCode() {
            return defaultBusinessCode;
        }

        public void setDefaultBusinessCode(int defaultBusinessCode) {
            this.defaultBusinessCode = defaultBusinessCode;
        }

        public int getValidationCode() {
            return validationCode;
        }

        public void setValidationCode(int validationCode) {
            this.validationCode = validationCode;
        }

        public int getSystemCode() {
            return systemCode;
        }

        public void setSystemCode(int systemCode) {
            this.systemCode = systemCode;
        }

        public int getMethodNotAllowedCode() {
            return methodNotAllowedCode;
        }

        public void setMethodNotAllowedCode(int methodNotAllowedCode) {
            this.methodNotAllowedCode = methodNotAllowedCode;
        }

        public int getNotFoundCode() {
            return notFoundCode;
        }

        public void setNotFoundCode(int notFoundCode) {
            this.notFoundCode = notFoundCode;
        }

        public int getUnsupportedMediaTypeCode() {
            return unsupportedMediaTypeCode;
        }

        public void setUnsupportedMediaTypeCode(int unsupportedMediaTypeCode) {
            this.unsupportedMediaTypeCode = unsupportedMediaTypeCode;
        }

        public int getBadRequestCode() {
            return badRequestCode;
        }

        public void setBadRequestCode(int badRequestCode) {
            this.badRequestCode = badRequestCode;
        }

        public boolean isParseBusinessErrorCode() {
            return parseBusinessErrorCode;
        }

        public void setParseBusinessErrorCode(boolean parseBusinessErrorCode) {
            this.parseBusinessErrorCode = parseBusinessErrorCode;
        }
    }
}
