package dev.patra.analytics.infra.ingest.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.ConnectionInitiationException;
import com.clickhouse.client.api.ServerException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RejectionCodesTest {

  @ParameterizedTest
  @ValueSource(
      ints = {6, 25, 26, 27, 33, 38, 41, 53, 70, 72, 117, 321, 349, 376, 441, 467, 675, 676})
  void data_errors_are_rejected(int code) {
    assertThat(RejectionCodes.rejected(server(code))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {60, 159, 241, 243, 252})
  void server_side_errors_are_retryable(int code) {
    assertThat(RejectionCodes.rejected(server(code))).isFalse();
  }

  @Test
  void connection_and_timeout_errors_are_retryable() {
    assertThat(RejectionCodes.rejected(new ConnectionInitiationException("refused"))).isFalse();
    assertThat(RejectionCodes.rejected(new SocketTimeoutException("read timed out"))).isFalse();
  }

  @Test
  void wrapped_server_error_is_unwrapped() {
    assertThat(RejectionCodes.rejected(new ExecutionException(new CompletionException(server(27)))))
        .isTrue();
  }

  private static ServerException server(int code) {
    return new ServerException(code, "Code: " + code, 500, "q");
  }
}
