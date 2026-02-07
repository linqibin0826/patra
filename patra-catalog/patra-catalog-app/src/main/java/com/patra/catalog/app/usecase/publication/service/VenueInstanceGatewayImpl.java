package com.patra.catalog.app.usecase.publication.service;

import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.repository.VenueInstanceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
/// - 使用 `REQUIRES_NEW` 事务传播，确保实例创建的独立性
/// - 支持并发创建场景（通过唯一约束 + 重试机制）
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
  /// 否则创建新实例并保存。
  ///
  /// **并发处理**：
  ///
  /// 使用 `REQUIRES_NEW` 事务隔离并发创建。如果发生唯一约束冲突，
  /// 会重新查询返回已存在的实例。
  ///
  /// @param params 期刊实例参数（包含 venueId、volume、issue、出版日期等）
  /// @return 期刊实例聚合根（永不为 null）
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public VenueInstanceAggregate findOrCreateJournalInstance(JournalInstanceParams params) {
    VenueId venueId = params.getVenueId();
    String volume = params.getVolume();
    String issue = params.getIssue();
    Integer publicationYear = params.getPublicationYear();

    // 1. 先尝试查找已存在的实例
    Optional<VenueInstanceAggregate> existing =
        venueInstanceRepository.findJournalInstance(
            venueId.value(), volume, issue, publicationYear);

    if (existing.isPresent()) {
      log.debug(
          "找到已存在的 JournalInstance: venueId={}, volume={}, issue={}, year={}",
          venueId.value(),
          volume,
          issue,
          publicationYear);
      return existing.get();
    }

    // 2. 不存在则创建新实例
    VenueInstanceAggregate newInstance =
        VenueInstanceAggregate.forJournal(
            venueId,
            volume,
            issue,
            publicationYear,
            params.getPublicationMonth(),
            params.getPublicationDay());

    try {
      venueInstanceRepository.save(newInstance);
      log.debug(
          "创建新 JournalInstance: venueId={}, volume={}, issue={}, year={}",
          venueId.value(),
          volume,
          issue,
          publicationYear);
      return newInstance;
    } catch (DataIntegrityViolationException e) {
      // 3. 并发创建时唯一约束冲突，重新查询已存在的实例
      log.debug("JournalInstance 创建冲突（唯一约束），尝试重新查询: {}", e.getMessage());
      return venueInstanceRepository
          .findJournalInstance(venueId.value(), volume, issue, publicationYear)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "无法创建或找到 JournalInstance: venueId=%d, volume=%s, issue=%s, year=%d"
                          .formatted(venueId.value(), volume, issue, publicationYear),
                      e));
    }
  }
}
