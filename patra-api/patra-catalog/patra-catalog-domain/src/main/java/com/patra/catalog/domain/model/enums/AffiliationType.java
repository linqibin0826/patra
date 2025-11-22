package com.patra.catalog.domain.model.enums;

/// 机构类型枚举。表示机构的分类(教育、医疗、企业等)。
///
/// **主要类型**：
///
/// - EDUCATION：教育机构(大学、研究所等)
///   - HEALTHCARE：医疗机构(医院、诊所等)
///   - COMPANY：企业机构(制药公司、生物技术公司等)
///   - GOVERNMENT：政府机构(卫生部、科研机构等)
///   - NONPROFIT：非营利组织(基金会、协会等)
///   - OTHER：其他类型机构
///
/// **使用场景**：
///
/// - 机构分类统计(如统计各类型机构的发文量)
///   - 机构筛选(如仅查询教育机构)
///   - 机构认证(不同类型机构的认证要求不同)
///
/// @author linqibin
/// @since 0.1.0
public enum AffiliationType {

  /// 教育机构(大学、研究所、学院等)
  EDUCATION("education", "教育机构", "University, Research Institute"),

  /// 医疗机构(医院、诊所、医疗中心等)
  HEALTHCARE("healthcare", "医疗机构", "Hospital, Clinic, Medical Center"),

  /// 企业机构(制药公司、生物技术公司等)
  COMPANY("company", "企业机构", "Pharmaceutical Company, Biotech"),

  /// 政府机构(卫生部、科研机构等)
  GOVERNMENT("government", "政府机构", "Government Agency, Ministry"),

  /// 非营利组织(基金会、协会等)
  NONPROFIT("nonprofit", "非营利组织", "Foundation, Association"),

  /// 其他类型机构
  OTHER("other", "其他", "Other");

  /// 代码值(与数据库存储一致)
  private final String code;

  /// 中文名称
  private final String cnName;

  /// 英文描述
  private final String enDescription;

  /// 构造函数。
  ///
  /// @param code 代码值
  /// @param cnName 中文名称
  /// @param enDescription 英文描述
  AffiliationType(String code, String cnName, String enDescription) {
    this.code = code;
    this.cnName = cnName;
    this.enDescription = enDescription;
  }

  /// 获取代码值。
  ///
  /// @return 代码值
  public String getCode() {
    return code;
  }

  /// 获取中文名称。
  ///
  /// @return 中文名称
  public String getCnName() {
    return cnName;
  }

  /// 获取英文描述。
  ///
  /// @return 英文描述
  public String getEnDescription() {
    return enDescription;
  }

  /// 根据代码值获取枚举。
  ///
  /// @param code 代码值
  /// @return 机构类型枚举,如果不存在则抛出异常
  /// @throws IllegalArgumentException 如果代码值无效
  public static AffiliationType fromCode(String code) {
    for (AffiliationType type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("无效的机构类型代码：" + code);
  }

  /// 判断是否为教育机构。
  ///
  /// @return true 如果为教育机构
  public boolean isEducation() {
    return this == EDUCATION;
  }

  /// 判断是否为医疗机构。
  ///
  /// @return true 如果为医疗机构
  public boolean isHealthcare() {
    return this == HEALTHCARE;
  }

  /// 判断是否为企业机构。
  ///
  /// @return true 如果为企业机构
  public boolean isCompany() {
    return this == COMPANY;
  }
}
