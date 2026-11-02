package dev.patra.catalog.app.publication.usecase.query;

import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import dev.patra.catalog.domain.publication.port.read.PublicationNeighborReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 最新收录与同主题邻居 CQRS 查询服务（给 analytics 的热读补位与「看过还看了」补位）。不开事务。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PublicationNeighborQueryService {

  private final PublicationNeighborReadPort readPort;

  /// 最新收录的可用文献。
  ///
  /// @param limit 条数（调用方已校验 1 ~ 100）
  /// @return 文献主键，收录时间新的在前
  public List<Long> latest(int limit) {
    return readPort.findLatestIds(limit);
  }

  /// 同主题邻居。
  ///
  /// @param publicationId 文献主键
  /// @param limit 条数（调用方已校验 1 ~ 20）
  /// @return 邻居，按展示顺序
  public List<SubjectNeighborReadModel> subjectNeighbors(long publicationId, int limit) {
    return readPort.findSubjectNeighbors(publicationId, limit);
  }
}
