package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.adapter.scheduler.config.MeshDataSourceProperties;
import com.patra.catalog.adapter.scheduler.exception.MeshConfigurationException;
import com.patra.catalog.adapter.scheduler.util.MeshFileNameParser;
import com.patra.catalog.app.usecase.mesh.MeshImportUseCase;
import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 数据导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 MeSH 数据的批量导入。
/// URL 从配置文件读取，版本号从文件名自动推断。
///
/// **包含两个 JobHandler**：
///
/// - `meshDescriptorImportJob`：导入 MeSH 主题词（约 35,000 条）
/// - `meshQualifierImportJob`：导入 MeSH 限定词（约 80 条）
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     mesh:
///       descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
///       qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
/// ```
///
/// **导入策略**：
///
/// 纯 INSERT 策略，用于一次性数据初始化。如果表中已有数据，导入会失败。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshImportScheduleJob {

  private final MeshImportUseCase meshImportUseCase;
  private final MeshDataSourceProperties meshDataSourceProperties;

  /// 执行 MeSH 主题词导入任务。
  ///
  /// **JobHandler 名称**: `meshDescriptorImportJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("meshDescriptorImportJob")
  public void executeDescriptorImport() {
    log.info("MeSH Descriptor 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = meshDataSourceProperties.getDescriptorUrl();
      String meshVersion = MeshFileNameParser.extractVersion(url);
      log.info("MeSH Descriptor 配置：URL [{}]，版本 [{}]（从文件名推断）", url, meshVersion);

      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(url, meshVersion);
      MeshDescriptorImportResult result = meshImportUseCase.importDescriptors(command);
      handleSuccess(result.message());

    } catch (MeshConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 执行 MeSH 限定词导入任务。
  ///
  /// **JobHandler 名称**: `meshQualifierImportJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("meshQualifierImportJob")
  public void executeQualifierImport() {
    log.info("MeSH Qualifier 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = meshDataSourceProperties.getQualifierUrl();
      String meshVersion = MeshFileNameParser.extractVersion(url);
      log.info("MeSH Qualifier 配置：URL [{}]，版本 [{}]（从文件名推断）", url, meshVersion);

      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(url, meshVersion);
      MeshQualifierImportResult result = meshImportUseCase.importQualifiers(command);
      handleSuccess(result.message());

    } catch (MeshConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 处理配置错误。
  private void handleConfigurationError(MeshConfigurationException ex) {
    log.warn("MeSH 导入任务配置错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("配置错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("MeSH 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  private void handleSuccess(String message) {
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
