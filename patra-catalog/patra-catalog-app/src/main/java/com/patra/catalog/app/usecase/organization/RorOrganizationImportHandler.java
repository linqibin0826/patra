package com.patra.catalog.app.usecase.organization;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.organization.command.RorOrganizationImportCommand;
import com.patra.catalog.app.usecase.organization.command.RorOrganizationImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.organization.RorImportParams;
import com.patra.catalog.domain.port.batch.RorOrganizationBatchPort;
import com.patra.catalog.domain.port.repository.OrganizationRepository;
import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// ROR 机构导入处理器。
///
/// **职责**：
///
/// - 编排 ROR 机构导入流程
/// - 委派具体任务给领域端口
///
/// **临时文件下载特性**：
///
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，再从本地文件解析
/// - 传递 downloadUrl 给 Job，由 ItemReader 负责下载到临时文件
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持增量或覆盖模式
/// - 如果表中已有数据，直接抛出 `DataAlreadyExistsException`
/// - 如需重新导入，必须先手动清空数据库
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class RorOrganizationImportHandler
    implements CommandHandler<RorOrganizationImportCommand, RorOrganizationImportResult> {

  private final OrganizationRepository organizationRepository;
  private final RorOrganizationBatchPort rorOrganizationBatchPort;

  /// 导入 ROR 机构数据。
  ///
  /// 大数据量（约 110,000 条），使用批处理进行导入。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何机构数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// **事务说明**：
  ///
  /// 本方法**不使用 `@Transactional`**，原因：
  ///
  /// 1. 本方法仅启动 Spring Batch Job，不直接执行数据库写操作
  /// 2. 实际数据持久化由 Batch Job 的 chunk 事务管理（每 500 条一个事务）
  /// 3. `launchImport()` 返回后任务异步执行，无法用单一事务包裹
  ///
  /// @param command 导入命令（包含文件 URL 和版本）
  /// @return 导入结果
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  public RorOrganizationImportResult handle(RorOrganizationImportCommand command) {
    log.info("启动 ROR 机构导入，URL：{}，版本：{}", command.url(), command.rorVersion());

    // 1. 检查数据是否已存在
    if (organizationRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("Organization");
    }

    // 2. 启动批处理导入（传递 downloadUrl，由 ItemReader 负责下载到临时文件）
    try {
      RorImportParams params = RorImportParams.withDownloadUrl(command.url(), command.rorVersion());
      Long executionId = rorOrganizationBatchPort.launchImport(params);

      log.info("ROR 机构导入任务已启动，executionId：{}", executionId);
      return RorOrganizationImportResult.success(executionId, command.url(), command.rorVersion());

    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(CatalogErrorCode.CAT_1401, "ROR 机构导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1401, "ROR 机构导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需清理临时文件，ItemReader 在 close() 时自动删除临时文件
  }
}
