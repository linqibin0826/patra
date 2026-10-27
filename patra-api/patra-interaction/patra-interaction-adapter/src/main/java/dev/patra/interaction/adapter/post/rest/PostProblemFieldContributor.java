package dev.patra.interaction.adapter.post.rest;

import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.tool.web.error.spi.ProblemFieldContributor;
import java.util.Map;
import org.springframework.stereotype.Component;

/// 重复投稿日期（409 `DUPLICATE_SUBMISSION`）的响应带上已有经验的 ID：`existingPostId`，字符串（与其他 ID 一致）。
/// 错误引擎本身只输出 `traits[]`、`errors[]`、`retryAfterSeconds`，额外字段经这个 SPI 加。
@Component
public class PostProblemFieldContributor implements ProblemFieldContributor {

  /// 加字段。
  ///
  /// @param fields 扩展字段
  /// @param exception 正在处理的异常
  @Override
  public void contribute(Map<String, Object> fields, Throwable exception) {
    if (exception instanceof DuplicateSubmissionException duplicate) {
      fields.put("existingPostId", Long.toString(duplicate.existingPostId().value()));
    }
  }
}
