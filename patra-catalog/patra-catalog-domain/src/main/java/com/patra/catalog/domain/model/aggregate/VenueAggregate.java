package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.IssnInfo;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import lombok.Getter;

/// 出版载体聚合根。管理期刊、书籍、会议等出版载体的基本信息。
/// 
/// **一致性边界**：
/// 
/// - ISSN在期刊类型中必须唯一
///   - ISBN在书籍类型中必须唯一
///   - 载体类型确定后不应随意变更（影响关联的文献分类）
///   - 期刊必须有ISSN，书籍必须有ISBN
/// 
/// **业务规则**：
/// 
/// - 不同类型的载体有不同的必填字段验证规则
///   - JOURNAL类型：必须提供ISSN、可选ISO/MEDLINE缩写
///   - BOOK类型：必须提供ISBN
///   - CONFERENCE类型：会议信息在VenueInstance中管理
/// 
/// **设计说明**：
/// 
/// - VenueInstance（卷期/版次）不直接持有在聚合根内
///   - 通过Repository按需加载instances（避免性能问题）
///   - 卷期唯一性通过数据库唯一索引保证
/// 
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 基本信息 ==========

  /// 载体类型（JOURNAL/BOOK/CONFERENCE/OTHER）
  private final VenueType venueType;

  /// 载体名称（期刊名/书名/会议名）
  private final String title;

  // ========== 期刊专用字段 ==========

  /// ISO标准缩写（期刊专用）
  private final String isoAbbreviation;

  /// MEDLINE缩写（期刊专用）
  private final String medlineAbbreviation;

  /// ISSN信息（期刊专用）
  private final IssnInfo issnInfo;

  /// NLM唯一标识符（期刊专用）
  private final String nlmUniqueId;

  // ========== 书籍专用字段 ==========

  /// ISBN号（书籍专用，格式：978-3-16-148410-0）
  private final String isbn;

  // ========== 出版信息 ==========

  /// 出版国家（ISO 3166-1 alpha-3，如USA/CHN）
  private final String country;

  /// 出版商名称
  private final String publisher;

  // ========== 扩展字段 ==========

  /// 类型特定数据（JSON，灵活扩展）
  private String venueSpecificDataJson;

  /// 私有构造函数。
/// 
/// @param id 主键ID（新建时为null）
/// @param venueType 载体类型
/// @param title 载体名称
/// @param isoAbbreviation ISO缩写
/// @param medlineAbbreviation MEDLINE缩写
/// @param issnInfo ISSN信息
/// @param nlmUniqueId NLM唯一标识符
/// @param isbn ISBN号
/// @param country 出版国家
/// @param publisher 出版商
  private VenueAggregate(
      Long id,
      VenueType venueType,
      String title,
      String isoAbbreviation,
      String medlineAbbreviation,
      IssnInfo issnInfo,
      String nlmUniqueId,
      String isbn,
      String country,
      String publisher) {
    super(id);

    // 必填字段验证
    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(title, "载体名称不能为空");

    // 类型特定字段验证
    if (venueType.requiresIssn()) {
      Assert.notNull(issnInfo, "期刊类型必须提供ISSN信息");
    }
    if (venueType.requiresIsbn()) {
      Assert.notBlank(isbn, "书籍类型必须提供ISBN");
    }

    // ISBN格式验证（如果提供）
    if (StrUtil.isNotBlank(isbn)) {
      Assert.isTrue(
          isbn.matches("^(?:97[89]-)?\\d{1,5}-\\d{1,7}-\\d{1,7}-[\\dX]$"),
          "ISBN格式无效：%s", isbn);
    }

    // 赋值
    this.venueType = venueType;
    this.title = title;
    this.isoAbbreviation = isoAbbreviation;
    this.medlineAbbreviation = medlineAbbreviation;
    this.issnInfo = issnInfo;
    this.nlmUniqueId = nlmUniqueId;
    this.isbn = isbn;
    this.country = country;
    this.publisher = publisher;
  }

  // ========== 工厂方法 ==========

  /// 创建期刊载体。
/// 
/// @param title 期刊名称
/// @param issnInfo ISSN信息
/// @param isoAbbreviation ISO缩写
/// @param medlineAbbreviation MEDLINE缩写
/// @param nlmUniqueId NLM唯一标识符
/// @param country 出版国家
/// @param publisher 出版商
/// @return 期刊聚合根
  public static VenueAggregate createJournal(
      String title,
      IssnInfo issnInfo,
      String isoAbbreviation,
      String medlineAbbreviation,
      String nlmUniqueId,
      String country,
      String publisher) {
    return new VenueAggregate(
        null, // 新建时ID为null
        VenueType.JOURNAL,
        title,
        isoAbbreviation,
        medlineAbbreviation,
        issnInfo,
        nlmUniqueId,
        null, // ISBN仅书籍使用
        country,
        publisher);
  }

  /// 创建书籍载体。
