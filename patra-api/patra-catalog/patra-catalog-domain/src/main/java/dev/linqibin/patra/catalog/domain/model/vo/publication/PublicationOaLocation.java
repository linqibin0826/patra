package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.OaLocationType;
import dev.linqibin.patra.catalog.domain.model.enums.OaStatus;
import dev.linqibin.patra.catalog.domain.model.enums.VersionType;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

/// 文献开放获取位置值对象。
///
/// 封装文献的 OA（Open Access）位置信息，支持多位置管理和最佳位置选择。
///
/// **补充数据（聚合边界外）**：
///
/// - OA 位置通过 Repository 独立管理
/// - 一个文献可有多个 OA 位置（不同来源）
/// - 使用 `PublicationRepository.replaceOaLocationsBatch()` 批量替换
///
/// **最佳位置选择**：
///
/// 优先级排序：
/// 1. OA 状态：GOLD > GREEN > HYBRID > BRONZE
/// 2. 位置类型：PUBLISHER > PUBMED_CENTRAL > REPOSITORY > PREPRINT
/// 3. 版本类型：PUBLISHED > ACCEPTED > SUBMITTED
///
/// **唯一性约束**：
///
/// - 同一文献 + 同一 URL 只能有一条记录
///
/// 使用示例：
///
/// ```java
/// // 创建出版商黄金 OA 位置
/// PublicationOaLocation gold = PublicationOaLocation.builder()
///     .oaStatus(OaStatus.GOLD)
///     .locationType(OaLocationType.PUBLISHER)
///     .url("https://www.nature.com/articles/...")
///     .hostDomain("nature.com")
///     .versionType(VersionType.PUBLISHED)
///     .license("CC-BY-4.0")
///     .isBest(true)
///     .build();
///
/// // 创建 PMC 绿色 OA 位置
/// PublicationOaLocation pmc = PublicationOaLocation.ofPmc("PMC1234567", true);
/// ```
///
/// @param oaStatus OA 状态
/// @param locationType 位置类型
/// @param url 访问 URL
/// @param hostDomain 托管域名
/// @param repositoryName 仓库名称
/// @param repositoryId 仓库标识符
/// @param versionType 版本类型
/// @param license 许可证
/// @param availableDate 可用日期
/// @param embargoEndDate 禁发期结束日期
/// @param isBest 是否最佳位置
/// @param priority 优先级（数值越小优先级越高）
/// @param evidenceSource 证据来源（如 "Unpaywall"、"OpenAlex"）
/// @param checkedDate 链接检查时间
/// @param isActive 是否有效
/// @param pmcid PMC ID
/// @author linqibin
/// @since 0.1.0
@Builder(toBuilder = true)
public record PublicationOaLocation(
    OaStatus oaStatus,
    OaLocationType locationType,
    String url,
    String hostDomain,
    String repositoryName,
    String repositoryId,
    VersionType versionType,
    String license,
    LocalDate availableDate,
    LocalDate embargoEndDate,
    boolean isBest,
    Integer priority,
    String evidenceSource,
    Instant checkedDate,
    boolean isActive,
    String pmcid)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：设置默认值。
  public PublicationOaLocation {
    // 默认为活动状态
    // 注意：record 的紧凑构造器中不能重新赋值 boolean 基本类型的默认值
    // isActive 的默认值由 builder 处理
  }

  /// 创建 PMC 位置（绿色 OA）。
  ///
  /// @param pmcid PMC ID（如 "PMC1234567"）
  /// @param isBest 是否最佳位置
  /// @return PMC OA 位置
  public static PublicationOaLocation ofPmc(String pmcid, boolean isBest) {
    return PublicationOaLocation.builder()
        .oaStatus(OaStatus.GREEN)
        .locationType(OaLocationType.PUBMED_CENTRAL)
        .repositoryName("PubMed Central")
        .repositoryId(pmcid)
        .pmcid(pmcid)
        .url("https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmcid)
        .hostDomain("ncbi.nlm.nih.gov")
        .versionType(VersionType.ACCEPTED)
        .isBest(isBest)
        .isActive(true)
        .build();
  }

  /// 创建出版商位置（黄金 OA）。
  ///
  /// @param url 出版商 URL
  /// @param license 许可证
  /// @return 出版商 OA 位置
  public static PublicationOaLocation ofPublisher(String url, String license) {
    String domain = extractDomain(url);
    return PublicationOaLocation.builder()
        .oaStatus(OaStatus.GOLD)
        .locationType(OaLocationType.PUBLISHER)
        .url(url)
        .hostDomain(domain)
        .versionType(VersionType.PUBLISHED)
        .license(license)
        .isBest(true)
        .isActive(true)
        .build();
  }

  /// 创建预印本位置。
  ///
  /// @param url 预印本 URL
  /// @param repositoryName 预印本服务器名称（如 "arXiv"、"bioRxiv"）
  /// @return 预印本 OA 位置
  public static PublicationOaLocation ofPreprint(String url, String repositoryName) {
    String domain = extractDomain(url);
    return PublicationOaLocation.builder()
        .oaStatus(OaStatus.GREEN)
        .locationType(OaLocationType.PREPRINT)
        .url(url)
        .hostDomain(domain)
        .repositoryName(repositoryName)
        .versionType(VersionType.SUBMITTED)
        .isActive(true)
        .build();
  }

  /// 判断是否为开放获取。
  ///
  /// @return true 如果 OA 状态不是 CLOSED
  public boolean isOpenAccess() {
    return oaStatus != null && oaStatus.isOpenAccess();
  }

  /// 判断是否为官方来源。
  ///
  /// @return true 如果为出版商或 PMC
  public boolean isOfficialSource() {
    return locationType != null && locationType.isOfficialSource();
  }

  /// 判断是否为最终出版版本。
  ///
  /// @return true 如果版本类型为 PUBLISHED
  public boolean isFinalVersion() {
    return versionType != null && versionType.isFinalVersion();
  }

  /// 判断是否已通过同行评审。
  ///
  /// @return true 如果版本类型为 ACCEPTED 或 PUBLISHED
  public boolean isPeerReviewed() {
    return versionType != null && versionType.isPeerReviewed();
  }

  /// 判断是否有许可证信息。
  ///
  /// @return true 如果 license 不为空
  public boolean hasLicense() {
    return StrUtil.isNotBlank(license);
  }

  /// 判断是否有禁发期。
  ///
  /// @return true 如果设置了禁发期结束日期
  public boolean hasEmbargo() {
    return embargoEndDate != null;
  }

  /// 判断禁发期是否已结束。
  ///
  /// @return true 如果无禁发期或已过期
  public boolean isEmbargoEnded() {
    if (embargoEndDate == null) {
      return true;
    }
    return LocalDate.now().isAfter(embargoEndDate);
  }

  /// 判断是否有 PMC ID。
  ///
  /// @return true 如果 pmcid 不为空
  public boolean hasPmcid() {
    return StrUtil.isNotBlank(pmcid);
  }

  /// 判断是否有有效 URL。
  ///
  /// @return true 如果 url 不为空
  public boolean hasUrl() {
    return StrUtil.isNotBlank(url);
  }

  /// 判断优先级是否高于指定位置。
  ///
  /// 比较顺序：OA 状态 > 位置类型 > 版本类型
  ///
  /// @param other 比较的位置
  /// @return true 如果当前位置优先级更高
  public boolean isBetterThan(PublicationOaLocation other) {
    if (other == null) return true;

    // 1. 比较 OA 状态
    if (oaStatus != null && other.oaStatus != null) {
      if (oaStatus.isBetterThan(other.oaStatus)) return true;
      if (other.oaStatus.isBetterThan(oaStatus)) return false;
    }

    // 2. 比较位置类型
    if (locationType != null && other.locationType != null) {
      if (locationType.isBetterThan(other.locationType)) return true;
      if (other.locationType.isBetterThan(locationType)) return false;
    }

    // 3. 比较版本类型
    if (versionType != null && other.versionType != null) {
      return versionType.isBetterThan(other.versionType);
    }

    return false;
  }

  /// 从 URL 提取域名。
  private static String extractDomain(String url) {
    if (StrUtil.isBlank(url)) {
      return null;
    }
    try {
      String noProtocol = url.replaceFirst("^https?://", "");
      int slashIndex = noProtocol.indexOf('/');
      return slashIndex > 0 ? noProtocol.substring(0, slashIndex) : noProtocol;
    } catch (Exception e) {
      return null;
    }
  }

  /// 获取显示文本。
  ///
  /// @return 位置类型和 OA 状态
  public String toDisplayString() {
    String type = locationType != null ? locationType.getDescription() : "Unknown";
    String status = oaStatus != null ? oaStatus.getDescription() : "Unknown";
    return String.format("%s (%s)", type, status);
  }
}
