package dev.linqibin.patra.catalog.infra.batch.author;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import dev.linqibin.patra.catalog.domain.model.aggregate.AuthorAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import dev.linqibin.patra.catalog.domain.model.vo.author.Orcid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/// PubMed Computed Authors JSON Lines 解析器。
///
/// 从 JSON Lines 文件解析 PubMed Computed Authors 数据，
/// 并转换为 `AuthorAggregate`。
///
/// **数据格式**：
///
/// ```json
/// {"name": "MAKAR+A", "names": ["Makar,Artur,A"], "orcid": ["0000-0001-7121-5322"], "pmids":
// [32708434]}
/// ```
///
/// **转换规则**：
///
/// - `name` → `normalizedKey`（业务键）
/// - `names` → 解析为 `AuthorNameVariant` 列表
/// - `orcid` → 转换为 `Orcid` 列表
/// - `pmids` → 暂不处理（未来可用于关联 Article）
///
/// **错误处理**：
///
/// - 解析失败的行输出 WARN 日志并跳过
/// - 不终止整体解析流程
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class PubMedComputedAuthorParser {

  /// ICU4J Collator 实例（线程安全，可复用）。
  ///
  /// 配置为 PRIMARY 强度，匹配 MySQL `utf8mb4_0900_ai_ci` 的行为：
  /// - 忽略重音差异："García" = "Garcia"
  /// - 忽略大小写："Tephly" = "TEPHLY"
  /// - 德语 ß = ss："Müller" = "Mueller"
  private static final Collator UNICODE_CI_COLLATOR;

  static {
    // 使用 ROOT locale 获取语言无关的 Unicode Collation Algorithm
    UNICODE_CI_COLLATOR = Collator.getInstance(ULocale.ROOT);
    // PRIMARY 强度：忽略重音和大小写（等价于 MySQL 的 _ci 和 _ai）
    UNICODE_CI_COLLATOR.setStrength(Collator.PRIMARY);
    // 冻结以保证线程安全
    UNICODE_CI_COLLATOR.freeze();
  }

  private final ObjectMapper objectMapper;

  /// 创建解析器实例。
  ///
  /// **ObjectMapper 配置**：
  ///
  /// 使用 `rebuild()` 创建派生配置，
  /// 并禁用 `FAIL_ON_UNKNOWN_PROPERTIES` 以容忍未知字段。
  ///
  /// @param objectMapper Jackson ObjectMapper（由 Spring 自动注入）
  public PubMedComputedAuthorParser(ObjectMapper objectMapper) {
    this.objectMapper =
        objectMapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  /// 解析 JSON Lines 输入流。
  ///
  /// **资源管理**：返回的 Stream 注册了 `onClose()` 钩子，
  /// 调用 `stream.close()` 时会自动关闭底层的 `BufferedReader`。
  ///
  /// @param inputStream JSON Lines 输入流
  /// @return AuthorAggregate 流（使用完毕后需调用 close()）
  /// @throws IOException 读取或解析失败时
  public Stream<AuthorAggregate> parse(InputStream inputStream) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    return reader
        .lines()
        .filter(line -> !line.isBlank())
        .map(this::parseLine)
        .filter(Objects::nonNull)
        .onClose(
            () -> {
              try {
                reader.close();
              } catch (IOException e) {
                log.warn("关闭 BufferedReader 时发生异常", e);
              }
            });
  }

  /// 解析单行 JSON。
  ///
  /// @param line JSON 行
  /// @return AuthorAggregate 或 null（解析失败时）
  private AuthorAggregate parseLine(String line) {
    try {
      PubMedComputedAuthorDto dto = objectMapper.readValue(line, PubMedComputedAuthorDto.class);
      return toAggregate(dto);
    } catch (Exception e) {
      log.warn(
          "解析 JSON 行失败，跳过：{}...，原因：{}",
          line.length() > 100 ? line.substring(0, 100) : line,
          e.getMessage());
      return null;
    }
  }

  /// 转换 DTO 为聚合根。
  ///
  /// @param dto 解析的 DTO
  /// @return AuthorAggregate
  private AuthorAggregate toAggregate(PubMedComputedAuthorDto dto) {
    // 1. 创建聚合根
    AuthorAggregate author = AuthorAggregate.fromPubMedComputed(dto.name());

    // 2. 解析并设置名字变体（使用 ICU4J Collator 去重，精确匹配 MySQL utf8mb4_0900_ai_ci）
    if (dto.names() != null && !dto.names().isEmpty()) {
      // 使用 LinkedHashMap 保持插入顺序，按 collation key 去重
      LinkedHashMap<String, AuthorNameVariant> uniqueVariants = new LinkedHashMap<>();
      for (String name : dto.names()) {
        AuthorNameVariant variant = parseNameVariant(name);
        if (variant != null) {
          // 使用 collation key 作为去重依据（精确匹配 MySQL utf8mb4_0900_ai_ci 行为）
          String key = toCollationKey(variant.fullString());
          uniqueVariants.putIfAbsent(key, variant);
        }
      }
      author.withNameVariants(uniqueVariants.values().stream().toList());
    }

    // 3. 设置 ORCID
    if (dto.orcid() != null && !dto.orcid().isEmpty()) {
      List<Orcid> orcids =
          dto.orcid().stream()
              .filter(s -> s != null && !s.isBlank())
              .map(this::parseOrcid)
              .filter(Objects::nonNull)
              .toList();
      author.withOrcids(orcids);
    }

    return author;
  }

  /// 解析名字变体字符串。
  ///
  /// 格式：`LastName,FirstName,Initials`（如 `Makar,Artur,A`）
  ///
  /// @param nameStr 名字字符串
  /// @return AuthorNameVariant 或 null（解析失败时）
  private AuthorNameVariant parseNameVariant(String nameStr) {
    if (nameStr == null || nameStr.isBlank()) {
      return null;
    }

    try {
      // 使用 AuthorNameVariant.parse() 方法，它已经处理了各种格式
      return AuthorNameVariant.parse(nameStr);
    } catch (Exception e) {
      log.debug("解析名字变体失败：{}，原因：{}", nameStr, e.getMessage());
      return null;
    }
  }

  /// 解析 ORCID 字符串。
  ///
  /// @param orcidStr ORCID 字符串
  /// @return Orcid 或 null（格式无效时）
  private Orcid parseOrcid(String orcidStr) {
    try {
      return Orcid.of(orcidStr);
    } catch (Exception e) {
      log.debug("解析 ORCID 失败：{}，原因：{}", orcidStr, e.getMessage());
      return null;
    }
  }

  /// 生成 MySQL utf8mb4_0900_ai_ci 兼容的 Collation Key。
  ///
  /// 使用 ICU4J 实现完整的 Unicode Collation Algorithm，自动处理：
  /// - 重音差异（西班牙语 García/Garcia、法语 François/Francois 等）
  /// - 德语 ß/ss 等价（Müller/Mueller）
  /// - 大小写不敏感（Tephly/TEPHLY）
  /// - 其他所有 UCA 定义的等价规则
  ///
  /// @param value 原始字符串
  /// @return 十六进制格式的 collation key
  private String toCollationKey(String value) {
    byte[] keyBytes = UNICODE_CI_COLLATOR.getCollationKey(value).toByteArray();
    StringBuilder hex = new StringBuilder(keyBytes.length * 2);
    for (byte b : keyBytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  /// PubMed Computed Author DTO（内部使用）。
  ///
  /// @param name 规范化标识（如 "Lu+Z"）
  /// @param names 名字变体列表
  /// @param orcid ORCID 列表
  /// @param pmids 关联的 PMID 列表（暂不处理）
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PubMedComputedAuthorDto(
      String name, List<String> names, List<String> orcid, List<Integer> pmids) {}
}
