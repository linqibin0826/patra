package com.patra.catalog.infra.adapter.batch.author;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.port.repository.AuthorRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 批量写入器（纯 INSERT 策略）。
///
/// **职责**：
///
/// - 将 AuthorAggregate 批量持久化
/// - 纯 INSERT 策略：所有记录作为新记录插入
/// - 子实体数据（NameVariant、Orcid）由 Repository 统一处理
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持 Upsert（更新已存在记录）
/// - 如果存在主键冲突，批处理会失败
/// - 数据库应该在导入前为空表状态
///
/// **架构改进**：
///
/// 持久化逻辑统一委托给 AuthorRepository，遵循六边形架构原则：
///
/// - ItemWriter 只负责协调批处理流程
/// - 聚合持久化逻辑集中在 Repository
/// - 依赖倒置：依赖 Domain 层接口而非 Infra 层 Mapper
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorItemWriter implements ItemWriter<AuthorAggregate> {

  private final AuthorRepository authorRepository;

  @Override
  public void write(Chunk<? extends AuthorAggregate> chunk) throws Exception {
    List<? extends AuthorAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    log.debug("开始写入 {} 条 Author 记录", items.size());
    authorRepository.saveBatch(new ArrayList<>(items));
    log.debug("写入完成：新增={}", items.size());
  }
}
