package dev.patra.catalog.domain.publication.port.read;

import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;
import java.util.Collection;
import java.util.List;

/// 文献批量解析 CQRS 读端口：按内部 ID 或 PMID 批量查文献，软删的照样返回。
///
/// 只返回库里存在的行，顺序不保证；去重、按请求排序、为不存在的键补行由查询服务负责。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationResolveReadPort {

  /// 按内部 ID 批量查。
  ///
  /// @param ids 文献主键；空集合时直接返回空列表、不查库
  /// @return 存在的文献（含软删）
  List<PublicationResolveReadModel> findByIds(Collection<Long> ids);

  /// 按 PMID 批量查。
  ///
  /// @param pmids PubMed ID；空集合时直接返回空列表、不查库
  /// @return 存在的文献（含软删）
  List<PublicationResolveReadModel> findByPmids(Collection<String> pmids);
}
