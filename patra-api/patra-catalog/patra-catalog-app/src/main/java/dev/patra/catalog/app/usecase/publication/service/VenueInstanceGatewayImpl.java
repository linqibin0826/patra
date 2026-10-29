package dev.patra.catalog.app.usecase.publication.service;

import dev.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.model.vo.venue.VenueId;
import dev.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import dev.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import dev.patra.catalog.domain.port.repository.VenueInstanceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// VenueInstance 便捷服务。
///
/// 提供 VenueInstance 的 findOrCreate 功能，用于文献导入时创建或复用载体实例。
///
/// **核心功能**：
///
/// - `findOrCreateJournalInstance()`：查找或创建期刊实例（卷期）
///
/// **设计说明**：
///
/// - 在 Processor 里调用，用 `REQUIRES_NEW` 单独提交：导入步骤是容错的，写入失败时整块 chunk
///   回滚再逐条重写，已处理的文献仍带着实例 ID；实例若跟着 chunk 回滚，这些文献就指向一个
///   不存在的实例（`venue_instance_id` 没有外键，不会报错）
/// - 并发创建同一卷期靠唯一键 + `INSERT … ON CONFLICT DO NOTHING`：插入被跳过就重新查询，
///   不靠捕获异常，所在事务不会作废
/// - 与 Processor 阶段解耦，可独立测试
///
/// **性能优化**：
///
/// 同一期刊的文献通常聚集在一起（按文件排序），因此实例创建频率低。
/// 建议在 Processor 层配合 VenueCache 使用，减少数据库查询。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class VenueInstanceGatewayImpl implements VenueInstanceGateway {

  private final VenueInstanceRepository venueInstanceRepository;

  /// 查找或创建期刊实例。
  ///
  /// 如果指定的 (venueId, volume, issue, year) 组合已存在，返回现有实例；
  /// 否则插入新实例。并发插入同一卷期时只有一方插入成功，另一方重新查询拿到它。
  ///
  /// @param params 期刊实例参数（包含 venueId、volume、issue、出版日期等）
  /// @return 期刊实例聚合根（永不为 null）
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public VenueInstanceAggregate findOrCreateJournalInstance(JournalInstanceParams params) {
    VenueId venueId = params.getVenueId();
    String volume = params.getVolume();
    String issue = params.getIssue();
    Integer publicationYear = params.getPublicationYear();

    Optional<VenueInstanceAggregate> existing =
        venueInstanceRepository.findJournalInstance(
            venueId.value(), volume, issue, publicationYear);
    if (existing.isPresent()) {
      return existing.get();
    }

    VenueInstanceAggregate newInstance =
        VenueInstanceAggregate.forJournal(
            venueId,
            volume,
            issue,
            publicationYear,
            params.getPublicationMonth(),
            params.getPublicationDay());
    if (venueInstanceRepository.insertJournalInstanceIfAbsent(newInstance)) {
      log.debug(
          "创建新 JournalInstance: venueId={}, volume={}, issue={}, year={}",
          venueId.value(),
          volume,
          issue,
          publicationYear);
      return newInstance;
    }

    log.debug(
        "JournalInstance 已被并发插入，重新查询: venueId={}, volume={}, issue={}, year={}",
        venueId.value(),
        volume,
        issue,
        publicationYear);
    return venueInstanceRepository
        .findJournalInstance(venueId.value(), volume, issue, publicationYear)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "无法创建或找到 JournalInstance: venueId=%d, volume=%s, issue=%s, year=%d"
                        .formatted(venueId.value(), volume, issue, publicationYear)));
  }
}
