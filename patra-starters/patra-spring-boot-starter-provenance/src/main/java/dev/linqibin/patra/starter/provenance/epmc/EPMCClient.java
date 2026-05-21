package dev.linqibin.patra.starter.provenance.epmc;

import dev.linqibin.patra.starter.provenance.common.config.ProvenanceConfig;
import dev.linqibin.patra.starter.provenance.common.exception.ProvenanceClientException;
import dev.linqibin.patra.starter.provenance.epmc.model.request.SearchRequest;
import dev.linqibin.patra.starter.provenance.epmc.model.response.SearchResponse;

/// Europe PMC (EPMC) 客户端接口
///
/// 提供调用 Europe PMC API 的方法,用于欧洲生命科学出版物检索。
///
/// **主要功能:**
///
/// - 支持全文检索和结构化查询
///   - 提供丰富的元数据和全文链接
///   - 包含开放获取标识
///
/// @author linqibin
/// @since 0.1.0
public interface EPMCClient {

  /// 调用 Europe PMC 搜索 API 进行文献发现
  ///
  /// @param request 搜索请求参数
  /// @return 搜索响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  SearchResponse search(SearchRequest request);

  /// 使用调用方提供的配置覆盖调用 Europe PMC 搜索 API
  ///
  /// @param request 搜索请求参数
  /// @param config 配置覆盖(可选)
  /// @return 搜索响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  SearchResponse search(SearchRequest request, ProvenanceConfig config);
}
