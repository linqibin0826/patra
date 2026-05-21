package dev.linqibin.patra.starter.provenance.pubmed;

import dev.linqibin.patra.starter.provenance.common.config.ProvenanceConfig;
import dev.linqibin.patra.starter.provenance.common.exception.ProvenanceClientException;
import dev.linqibin.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import dev.linqibin.patra.starter.provenance.pubmed.model.request.EPostRequest;
import dev.linqibin.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import dev.linqibin.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import dev.linqibin.patra.starter.provenance.pubmed.model.response.EPostResponse;
import dev.linqibin.patra.starter.provenance.pubmed.model.response.ESearchResponse;

/// PubMed 客户端接口
///
/// 提供调用 PubMed E-utilities API 的方法,支持文献搜索(ESearch)、详情获取(EFetch)和ID列表上传(EPost)。
///
/// **支持的E-utilities:**
///
/// - ESearch - 搜索PubMed数据库,返回PMID列表
///   - EFetch - 根据PMID获取出版物详细信息
///   - EPost - 上传大量ID到History Server,获取WebEnv令牌
///
/// @author linqibin
/// @since 0.1.0
public interface PubMedClient {

  /// 调用 PubMed ESearch API 检索文献标识符
  ///
  /// 默认使用 JSON 格式。
  ///
  /// @param request esearch 请求参数
  /// @return esearch 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  ESearchResponse esearch(ESearchRequest request);

  /// 使用调用方提供的配置覆盖调用 PubMed ESearch API
  ///
  /// @param request esearch 请求参数
  /// @param config 配置覆盖(可选)
  /// @return esearch 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config);

  /// 调用 PubMed EFetch API 根据标识符检索文献详情
  ///
  /// 默认使用 XML 格式以获取详细的出版物数据。
  ///
  /// @param request efetch 请求参数
  /// @return efetch 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  EFetchResponse efetch(EFetchRequest request);

  /// 使用调用方提供的配置覆盖调用 PubMed EFetch API
  ///
  /// @param request efetch 请求参数
  /// @param config 配置覆盖(可选)
  /// @return efetch 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config);

  /// 调用 PubMed EPost API 上传 ID 列表到 History Server
  ///
  /// EPost 是处理大量 ID 列表(>200 个 UID)的推荐方法。它将 UID 上传到 NCBI 的 History Server 并返回 WebEnv 令牌和
  /// query_key,可在后续的 EFetch 或其他 E-utility 调用中使用。
  ///
  /// **NCBI 最佳实践:**当需要获取超过 200 条记录时使用 EPost,以避免 URL 长度限制。
  ///
  /// @param request 包含 ID 列表的 epost 请求
  /// @return 包含 WebEnv 和 QueryKey 的 epost 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  EPostResponse epost(EPostRequest request);

  /// 使用调用方提供的配置覆盖调用 PubMed EPost API
  ///
  /// @param request 包含 ID 列表的 epost 请求
  /// @param config 配置覆盖(可选)
  /// @return 包含 WebEnv 和 QueryKey 的 epost 响应
  /// @throws ProvenanceClientException 网关报告错误或解析失败时抛出
  EPostResponse epost(EPostRequest request, ProvenanceConfig config);
}
