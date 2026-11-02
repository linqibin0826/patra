package dev.patra.catalog.domain.publication.port.read;

import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import java.util.List;

/// 最新收录与同主题邻居 CQRS 读端口（给 analytics 的热读补位、「看过还看了」补位）。
///
/// 「可用」的文献 = 没有软删、不是撤稿文献（发表类型 `D016441` 或全文原因 `RETRACTED`）。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationNeighborReadPort {

  /// 最新收录的可用文献：主键倒序（雪花 ID，即首次入库时间倒序）。
  ///
  /// @param limit 条数
  /// @return 文献主键
  List<Long> findLatestIds(int limit);

  /// 同主题邻居：先同主要主题词（多个词之间轮流取、词内收录时间新的在前），再同期刊；去掉自己与重复。
  ///
  /// 主要主题词按 PubMed `[majr]`：主题词本身或它的任一副主题词标了 major。
  ///
  /// @param publicationId 文献主键；不存在或已软删时返回空列表
  /// @param limit 条数
  /// @return 邻居，按展示顺序
  List<SubjectNeighborReadModel> findSubjectNeighbors(long publicationId, int limit);
}
