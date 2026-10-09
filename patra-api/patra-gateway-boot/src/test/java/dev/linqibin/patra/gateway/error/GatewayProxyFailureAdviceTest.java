package dev.linqibin.patra.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.web.error.handler.GlobalRestExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class GatewayProxyFailureAdviceTest {

  private final GlobalRestExceptionHandler globalHandler = mock(GlobalRestExceptionHandler.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final GatewayProxyFailureAdvice advice =
      new GatewayProxyFailureAdvice(HttpStdErrors.of("GW"), globalHandler);

  @Test
  void should_hand_a_safe_wrapper_to_the_global_handler_instead_of_the_raw_exception() {
    ResourceAccessException raw =
        new ResourceAccessException(
            "I/O error on GET request for \"http://192.168.97.5:6300/venues\"",
            new ConnectException("Connection refused"));

    advice.handleProxyFailure(raw, request, response);

    ArgumentCaptor<Exception> handed = ArgumentCaptor.forClass(Exception.class);
    verify(globalHandler).handleException(handed.capture(), same(request), same(response));
    assertThat(handed.getValue()).isInstanceOf(DownstreamUnavailableException.class);
    assertThat(handed.getValue().getMessage()).doesNotContain("http://");
    assertThat(handed.getValue().getCause()).isSameAs(raw);
  }

  @Test
  void should_pass_unclassified_exceptions_through_unchanged() {
    HttpServerErrorException badGateway = new HttpServerErrorException(HttpStatus.BAD_GATEWAY);

    advice.handleProxyFailure(badGateway, request, response);

    verify(globalHandler).handleException(same(badGateway), any(), any());
  }
}
