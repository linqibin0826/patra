package com.patra.starter.web.resp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ok_shouldWrapSuccessPayload() {
        ApiResponse<String> response = ApiResponse.ok("ok");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(ResultCode.OK.getCode());
        assertThat(response.getMessage()).isEqualTo(ResultCode.OK.getMessage());
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void failure_shouldFallbackMessageWhenNull() {
        ApiResponse<Void> response = ApiResponse.failure(ResultCode.BAD_REQUEST, null);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(response.getMessage()).isEqualTo(ResultCode.BAD_REQUEST.getMessage());
        assertThat(response.getData()).isNull();
    }

    @Test
    void error_shouldKeepCustomCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.error(503, "Service unavailable");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(503);
        assertThat(response.getMessage()).isEqualTo("Service unavailable");
        assertThat(response.getData()).isNull();
    }
}
