package dev.linqibin.patra.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class ProxyFailureClassifierTest {

  private final HttpStdErrors.Group http = HttpStdErrors.of("GW");

  @Test
  void should_wrap_gateway_service_unavailable_as_downstream_unavailable() {
    HttpServerErrorException noInstance =
        new HttpServerErrorException(
            HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for patra-ingest");

    ApplicationException wrapped = ProxyFailureClassifier.wrap(noInstance, http).orElseThrow();

    assertThat(wrapped).isInstanceOf(DownstreamUnavailableException.class);
    assertThat(wrapped.getErrorCode().code()).isEqualTo("GW-0503");
    assertThat(wrapped.getMessage()).isEqualTo("下游服务暂时不可用");
    assertThat(wrapped.getCause()).isSameAs(noInstance);
  }

  @Test
  void should_ignore_other_http_server_errors() {
    assertThat(
            ProxyFailureClassifier.wrap(new HttpServerErrorException(HttpStatus.BAD_GATEWAY), http))
        .isEmpty();
  }

  @Test
  void should_wrap_connection_refused_as_downstream_unavailable() {
    ResourceAccessException refused =
        new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

    assertThat(codeOf(ProxyFailureClassifier.wrap(refused, http))).isEqualTo("GW-0503");
  }

  @Test
  void should_treat_connect_timeout_as_unavailable_not_timeout() {
    ResourceAccessException connectTimeout =
        new ResourceAccessException(
            "I/O error", new HttpConnectTimeoutException("HTTP connect timed out"));

    assertThat(codeOf(ProxyFailureClassifier.wrap(connectTimeout, http))).isEqualTo("GW-0503");
  }

  @Test
  void should_wrap_unknown_host_and_unresolved_address_as_unavailable() {
    ResourceAccessException unknownHost =
        new ResourceAccessException("I/O error", new UnknownHostException("nowhere"));
    ConnectException unresolvedConnect = new ConnectException("nowhere");
    unresolvedConnect.initCause(new UnresolvedAddressException());
    ResourceAccessException unresolved =
        new ResourceAccessException("I/O error", unresolvedConnect);

    assertThat(codeOf(ProxyFailureClassifier.wrap(unknownHost, http))).isEqualTo("GW-0503");
    assertThat(codeOf(ProxyFailureClassifier.wrap(unresolved, http))).isEqualTo("GW-0503");
  }

  @Test
  void should_wrap_read_timeout_as_downstream_timeout() {
    ResourceAccessException httpTimeout =
        new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"));
    ResourceAccessException socketTimeout =
        new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

    ApplicationException wrapped = ProxyFailureClassifier.wrap(httpTimeout, http).orElseThrow();
    assertThat(wrapped).isInstanceOf(DownstreamTimeoutException.class);
    assertThat(wrapped.getErrorCode().code()).isEqualTo("GW-0504");
    assertThat(wrapped.getMessage()).isEqualTo("下游服务响应超时");
    assertThat(codeOf(ProxyFailureClassifier.wrap(socketTimeout, http))).isEqualTo("GW-0504");
  }

  @Test
  void should_find_timeout_nested_in_cause_chain() {
    ResourceAccessException nested =
        new ResourceAccessException(
            "I/O error", new IOException("wrapped", new HttpTimeoutException("timed out")));

    assertThat(codeOf(ProxyFailureClassifier.wrap(nested, http))).isEqualTo("GW-0504");
  }

  @Test
  void should_wrap_io_failure_without_known_cause_as_unavailable() {
    assertThat(codeOf(ProxyFailureClassifier.wrap(new ResourceAccessException("I/O error"), http)))
        .isEqualTo("GW-0503");
  }

  @Test
  void should_ignore_unrelated_exceptions() {
    assertThat(ProxyFailureClassifier.wrap(new IllegalStateException("boom"), http)).isEmpty();
  }

  private static String codeOf(Optional<ApplicationException> wrapped) {
    return wrapped.orElseThrow().getErrorCode().code();
  }
}
