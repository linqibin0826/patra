package com.patra.starter.provenance.pubmed.model.request;

import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.RetMode;
import com.patra.common.provenance.api.values.pubmed.RetType;
import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * PubMed EFetch API 请求参数,遵循 E-utilities 官方规范。
 *
 * <p>EFetch 用于根据 PubMed ID 列表检索文章详细信息。支持两种使用模式:
 *
 * <ul>
 *   <li><b>直接ID模式</b>: 提供逗号分隔的 PMID 列表 (最多200个)
 *   <li><b>历史服务器模式</b>: 使用 ESearch/EPost 返回的 WebEnv + query_key
 * </ul>
 *
 * <p>字段说明:
 *
 * @param db 数据库标识符 (本 starter 固定为 "pubmed")
 * @param id 逗号分隔的文章标识符列表 (直接ID模式必填,最多200个)
 * @param retmode 响应格式 (xml 或 text,默认 xml)
 * @param rettype 响应类型 (abstract/medline/full/uilist,默认 abstract)
 * @param retstart 起始偏移量 (仅用于历史服务器分页)
 * @param retmax 单次返回的最大记录数 (默认20,最大10000)
 * @param webenv WebEnv 令牌 (从 ESearch/EPost 获取)
 * @param queryKey 查询键 (与 WebEnv 配合使用)
 * @param apiKey API 密钥 (可提升请求配额: 3次/秒 → 10次/秒)
 * @param tool 工具标识符 (向 NCBI 注册的应用名称,如 "patra")
 * @param email 维护者邮箱 (用于 NCBI 运营联系)
 *     <p><b>使用建议:</b>
 *     <ul>
 *       <li>使用 XML 默认格式获取详细文章结构
 *       <li>使用 {@link #forUiList(String, String)} 获取轻量级标识符列表
 *       <li>超过200个ID时,应先使用 EPost 上传到历史服务器
 *     </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record EFetchRequest(
    // 必填参数
    String db, // 数据库名称 (例如: "pubmed")
    String id, // 文章ID列表 (逗号分隔,最多200个)

    // 可选参数 - 基本控制
    String retmode, // 返回模式 (xml/text,默认 xml)
    String rettype, // 返回类型 (abstract/medline/full/uilist,默认 abstract)
    Integer retstart, // 起始位置 (仅用于历史查询)
    Integer retmax, // 返回数量 (默认20,最大10000)

    // 可选参数 - 历史服务器和会话
    String webenv, // Web 环境字符串 (WebEnv)
    String queryKey, // 查询键

    // 可选参数 - 认证和标识 (重要)
    String apiKey, // API 密钥 (提升速率限制: 3次/秒 → 10次/秒)
    String tool, // 工具名称 (标识应用程序,例如: "patra")
    String email // 联系邮箱 (NCBI 可联系开发者)
    ) implements ApiRequest {

  /**
   * 创建用于通过 XML 检索摘要的请求。
   *
   * @param db 数据库标识符,通常为 "pubmed"
   * @param id 逗号分隔的 PubMed 标识符列表 (每次最多200个)
   */
  public EFetchRequest(String db, String id) {
    this(db, id, RetMode.XML.value(), RetType.ABSTRACT.value(), null, null, null, null, null, null, null);
  }

  /**
   * 创建用于以纯文本格式检索标识符列表的请求。
   *
   * @param db 数据库标识符,通常为 "pubmed"
   * @param id 逗号分隔的 PubMed 标识符列表 (每次最多200个)
   * @return 配置为返回 uilist 纯文本负载的请求
   */
  public static EFetchRequest forUiList(String db, String id) {
    return new EFetchRequest(db, id, RetMode.TEXT.value(), RetType.UILIST.value(), null, null, null, null, null, null, null);
  }

  // Compact constructor: validate required parameters
  public EFetchRequest {
    db = db != null ? db.trim() : null;
    if (!StringUtils.hasText(db)) {
      throw new IllegalArgumentException("db cannot be null or blank");
    }

    id = id != null ? id.trim() : null;
    webenv = webenv != null ? webenv.trim() : null;
    queryKey = queryKey != null ? queryKey.trim() : null;
    boolean hasId = StringUtils.hasText(id);
    boolean hasHistory = StringUtils.hasText(webenv) && StringUtils.hasText(queryKey);
    if (!hasId && !hasHistory) {
      throw new IllegalArgumentException("Either id or (WebEnv + queryKey) must be provided");
    }
    if (!hasId) {
      id = "";
    }

    // Default to XML format (since most rettype only support XML)
    retmode = retmode != null ? retmode.trim() : null;
    if (!StringUtils.hasText(retmode)) {
      retmode = RetMode.XML.value();
    }
    rettype = rettype != null ? rettype.trim() : null;
    if (!StringUtils.hasText(rettype)) {
      rettype = RetType.ABSTRACT.value();
    }
  }

  /**
   * 组装 EFetch 端点可识别的出站查询参数映射。
   *
   * @return 准备好提交给网关的参数映射
   */
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(PubMedParamKeys.DB, db);
    if (StringUtils.hasText(id)) {
      params.put(PubMedParamKeys.ID, id);
    }

    // Basic control
    params.put(PubMedParamKeys.RETMODE, retmode != null ? retmode : RetMode.XML.value());
    params.put(PubMedParamKeys.RETTYPE, rettype != null ? rettype : RetType.ABSTRACT.value());
    if (retstart != null) params.put(PubMedParamKeys.RETSTART, retstart.toString());
    if (retmax != null) params.put(PubMedParamKeys.RETMAX, retmax.toString());

    // History and session
    if (webenv != null) params.put(PubMedParamKeys.WEBENV, webenv);
    if (queryKey != null) params.put(PubMedParamKeys.QUERY_KEY, queryKey);

    // Authentication and identification
    if (apiKey != null) params.put(PubMedParamKeys.API_KEY, apiKey);
    if (tool != null) params.put(PubMedParamKeys.TOOL, tool);
    if (email != null) params.put(PubMedParamKeys.EMAIL, email);

    return params;
  }
}
