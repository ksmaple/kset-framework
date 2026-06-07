package com.kset.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void createsFromErrorCode() {
        BusinessException exception = new BusinessException(TestErrorCode.NAME_EXISTS);

        assertThat(exception.getCode()).isEqualTo(1002);
        assertThat(exception.getErrorCode()).isEqualTo("1002");
        assertThat(exception.getMessage()).isEqualTo("name exists");
    }

    @Test
    void canOverrideErrorCodeMessage() {
        BusinessException exception = new BusinessException(TestErrorCode.NAME_EXISTS, "custom message");

        assertThat(exception.getCode()).isEqualTo(1002);
        assertThat(exception.getErrorCode()).isEqualTo("1002");
        assertThat(exception.getMessage()).isEqualTo("custom message");
    }

    private enum TestErrorCode implements BizErrorCode {
        NAME_EXISTS(1002, "name exists");

        private final int code;
        private final String message;

        TestErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
