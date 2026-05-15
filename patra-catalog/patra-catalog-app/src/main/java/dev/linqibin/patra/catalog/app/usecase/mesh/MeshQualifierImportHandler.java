package dev.linqibin.patra.catalog.app.usecase.mesh;

import dev.linqibin.patra.catalog.api.error.CatalogErrorCode;
import dev.linqibin.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import dev.linqibin.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import dev.linqibin.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import dev.linqibin.patra.catalog.domain.port.repository.MeshQualifierRepository;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.DomainException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
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
/// **下载策略**：
///
/// - 下载到临时文件，解耦 HTTP 连接与数据处理
/// - 使用 finally 块自动清理临时文件
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

  private final FileDownloadPort fileDownloadPort;
  private final MeshQualifierParserPort qualifierParserPort;
  private final MeshQualifierRepository qualifierRepository;

  /// 导入 MeSH 限定词。
  ///
  /// 从远程 URL 下载到临时文件并解析 XML，批量保存到数据库。
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

    // 2. 下载到临时文件
    FileDownloadResult downloadResult = fileDownloadPort.download(URI.create(command.url()));
    try {
      log.info("文件下载完成（{} 字节），开始解析", downloadResult.fileSize());

      // 3. 解析 XML 并设置版本号
      List<MeshQualifierAggregate> qualifiers;
      try (InputStream fileStream = Files.newInputStream(downloadResult.filePath())) {
        qualifiers =
            qualifierParserPort
                .parse(fileStream)
                .map(q -> q.withMeshVersion(command.meshVersion()))
                .toList();
      }

      // 4. 批量保存
      qualifierRepository.saveBatch(qualifiers);

      log.info("MeSH 限定词导入完成，数量：{}", qualifiers.size());
      return MeshQualifierImportResult.success(
          command.url(), command.meshVersion(), qualifiers.size());

    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入时发生意外错误: " + e.getMessage(), e);
    } finally {
      try {
        Files.deleteIfExists(downloadResult.filePath());
      } catch (IOException e) {
        log.warn("清理临时文件失败：{}", downloadResult.filePath(), e);
      }
    }
  }
}