/// 
/// @param title 书名
/// @param isbn ISBN号
/// @param country 出版国家
/// @param publisher 出版商
/// @return 书籍聚合根
  public static VenueAggregate createBook(
      String title,
      String isbn,
      String country,
      String publisher) {
    return new VenueAggregate(
        null,
        VenueType.BOOK,
        title,
        null, // ISO/MEDLINE缩写仅期刊使用
        null,
        null, // ISSN仅期刊使用
        null,
        isbn,
        country,
        publisher);
  }

  /// 创建会议载体。
/// 
/// @param title 会议系列名称
/// @param country 举办国家
/// @return 会议聚合根
  public static VenueAggregate createConference(
      String title,
      String country) {
    return new VenueAggregate(
        null,
        VenueType.CONFERENCE,
        title,
        null,
        null,
        null,
        null,
        null,
        country,
        null);
  }

  /// 创建其他类型载体。
/// 
/// @param title 名称
/// @return 其他类型聚合根
  public static VenueAggregate createOther(String title) {
    return new VenueAggregate(
        null,
        VenueType.OTHER,
        title,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /// 从持久化状态重建聚合根（由Repository使用）。
/// 
/// @param id 主键ID
/// @param venueType 载体类型
/// @param title 载体名称
/// @param isoAbbreviation ISO缩写
/// @param medlineAbbreviation MEDLINE缩写
/// @param issnInfo ISSN信息
/// @param nlmUniqueId NLM唯一标识符
/// @param isbn ISBN号
/// @param country 出版国家
/// @param publisher 出版商
/// @param version 乐观锁版本
/// @return 重建的聚合根
  public static VenueAggregate restore(
      Long id,
      VenueType venueType,
      String title,
      String isoAbbreviation,
      String medlineAbbreviation,
      IssnInfo issnInfo,
      String nlmUniqueId,
      String isbn,
      String country,
      String publisher,
      Long version) {
    VenueAggregate aggregate = new VenueAggregate(
        id,
        venueType,
        title,
        isoAbbreviation,
        medlineAbbreviation,
        issnInfo,
        nlmUniqueId,
        isbn,
        country,
        publisher);
    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 业务方法 ==========

  /// 设置特定数据JSON。
/// 
/// @param json JSON字符串
  public void setVenueSpecificDataJson(String json) {
    this.venueSpecificDataJson = json;
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否为期刊。
/// 
/// @return true 如果为期刊类型
  public boolean isJournal() {
    return venueType.isJournal();
  }

  /// 判断是否为书籍。
/// 
/// @return true 如果为书籍类型
  public boolean isBook() {
    return venueType.isBook();
  }

  /// 判断是否为会议。
/// 
/// @return true 如果为会议类型
  public boolean isConference() {
    return venueType.isConference();
  }

  /// 获取ISSN（期刊专用）。
/// 
/// @return ISSN号，如果不是期刊则返回null
  public String getIssn() {
    return issnInfo != null ? issnInfo.issn() : null;
  }

  /// 判断是否有ISO缩写。
/// 
/// @return true 如果有ISO缩写
  public boolean hasIsoAbbreviation() {
    return StrUtil.isNotBlank(isoAbbreviation);
  }

  /// 判断是否有MEDLINE缩写。
/// 
/// @return true 如果有MEDLINE缩写
  public boolean hasMedlineAbbreviation() {
    return StrUtil.isNotBlank(medlineAbbreviation);
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
/// 
/// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 载体类型不能为空
    if (venueType == null) {
      throw new IllegalStateException("载体类型不能为空");
    }

    // 名称不能为空
    if (StrUtil.isBlank(title)) {
      throw new IllegalStateException("载体名称不能为空");
    }

    // 期刊必须有ISSN
    if (venueType.requiresIssn() && issnInfo == null) {
      throw new IllegalStateException("期刊类型必须提供ISSN信息");
    }

    // 书籍必须有ISBN
    if (venueType.requiresIsbn() && StrUtil.isBlank(isbn)) {
      throw new IllegalStateException("书籍类型必须提供ISBN");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "VenueAggregate[id=%d, type=%s, title=%s, issn=%s, isbn=%s]",
        getId(),
        venueType.getCode(),
        title,
        getIssn(),
        isbn);
  }
}
