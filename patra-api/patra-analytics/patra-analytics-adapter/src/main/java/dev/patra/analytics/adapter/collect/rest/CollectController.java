package dev.patra.analytics.adapter.collect.rest;

import dev.patra.analytics.adapter.collect.rest.response.CollectResponse;
import dev.patra.analytics.app.collect.usecase.collect.CollectEventsCommand;
import dev.patra.analytics.app.collect.usecase.collect.CollectEventsResult;
import dev.patra.analytics.domain.collect.exception.BatchTooLargeException;
import dev.patra.tool.cqrs.CommandBus;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 采集端点（采集 spec §4.1）：门户服务端与模拟器经网关调用，不要求登录。
///
/// 请求体按字节读，超过 1 MiB 直接拒收；收下（不论其中几条不合格）返回 202，外壳不合格 400，Kafka 写不进 503。
@Tag(name = "Collect", description = "采集行为事件")
@RestController
@RequiredArgsConstructor
public class CollectController {

  /// 请求体上限（字节）
  static final int MAX_BODY_BYTES = 1_048_576;

  /// 每批采集结果的指标
  static final String BATCHES_METRIC = "analytics.collect.batches";

  private final CommandBus commandBus;
  private final MeterRegistry meters;

  /// 采集一批事件。
  ///
  /// @param request 请求（按字节读请求体）
  /// @return 合格与不合格的条数
  @Operation(summary = "采集一批事件", description = "请求体是 collect-batch：1～500 条采集信封")
  @PostMapping(path = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public CollectResponse collect(HttpServletRequest request) {
    String status = "202";
    try {
      CollectEventsResult result = commandBus.handle(CollectEventsCommand.of(readBody(request)));
      return CollectResponse.of(result.accepted(), result.rejected());
    } catch (RuntimeException e) {
      status = statusOf(e);
      throw e;
    } finally {
      meters.counter(BATCHES_METRIC, "status", status).increment();
    }
  }

  /// 按字节读请求体，超过上限就拒收。
  ///
  /// @param request 请求
  /// @return 请求体文本（UTF-8）
  private static String readBody(HttpServletRequest request) {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      throw new BatchTooLargeException("请求体超过 1 MiB");
    }
    try (InputStream in = request.getInputStream()) {
      byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
      if (body.length > MAX_BODY_BYTES) {
        throw new BatchTooLargeException("请求体超过 1 MiB");
      }
      return new String(body, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 异常对应的状态，只用于指标标签。
  ///
  /// @param e 异常
  /// @return 状态码文本
  private static String statusOf(RuntimeException e) {
    return switch (e.getClass().getSimpleName()) {
      case "InvalidBatchException" -> "400";
      case "BatchTooLargeException" -> "413";
      case "EventPublishUnavailableException" -> "503";
      default -> "500";
    };
  }
}
