package dev.linqibin.patra.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
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

class GatewayErrorMappingContributorTest {

  private final GatewayErrorMappingContributor contributor =
      new GatewayErrorMappingContributor(HttpStdErrors.of("GW"));

  @Test
  void should_map_gateway_service_unavailable_to_503() {
    HttpServerErrorException noInstance =
        new HttpServerErrorException(
            HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for patra-ingest");

    assertThat(codeOf(contributor.mapException(noInstance))).isEqualTo("GW-0503");
  }

  @Test
  void should_ignore_other_http_server_errors() {
    assertThat(contributor.mapException(new HttpServerErrorException(HttpStatus.BAD_GATEWAY)))
        .isEmpty();
  }

  @Test
  void should_map_connection_refused_to_503() {
    ResourceAccessException refused =
        new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

    assertThat(codeOf(contributor.mapException(refused))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_connect_timeout_to_503_not_504() {
    ResourceAccessException connectTimeout =
        new ResourceAccessException(
            "I/O error", new HttpConnectTimeoutException("HTTP connect timed out"));

    assertThat(codeOf(contributor.mapException(connectTimeout))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_unknown_host_and_unresolved_address_to_503() {
    ResourceAccessException unknownHost =
        new ResourceAccessException("I/O error", new UnknownHostException("nowhere"));
    // JDK 客户端解析不了地址时抛的是带 UnresolvedAddressException 原因的 ConnectException
    ConnectException unresolvedConnect = new ConnectException("nowhere");
    unresolvedConnect.initCause(new UnresolvedAddressException());
    ResourceAccessException unresolved =
        new ResourceAccessException("I/O error", unresolvedConnect);

    assertThat(codeOf(contributor.mapException(unknownHost))).isEqualTo("GW-0503");
    assertThat(codeOf(contributor.mapException(unresolved))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_read_timeout_to_504() {
    ResourceAccessException httpTimeout =
        new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"));
    ResourceAccessException socketTimeout =
        new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

    assertThat(codeOf(contributor.mapException(httpTimeout))).isEqualTo("GW-0504");
    assertThat(codeOf(contributor.mapException(socketTimeout))).isEqualTo("GW-0504");
  }

  @Test
  void should_map_nested_timeout_cause_to_504() {
    ResourceAccessException nested =
        new ResourceAccessException(
            "I/O error", new IOException("wrapped", new HttpTimeoutException("timed out")));

    assertThat(codeOf(contributor.mapException(nested))).isEqualTo("GW-0504");
  }

  @Test
  void should_map_io_failure_without_known_cause_to_503() {
    assertThat(codeOf(contributor.mapException(new ResourceAccessException("I/O error"))))
        .isEqualTo("GW-0503");
  }

  @Test
  void should_ignore_unrelated_exceptions() {
    assertThat(contributor.mapException(new IllegalStateException("boom"))).isEmpty();
  }

  private static String codeOf(Optional<ErrorCodeLike> resolved) {
    return resolved.orElseThrow().code();
  }
}
