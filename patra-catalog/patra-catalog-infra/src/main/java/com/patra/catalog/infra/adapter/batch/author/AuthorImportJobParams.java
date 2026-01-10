package com.patra.catalog.infra.adapter.batch.author;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// PubMed Computed Authors 导入 Job 参数。
///
/// 用于 PubMed Computed Authors 批量导入任务的强类型参数。
///
/// **流式处理特性**：
///
/// - 使用 `downloadUrl` 存储 JSON Lines 文件下载 URL
/// - ItemReader 在 open() 时建立 HTTP 连接，流式下载并解析
/// - 无临时文件清理逻辑
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorImportJobParams implements JobParams {

  /// PubMed Computed Authors JSON Lines 文件下载 URL
  private String downloadUrl;
}
