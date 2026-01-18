package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Publication 批量写入器。
///
/// **职责**：
///
/// - 将 Processor 处理后的 PublicationAggregate 批量写入数据库
/// - 使用 Repository.insertAll() 进行批量插入
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
public class PublicationItemWriter implements ItemWriter<PublicationAggregate> {

  private final PublicationRepository publicationRepository;

  @Override
  public void write(Chunk<? extends PublicationAggregate> chunk) throws Exception {
    if (chunk.isEmpty()) {
      return;
    }

    // 转换为 List（Chunk 不直接兼容 List 类型参数）
    var publications = chunk.getItems().stream().map(PublicationAggregate.class::cast).toList();

    log.debug("批量写入 {} 条 Publication", publications.size());
    publicationRepository.insertAll(publications);
  }
}
