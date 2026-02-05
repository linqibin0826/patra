package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Publication 批量写入器。
///
/// **职责**：
///
/// - 将 Processor 处理后的 PublicationImportResult 转换为 Domain 层类型
/// - 委托 PublicationRepository 批量写入数据库
///
/// **六边形架构改进**：
///
/// 重构前：Writer 直接依赖 15+ 个 DAO，违反了六边形架构原则
/// 重构后：Writer 仅依赖 PublicationRepository 和 Mapper，职责清晰
///
/// **写入流程**：
///
/// 1. 使用 Mapper 将 PublicationImportResult 转换为 PublicationCompleteData
/// 2. 委托 Repository 批量写入主数据和所有关联数据
///
/// **性能优化**：
///
/// - 批量插入减少数据库往返次数
/// - chunk size 由 Job 配置决定（推荐 500）
///
/// **错误处理**：
///
/// 由 Spring Batch FaultTolerant 机制处理：
/// - 单条失败时跳过该记录
/// - 批量失败时回退到逐条处理
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class PublicationItemWriter implements ItemWriter<PublicationImportResult> {

  private final PublicationRepository publicationRepository;
  private final PublicationImportResultMapper resultMapper;

  @Override
  public void write(Chunk<? extends PublicationImportResult> chunk) throws Exception {
    if (chunk.isEmpty()) {
      return;
    }

    // 转换为 Domain 层类型
    List<PublicationCompleteData> data =
        chunk.getItems().stream().map(resultMapper::toCompleteData).toList();

    // 委托给 Repository
    publicationRepository.insertAllWithAssociations(data);

    log.debug("批量写入 {} 条 Publication（含关联数据）", data.size());
  }
}
