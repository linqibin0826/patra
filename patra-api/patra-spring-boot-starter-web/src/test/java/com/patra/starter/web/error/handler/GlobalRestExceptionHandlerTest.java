package com.patra.starter.web.error.handler;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.web.error.adapter.ProblemDetailAdapter;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalRestExceptionHandlerTest {

    @Test
    void handleException_shouldReturnAdaptedProblemDetail() throws Exception {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "系统错误");
        detail.setProperty(ErrorKeys.PATH, "/demo");
        ProblemDetailResponse response = new ProblemDetailResponse(
                detail,
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorResolution(new DummyCode("ING-0500", 500), 500)
        );

        GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler(new StubAdapter(response), errors -> List.of());

        ResponseEntityAssertions result = ResponseEntityAssertions.from(
                handler.handleException(new RuntimeException("boom"), new MockHttpServletRequest())
        );
        assertThat(result.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.problemDetail()).isSameAs(detail);
    }

    @Test
    void handleMethodArgumentNotValid_shouldAttachErrorsAndLimitSize() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DemoPayload(), "payload");
        for (int i = 0; i < 3; i++) {
            bindingResult.addError(new FieldError("payload", "field" + i, "bad" + i, false, null, null, "msg" + i));
        }

        MethodParameter parameter = methodParameter();
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "校验失败");
        ProblemDetailResponse response = new ProblemDetailResponse(
                detail,
                HttpStatus.BAD_REQUEST,
                new ErrorResolution(new DummyCode("ING-0400", 400), 400)
        );

        List<ValidationError> formatted = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            formatted.add(new ValidationError("field" + i, "value" + i, "msg" + i));
        }

        GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler(new StubAdapter(response), new FixedFormatter(formatted));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/demo");
        WebRequest webRequest = new ServletWebRequest(servletRequest);

        org.springframework.http.ResponseEntity<?> responseEntity = handler.handleMethodArgumentNotValid(
                ex,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest
        );
        ResponseEntityAssertions result = ResponseEntityAssertions.from(responseEntity);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.problemDetail().getProperties()).containsKey(ErrorKeys.ERRORS);
        @SuppressWarnings("unchecked")
        List<ValidationError> finalErrors = (List<ValidationError>) result.problemDetail().getProperties().get(ErrorKeys.ERRORS);
        assertThat(finalErrors).hasSize(100);
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        Method method = GlobalRestExceptionHandlerTest.class.getDeclaredMethod("handle", DemoPayload.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void handle(DemoPayload payload) {
    }

    private record DemoPayload() {
    }

    private static final class StubAdapter implements ProblemDetailAdapter {
        private final ProblemDetailResponse response;

        private StubAdapter(ProblemDetailResponse response) {
            this.response = response;
        }

        @Override
        public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
            return response;
        }
    }

    private static final class FixedFormatter implements ValidationErrorsFormatter {
        private final List<ValidationError> errors;

        private FixedFormatter(List<ValidationError> errors) {
            this.errors = errors;
        }

        @Override
        public List<ValidationError> formatWithMasking(org.springframework.validation.BindingResult bindingResult) {
            return new ArrayList<>(errors);
        }
    }

    private record DummyCode(String code, int httpStatus) implements ErrorCodeLike {
        @Override
        public String code() {
            return code;
        }

        @Override
        public int httpStatus() {
            return httpStatus;
        }
    }

    private record ResponseEntityAssertions(HttpStatus status, MediaType contentType, ProblemDetail problemDetail) {
        static ResponseEntityAssertions from(org.springframework.http.ResponseEntity<?> entity) {
            ProblemDetail detail = (ProblemDetail) entity.getBody();
            HttpStatus status = entity.getStatusCode() instanceof HttpStatus httpStatus
                    ? httpStatus
                    : HttpStatus.valueOf(entity.getStatusCode().value());
            return new ResponseEntityAssertions(status, entity.getHeaders().getContentType(), detail);
        }
    }
}
