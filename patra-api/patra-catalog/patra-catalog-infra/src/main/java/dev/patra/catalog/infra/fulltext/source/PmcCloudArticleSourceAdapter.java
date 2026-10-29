package dev.patra.catalog.infra.fulltext.source;

import dev.patra.catalog.domain.fulltext.exception.PmcSourceException;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.port.source.PmcArticleSourcePort;
import dev.patra.catalog.infra.kernel.support.SecureXmlInputFactory;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// PMC Cloud Service（公开 S3 桶，HTTPS 匿名访问）的适配器，不引入 AWS SDK。
///
/// - 列版本：ListObjectsV2（`prefix={PMCID}.&delimiter=/`）返回的 `CommonPrefixes`；
/// - 元数据：`{prefix}/{prefix}.json`，响应 `Content-Type` 是 `binary/octet-stream`，
///   所以先按字符串取回再用 `JsonMapper` 读；`media_urls`、`pdf_url` 可能整个键不存在。
///
/// 由 boot 的 `PmcConfiguration` 创建（照 OpenAlex 客户端的装配方式）。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
public class PmcCloudArticleSourceAdapter implements PmcArticleSourcePort {

  private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

  private final RestClient restClient;
  private final JsonMapper jsonMapper;
  private final String baseUrl;

  /// 创建适配器。
  ///
  /// @param restClient 以桶地址为 baseUrl 的客户端
  /// @param jsonMapper JSON 读取器
  /// @param baseUrl 桶地址，拼文件下载地址用
  public PmcCloudArticleSourceAdapter(
      RestClient restClient, JsonMapper jsonMapper, String baseUrl) {
    this.restClient = restClient;
    this.jsonMapper = jsonMapper;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
  }

  /// 列出一篇文章在桶里的全部版本前缀。
  ///
  /// @param pmcid PMCID
  /// @return 版本前缀，不在桶里返回空列表
  @Override
  public List<String> listVersionPrefixes(String pmcid) {
    String xml;
    try {
      xml =
          restClient
              .get()
              .uri(
                  b ->
                      b.path("/")
                          .queryParam("list-type", "2")
                          .queryParam("prefix", pmcid + ".")
                          .queryParam("delimiter", "/")
                          .build())
              .retrieve()
              .body(String.class);
    } catch (RestClientException e) {
      throw new PmcSourceException("列出 PMC 版本失败：" + pmcid, e);
    }
    if (xml == null) {
      throw new PmcSourceException("列出 PMC 版本返回空内容：" + pmcid);
    }
    try {
      return parseCommonPrefixes(xml);
    } catch (XMLStreamException e) {
      throw new PmcSourceException("PMC 版本列表不是合法 XML：" + pmcid, e);
    }
  }

  /// 读一个版本的元数据。
  ///
  /// @param versionPrefix 版本前缀
  /// @return 元数据
  @Override
  public PmcArticleMetadata getMetadata(String versionPrefix) {
    String json;
    try {
      json =
          restClient
              .get()
              .uri("/{prefix}/{prefix}.json", versionPrefix, versionPrefix)
              .retrieve()
              .body(String.class);
    } catch (RestClientException e) {
      throw new PmcSourceException("读取 PMC 元数据失败：" + versionPrefix, e);
    }
    try {
      JsonNode root = jsonMapper.readTree(json);
      List<PmcArticleFile> media = new ArrayList<>();
      for (JsonNode url : root.path("media_urls")) {
        PmcArticleFile file = parseS3Url(url.asString());
        if (isImage(file.fileName())) {
          media.add(file);
        }
      }
      return PmcArticleMetadata.builder()
          .pmcid(root.path("pmcid").asString())
          .versionPrefix(versionPrefix)
          .openAccess(root.path("is_pmc_openaccess").asBoolean(false))
          .manuscript(root.path("is_manuscript").asBoolean(false))
          .retracted(root.path("is_retracted").asBoolean(false))
          .licenseCode(textOrNull(root.path("license_code")))
          .xml(parseS3Url(root.path("xml_url").asString()))
          .pdf(root.hasNonNull("pdf_url") ? parseS3Url(root.path("pdf_url").asString()) : null)
          .media(media)
          .build();
    } catch (JacksonException | IllegalArgumentException e) {
      throw new PmcSourceException("PMC 元数据不合法：" + versionPrefix, e);
    }
  }

  /// 拼出版本目录下某个文件的 HTTPS 地址。
  ///
  /// @param versionPrefix 版本前缀
  /// @param fileName 文件名
  /// @return 下载地址
  @Override
  public URI fileUri(String versionPrefix, String fileName) {
    return UriComponentsBuilder.fromUriString(baseUrl)
        .pathSegment(versionPrefix, fileName)
        .encode()
        .build()
        .toUri();
  }

  /// 只读 `CommonPrefixes` 下的 `Prefix`（顶层也有一个 `Prefix`），按 local name 匹配并去掉尾部 `/`。
  private static List<String> parseCommonPrefixes(String xml) throws XMLStreamException {
    List<String> prefixes = new ArrayList<>();
    XMLStreamReader reader =
        SecureXmlInputFactory.getInstance().createXMLStreamReader(new StringReader(xml));
    try {
      boolean inCommonPrefixes = false;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String name = reader.getLocalName();
          if ("CommonPrefixes".equals(name)) {
            inCommonPrefixes = true;
          } else if (inCommonPrefixes && "Prefix".equals(name)) {
            String prefix = reader.getElementText().trim();
            prefixes.add(prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "CommonPrefixes".equals(reader.getLocalName())) {
          inCommonPrefixes = false;
        }
      }
    } finally {
      reader.close();
    }
    return prefixes;
  }

  /// 解析 `s3://pmc-oa-opendata/<prefix>/<file>?md5=<md5>`。
  private static PmcArticleFile parseS3Url(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("缺少文件地址");
    }
    int query = url.indexOf('?');
    String path = query >= 0 ? url.substring(0, query) : url;
    String md5 = null;
    if (query >= 0) {
      for (String param : url.substring(query + 1).split("&")) {
        if (param.startsWith("md5=")) {
          md5 = param.substring(4);
        }
      }
    }
    return PmcArticleFile.of(path.substring(path.lastIndexOf('/') + 1), md5);
  }

  /// 按扩展名判断是否正文图片。
  private static boolean isImage(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot >= 0
        && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  /// 字符串字段，缺失或为 null 时返回 `null`。
  private static String textOrNull(JsonNode node) {
    return node.isMissingNode() || node.isNull() ? null : node.asString();
  }
}
