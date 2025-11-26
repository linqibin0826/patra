package com.patra.catalog.adapter.scheduler.job;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.adapter.scheduler.param.MeshImportJobParam;
import com.patra.catalog.app.usecase.mesh.MeshImportUseCase;
import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 主题词导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 MeSH 主题词的批量导入。
///
/// **职责**：
///
/// - 协议转换：将 XXL-Job JSON 参数转换为 Application 层 Command
/// - 日志记录：记录任务开始、结束和错误信息
/// - 结果报告：向 XXL-Job 控制台报告执行状态
///
/// **参数格式**（JSON）：
///
/// ```json
/// {
///   "filePath": "/data/mesh/desc2025.xml",
///   "meshVersion": "2025",
///   "mode": "INCREMENTAL"
/// }
/// ```
///
/// **导入模式**：
///
/// - `INCREMENTAL`：增量导入，幂等执行，支持断点续传
/// - `TRUNCATE_REIMPORT`：清空重导入，先清空所有数据再重新导入
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshImportScheduleJob {

  private final ObjectMapper objectMapper;
  private final MeshImportUseCase meshImportUseCase;

  /// 执行 MeSH 导入任务。
  ///
  /// **JobHandler 名称**: `catalogMeshImportJob`
  @XxlJob("catalogMeshImportJob")
  public void execute() {
    String rawParam = XxlJobHelper.getJobParam();
    log.info("MeSH 导入任务已触发，jobId [{}]，参数：{}", XxlJobHelper.getJobId(), rawParam);

    try {
      MeshImportCommand command = parseJobParam(rawParam);
      log.debug(
          "已解析 MeSH 导入命令：文件路径 [{}]，版本 [{}]，模式 [{}]",
          command.filePath(),
          command.meshVersion(),
          command.mode());

      MeshImportResult result = meshImportUseCase.importDescriptors(command);
      handleSuccess(result);

    } catch (CatalogScheduleParameterException ex) {
      handleParameterError(ex);
      throw ex;
    } catch (Exception ex) {
      handleExecutionError(ex);
      throw new RuntimeException("MeSH 导入任务执行失败", ex);
    }
  }

  /// 解析 JSON 参数并转换为 Command。
  ///
  /// 只负责 JSON 反序列化（协议转换），参数验证委托给 Command。
  ///
  /// @param rawParam 原始 JSON 参数
  /// @return 导入命令
  /// @throws CatalogScheduleParameterException 当参数无法解析或验证失败时
  private MeshImportCommand parseJobParam(String rawParam) {
    if (CharSequenceUtil.isBlank(rawParam)) {
      throw new CatalogScheduleParameterException("MeSH 导入参数不能为空，请提供 JSON 格式参数");
    }

    MeshImportJobParam param;
    try {
      param = objectMapper.readValue(rawParam, MeshImportJobParam.class);
    } catch (Exception ex) {
      throw new CatalogScheduleParameterException("MeSH 导入参数解析失败：" + ex.getMessage(), ex);
    }

    // 委托给 Command 进行参数验证和枚举转换
    return MeshImportCommand.of(param.filePath(), param.meshVersion(), param.mode());
  }

  /// 处理成功执行。
  private void handleSuccess(MeshImportResult result) {
    log.info(result.message());
    XxlJobHelper.handleSuccess(result.message());
  }

  /// 处理参数错误。
  private void handleParameterError(CatalogScheduleParameterException ex) {
    log.warn("MeSH 导入任务参数错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("MeSH 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }
}
