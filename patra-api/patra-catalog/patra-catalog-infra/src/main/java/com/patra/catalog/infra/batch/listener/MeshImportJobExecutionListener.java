package com.patra.catalog.infra.batch.listener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

/// MeSH 导入 Job 执行监听器。
///
/// 在 Job 完成后清理临时文件。
///
/// **清理策略**：
///
/// - COMPLETED：删除临时文件（导入成功，文件不再需要）
/// - STOPPED：删除临时文件（用户手动停止，文件不再需要）
/// - FAILED：保留临时文件（支持断点续传，下次启动可继续）
///
/// **参数要求**：
///
/// - `filePath`：文件路径（必须）
/// - `tempFile`：是否为临时文件（"true"/"false"，缺失默认为 false）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class MeshImportJobExecutionListener implements JobExecutionListener {

  @Override
  public void afterJob(JobExecution jobExecution) {
    JobParameters params = jobExecution.getJobParameters();
    String filePath = params.getString("filePath");
    String tempFileStr = params.getString("tempFile");

    // 如果 filePath 缺失或 tempFile 不是 true，直接返回
    if (filePath == null || !"true".equalsIgnoreCase(tempFileStr)) {
      return;
    }

    BatchStatus status = jobExecution.getStatus();
    if (status == BatchStatus.COMPLETED || status == BatchStatus.STOPPED) {
      try {
        Path path = Path.of(filePath);
        boolean deleted = Files.deleteIfExists(path);
        if (deleted) {
          log.info("Job 完成，已清理临时文件：{}，状态：{}", filePath, status);
        }
      } catch (IOException e) {
        log.warn("清理临时文件失败：{}，原因：{}", filePath, e.getMessage());
      }
    } else {
      log.info("Job 状态为 {}，保留临时文件以支持续传：{}", status, filePath);
    }
  }
}
