package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 限定词导入处理器。
///
/// **职责**：
///
/// - 编排 MeSH 限定词导入流程
/// - 管理事务边界
/// - 委派具体任务给领域端口
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，HTTP 响应体直接传递给 Parser
/// - 使用 try-with-resources 自动管理 HTTP 连接
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
public class MeshQualifierImportHandler
    implements CommandHandler<MeshQualifierImportCommand, MeshQualifierImportResult> {

  private final StreamingDownloadPort streamingDownloadPort;
  private final MeshQualifierParserPort qualifierParserPort;
  private final MeshQualifierRepository qualifierRepository;

  /// 导入 MeSH 限定词。
  ///
  /// 从远程 URL 流式下载并解析 XML，批量保存到数据库。
  ///
  /// **事务策略**：整个操作在一个事务内完成（数据量小，约 80 条）。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何限定词数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  @Transactional
  public MeshQualifierImportResult handle(MeshQualifierImportCommand command) {
    log.info("启动 MeSH 限定词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (qualifierRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH Qualifier");
    }

    // 2. 流式下载并解析（无磁盘落盘）
    try (StreamingDownloadResult downloadResult =
        streamingDownloadPort.download(URI.create(command.url()))) {

      log.info("HTTP 连接建立成功，开始流式解析");

      // 3. 解析 XML 并设置版本号
      List<MeshQualifierAggregate> qualifiers =
          qualifierParserPort
              .parse(downloadResult.inputStream())
              .map(q -> q.withMeshVersion(command.meshVersion()))
              .toList();

      // 4. 批量保存
      qualifierRepository.saveBatch(qualifiers);

      log.info("MeSH 限定词导入完成，数量：{}", qualifiers.size());
      return MeshQualifierImportResult.success(
          command.url(), command.meshVersion(), qualifiers.size());

    } catch (ApplicationException e) {
      // 已经是 ApplicationException，直接重新抛出
      throw e;
    } catch (RuntimeException e) {
      // 包装其他运行时异常
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      // 包装检查异常
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需 finally 清理临时文件，try-with-resources 自动关闭 HTTP 连接
  }
}
