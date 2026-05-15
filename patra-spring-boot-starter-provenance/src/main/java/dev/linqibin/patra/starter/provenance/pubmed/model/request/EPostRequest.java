package dev.linqibin.patra.starter.provenance.pubmed.model.request;

import dev.linqibin.patra.starter.provenance.common.gateway.ApiRequest;
import dev.linqibin.patra.common.provenance.api.params.PubMedParamKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/// PubMed EPost API 请求参数,用于将 ID 列表上传到历史服务器。
///
/// EPost 用于将 UID 列表上传到 Entrez 历史服务器,服务器返回 WebEnv 和 query_key, 这些令牌可在后续的 EFetch 或其他 E-utility
/// 调用中使用。这是 NCBI 推荐的处理大型 ID 列表 (>200个) 的方法。
///
/// **典型使用场景:**
///
/// 字段说明:
///
/// @param db 数据库标识符 (本 starter 固定为 "pubmed")
/// @param id 逗号分隔的 PubMed 标识符列表 (支持大批量,建议 <10000)
/// @param apiKey API 密钥 (可提升速率限制: 3次/秒 → 10次/秒,可选)
/// @param tool 客户端标识符 (向 NCBI 注册的工具名称,可选)
/// @param email 联系邮箱 (用于 NCBI 通知,可选)
///
/// **NCBI 最佳实践:** 获取超过200条记录时使用 EPost,以避免 URL 长度限制。 返回的 WebEnv/query_key 可在多次 EFetch
///     调用中重复使用。
///
/// 参考文档: <a href="https://www.ncbi.nlm.nih.gov/books/NBK25499/">E-utilities 深度指南</a>
/// @author linqibin
/// @since 0.1.0
public record EPostRequest(
    // 必填参数
    String db, // 数据库名称 (例如: "pubmed")
    String id, // 逗号分隔的 PMID 列表 (无硬性限制,但建议 <10000)

    // 可选参数 - 认证和标识
    String apiKey, // API 密钥 (提升速率限制: 3次/秒 → 10次/秒)
    String tool, // 工具名称 (标识应用程序,例如: "patra")
    String email // 联系邮箱 (NCBI 可联系开发者)
    ) implements ApiRequest {

  /// 创建仅包含数据库和 ID 列表的最小 EPost 请求。
  ///
  /// @param db 数据库标识符,通常为 "pubmed"
  /// @param id 逗号分隔的 PubMed 标识符列表
  public EPostRequest(String db, String id) {
    this(db, id, null, null, null);
  }

  // Compact constructor: validate required parameters
  public EPostRequest {
    if (db == null || db.isBlank()) {
      throw new IllegalArgumentException("db cannot be null or blank");
    }
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id cannot be null or blank");
    }
  }

  /// 组装 EPost 端点可识别的出站查询参数映射。
  ///
  /// @return 准备好提交给网关的参数映射
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(PubMedParamKeys.DB, db);
    params.put(PubMedParamKeys.ID, id);

    // Authentication and identification
    if (apiKey != null) params.put(PubMedParamKeys.API_KEY, apiKey);
    if (tool != null) params.put(PubMedParamKeys.TOOL, tool);
    if (email != null) params.put(PubMedParamKeys.EMAIL, email);

    return params;
  }

  /// 获取 ID 列表中的标识符数量 (用于日志记录/调试)。
  ///
  /// @return 逗号分隔的标识符数量
  public int getIdCount() {
    if (id == null || id.isBlank()) {
      return 0;
    }
    return id.split(",").length;
  }
}
