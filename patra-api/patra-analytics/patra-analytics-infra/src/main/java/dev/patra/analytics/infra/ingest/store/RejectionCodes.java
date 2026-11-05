package dev.patra.analytics.infra.ingest.store;

import com.clickhouse.client.api.ServerException;
import java.util.Set;

/// 「重试也不会好」的 ClickHouse 错误码：数据本身解析或转换不了。其余一律当可重试（入库设计 §4.4）。
///
/// 数字码取自 ClickHouse 26.8 的 `errorCodeToName()`（即 `src/Common/ErrorCodes.cpp`）。
final class RejectionCodes {

  /// 被拒的错误码
  private static final Set<Integer> REJECTED =
      Set.of(
          6, // CANNOT_PARSE_TEXT
          25, // CANNOT_PARSE_ESCAPE_SEQUENCE
          26, // CANNOT_PARSE_QUOTED_STRING
          27, // CANNOT_PARSE_INPUT_ASSERTION_FAILED
          33, // CANNOT_READ_ALL_DATA
          38, // CANNOT_PARSE_DATE
          41, // CANNOT_PARSE_DATETIME
          53, // TYPE_MISMATCH
          70, // CANNOT_CONVERT_TYPE
          72, // CANNOT_PARSE_NUMBER
          117, // INCORRECT_DATA
          321, // VALUE_IS_OUT_OF_RANGE_OF_DATA_TYPE
          349, // CANNOT_INSERT_NULL_IN_ORDINARY_COLUMN
          376, // CANNOT_PARSE_UUID
          441, // CANNOT_PARSE_DOMAIN_VALUE_FROM_STRING
          467, // CANNOT_PARSE_BOOL
          675, // CANNOT_PARSE_IPV4
          676); // CANNOT_PARSE_IPV6

  private RejectionCodes() {}

  /// 沿原因链找 ClickHouse 的服务端错误，判断是不是被拒。
  ///
  /// @param error 写入时抛出的异常（可能被 `ExecutionException` 等包着）
  /// @return 被拒时为 `true`；连不上、超时、其余服务端错误为 `false`
  static boolean rejected(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ServerException server) {
        return REJECTED.contains(server.getCode());
      }
    }
    return false;
  }
}
