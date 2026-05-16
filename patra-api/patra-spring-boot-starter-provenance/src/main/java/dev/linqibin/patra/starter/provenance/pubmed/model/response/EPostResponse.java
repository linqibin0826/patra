package dev.linqibin.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.util.StringUtils;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/// PubMed EPost API 响应的结构化视图 (仅支持 XML 格式)。
///
/// EPost 将 UID 列表上传到 Entrez 历史服务器,并返回 WebEnv 令牌和 query_key, 这些令牌可在后续 API 调用中使用。这避免了处理大型 ID 列表时的
/// URL 长度限制。
///
/// **典型响应字段:**
///
/// - **WebEnv**: 历史服务器会话令牌 (有效期约24小时)
///   - **QueryKey**: 标识上传的 UID 列表的查询键
///   - **Count**: 成功上传的标识符数量
///
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "ePostResult")
public final class EPostResponse {

  @JacksonXmlProperty(localName = "WebEnv")
  private String webEnv;

  @JacksonXmlProperty(localName = "QueryKey")
  private String queryKey;

  @JacksonXmlProperty(localName = "Count")
  private Integer count;

  public EPostResponse() {}

  /// 历史服务器会话令牌 (有效期约24小时)。
  public String webEnv() {
    return webEnv;
  }

  /// 标识 WebEnv 会话中上传的 UID 列表的数字键。
  public String queryKey() {
    return queryKey;
  }

  /// 成功上传的标识符数量 (可能为 `null`)。
  public Integer count() {
    return count;
  }

  /// 验证响应是否包含可用的 WebEnv 和 QueryKey。
  public boolean isValid() {
    return StringUtils.hasText(webEnv) && StringUtils.hasText(queryKey);
  }

  /// 用于日志记录的安全 WebEnv 片段,不泄露完整令牌。
  public String getTruncatedWebEnv() {
    if (!StringUtils.hasText(webEnv)) {
      return "null";
    }
    return webEnv.length() > 12 ? webEnv.substring(0, 12) + "..." : webEnv;
  }
}
