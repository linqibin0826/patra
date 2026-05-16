package dev.linqibin.patra.catalog.app.usecase.author;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.patra.catalog.api.error.CatalogErrorCode;
import dev.linqibin.patra.catalog.app.usecase.author.command.AuthorImportCommand;
import dev.linqibin.patra.catalog.app.usecase.author.dto.AuthorImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorImportParams;
import dev.linqibin.patra.catalog.domain.port.batch.AuthorBatchPort;
import dev.linqibin.patra.catalog.domain.port.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 导入处理器。
///
/// **职责**：
///
/// - 编排 PubMed Computed Authors 导入流程
/// - 委派具体任务给领域端口
///
/// **临时文件下载特性**：
///
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录
/// - 传递 downloadUrl 给 Job，由 ItemReader 负责下载到临时文件
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - JSON Lines 格式（每行一个 JSON 对象）
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
public class AuthorImportHandler
    implements CommandHandler<AuthorImportCommand, AuthorImportResult> {

  private final AuthorRepository authorRepository;
  private final AuthorBatchPort authorBatchPort;

  /// 导入 PubMed Computed Authors。
  ///
  /// 大数据量（2100 万+ 条），使用批处理进行导入。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何作者数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令（包含文件 URL）
  /// @return 导入结果
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  public AuthorImportResult handle(AuthorImportCommand command) {
    log.info("启动 PubMed Computed Authors 导入，URL：{}", command.url());

    // 1. 检查数据是否已存在
    if (authorRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("Author");
    }

    // 2. 启动批处理导入（传递 downloadUrl，由 ItemReader 负责下载到临时文件）
    try {
      AuthorImportParams params = AuthorImportParams.withDownloadUrl(command.url());
      Long executionId = authorBatchPort.launchAuthorImport(params);

      log.info("PubMed Computed Authors 导入任务已启动，executionId：{}", executionId);
      return AuthorImportResult.success(executionId, command.url());

    } catch (Exception e) {
      // ApplicationException 直接透传，其他异常包装为 ApplicationException
      if (e instanceof ApplicationException) {
        throw e;
      }
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "PubMed Computed Authors 导入失败: " + e.getMessage(), e);
    }
    // 无需清理临时文件，ItemReader 在 close() 时自动关闭 HTTP 连接
  }
}
