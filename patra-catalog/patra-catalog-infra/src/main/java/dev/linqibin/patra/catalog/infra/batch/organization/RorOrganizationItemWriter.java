package dev.linqibin.patra.catalog.infra.batch.organization;

import dev.linqibin.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import dev.linqibin.patra.catalog.domain.port.repository.OrganizationRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/// ROR 机构批量写入器（纯 INSERT 策略）。
///
/// **职责**：
///
/// - 将 OrganizationAggregate 批量持久化
/// - 纯 INSERT 策略：所有记录作为新记录插入
/// - 子表数据（Name、ExternalId、Relation、Location）由 Repository 统一处理
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
/// 持久化逻辑统一委托给 OrganizationRepository，遵循六边形架构原则：
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
public class RorOrganizationItemWriter implements ItemWriter<OrganizationAggregate> {

  private final OrganizationRepository organizationRepository;

  /// 批量写入机构聚合。
  ///
  /// @param chunk 待写入的机构聚合批次
  /// @throws Exception 写入失败时抛出异常
  @Override
  public void write(Chunk<? extends OrganizationAggregate> chunk) throws Exception {
    List<? extends OrganizationAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    log.debug("开始写入 {} 条 Organization 记录", items.size());
    organizationRepository.insertAll(new ArrayList<>(items));
    log.debug("写入完成：新增={}", items.size());
  }
}
