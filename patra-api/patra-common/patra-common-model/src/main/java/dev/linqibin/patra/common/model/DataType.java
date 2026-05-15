package dev.linqibin.patra.common.model;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/// 数据类型枚举 - 系统支持的所有数据类型标记
///
/// 设计理念：
///
/// - 作为类型标记，而非业务枚举
///   - 包含类型元信息（code, class, description）
///   - 支持通过 code 或 class 反查
///
/// ## 架构决策：
///
/// - ADR-0004: 不包含 tableName 字段（移除数据库耦合）
///   - 预定义 10 种数据类型，覆盖出版物、期刊、药品等领域
///   - 提供类型安全的查找和分类方法
///
/// @since 0.1.0
@Getter
public enum DataType {

  // ========== 出版物相关 ==========

  /// 出版物数据（标准化）
  ///
  /// 来源：PubMed, EPMC, Crossref
  PUBLICATION("publication", "出版物数据", CanonicalPublication.class),

  /// 出版物全文
  ///
  /// 来源：EPMC, PMC
  PUBLICATION_FULLTEXT("publication_fulltext", "出版物全文", PublicationFulltext.class),

  // ========== 期刊相关 ==========

  /// 期刊数据
  ///
  /// 来源：DOAJ, JCR
  JOURNAL("journal", "期刊数据", Journal.class),

  /// 期刊指标（影响因子等）
  ///
  /// 来源：JCR, Scopus
  JOURNAL_METRICS("journal_metrics", "期刊指标", JournalMetrics.class),

  // ========== 引用相关 ==========

  /// 引用关系
  ///
  /// 来源：Crossref, OpenCitations
  CITATION("citation", "引用数据", Citation.class),

  /// 参考文献
  ///
  /// 来源：PubMed, EPMC
  REFERENCE("reference", "参考文献", Reference.class),

  // ========== 药品相关 ==========

  /// 药品数据
  ///
  /// 来源：DrugBank, ChEMBL
  DRUG("drug", "药品数据", Drug.class),

  /// 药品相互作用
  ///
  /// 来源：DrugBank
  DRUG_INTERACTION("drug_interaction", "药品相互作用", DrugInteraction.class),

  // ========== 实体相关 ==========

  /// 作者信息
  ///
  /// 来源：PubMed, ORCID
  AUTHOR("author", "作者信息", Author.class),

  /// 机构信息
  ///
  /// 来源：PubMed, ROR
  AFFILIATION("affiliation", "机构信息", Affiliation.class);

  // ========== 字段定义 ==========

  /// 类型代码（用于配置和API） -- GETTER -- 获取类型代码
  ///
  /// @return 类型代码
  private final String code;

  /// 中文描述 -- GETTER -- 获取中文描述
  ///
  /// @return 中文描述
  private final String description;

  /// 对应的 Java 类 -- GETTER -- 获取对应的 Java 类
  ///
  /// @return Java 类
  private final Class<?> dataClass;

  // ========== 构造函数 ==========

  /// 构造数据类型枚举常量。
  ///
  /// @param code 类型代码
  /// @param description 中文描述
  /// @param dataClass 对应的 Java 类
  DataType(String code, String description, Class<?> dataClass) {
    this.code = code;
    this.description = description;
    this.dataClass = dataClass;
  }

  // ========== Getters ==========

  // ========== 类型检查方法 ==========

  /// 检查给定的类是否可以赋值给此数据类型
  ///
  /// @param clazz 要检查的类
  /// @return 如果可以赋值返回 true，否则返回 false
  public boolean isAssignableFrom(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    return dataClass.isAssignableFrom(clazz);
  }

  /// 判断是否为关系型数据类型（需要关联其他实体）
  ///
  /// 关系型数据类型包括：
  ///
  /// - CITATION - 引用关系
  ///   - REFERENCE - 参考文献
  ///   - DRUG_INTERACTION - 药品相互作用
  ///   - PUBLICATION_FULLTEXT - 文献全文（关联文献实体）
  ///
  /// @return 如果是关系型数据类型返回 true，否则返回 false
  public boolean isRelational() {
    return this == CITATION
        || this == REFERENCE
        || this == DRUG_INTERACTION
        || this == PUBLICATION_FULLTEXT;
  }

  /// 判断是否为核心实体类型
  ///
  /// 核心实体类型包括：
  ///
  /// - PUBLICATION - 出版物数据
  ///   - JOURNAL - 期刊数据
  ///   - DRUG - 药品数据
  ///   - AUTHOR - 作者信息
  ///
  /// @return 如果是核心实体类型返回 true，否则返回 false
  public boolean isCoreEntity() {
    return this == PUBLICATION || this == JOURNAL || this == DRUG || this == AUTHOR;
  }

  // ========== 查找方法 ==========

  /// 通过类型代码查找（精确匹配，不区分大小写）
  ///
  /// @param code 类型代码
  /// @return 对应的 DataType 枚举常量
  /// @throws IllegalArgumentException 如果找不到对应的类型
  /// @throws NullPointerException 如果 code 为 null
  public static DataType fromCode(String code) {
    if (code == null) {
      throw new NullPointerException("code cannot be null");
    }
    if (code.isEmpty()) {
      throw new IllegalArgumentException("Unknown DataType code: " + code);
    }

    for (DataType type : values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown DataType code: " + code);
  }

  /// 通过类型代码查找（返回 Optional）
  ///
  /// @param code 类型代码
  /// @return Optional 包装的 DataType，如果找不到返回 Optional.empty()
  public static Optional<DataType> findByCode(String code) {
    if (code == null) {
      return Optional.empty();
    }

    for (DataType type : values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  /// 通过 Class 查找
  ///
  /// @param clazz Java 类
  /// @return 对应的 DataType 枚举常量
  /// @throws IllegalArgumentException 如果找不到对应的类型
  /// @throws NullPointerException 如果 clazz 为 null
  public static DataType fromClass(Class<?> clazz) {
    if (clazz == null) {
      throw new NullPointerException("clazz cannot be null");
    }

    for (DataType type : values()) {
      if (type.dataClass.equals(clazz)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown DataType for class: " + clazz.getName());
  }

  /// 通过 Class 查找（返回 Optional）
  ///
  /// @param clazz Java 类
  /// @return Optional 包装的 DataType，如果找不到返回 Optional.empty()
  public static Optional<DataType> findByClass(Class<?> clazz) {
    if (clazz == null) {
      return Optional.empty();
    }

    for (DataType type : values()) {
      if (type.dataClass.equals(clazz)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  // ========== 分组方法 ==========

  /// 获取所有出版物相关类型
  ///
  /// @return 出版物相关类型的不可变集合
  public static Set<DataType> publicationTypes() {
    return Set.of(PUBLICATION, PUBLICATION_FULLTEXT, REFERENCE);
  }

  /// 获取所有期刊相关类型
  ///
  /// @return 期刊相关类型的不可变集合
  public static Set<DataType> journalTypes() {
    return Set.of(JOURNAL, JOURNAL_METRICS);
  }

  /// 获取所有药品相关类型
  ///
  /// @return 药品相关类型的不可变集合
  public static Set<DataType> drugTypes() {
    return Set.of(DRUG, DRUG_INTERACTION);
  }

  // ========== 占位符类（后续由实际领域模型替换） ==========

  /// 文献全文占位符类
  ///
  /// TODO: 后续由实际的 PublicationFulltext 领域模型替换
  public static class PublicationFulltext {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private PublicationFulltext() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 期刊占位符类
  ///
  /// TODO: 后续由实际的 Journal 领域模型替换
  public static class Journal {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Journal() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 期刊指标占位符类
  ///
  /// TODO: 后续由实际的 JournalMetrics 领域模型替换
  public static class JournalMetrics {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private JournalMetrics() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 引用关系占位符类
  ///
  /// TODO: 后续由实际的 Citation 领域模型替换
  public static class Citation {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Citation() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 参考文献占位符类
  ///
  /// TODO: 后续由实际的 Reference 领域模型替换
  public static class Reference {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Reference() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 药品占位符类
  ///
  /// TODO: 后续由实际的 Drug 领域模型替换
  public static class Drug {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Drug() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 药品相互作用占位符类
  ///
  /// TODO: 后续由实际的 DrugInteraction 领域模型替换
  public static class DrugInteraction {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private DrugInteraction() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 作者信息占位符类
  ///
  /// TODO: 后续由实际的 Author 领域模型替换
  public static class Author {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Author() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }

  /// 机构信息占位符类
  ///
  /// TODO: 后续由实际的 Affiliation 领域模型替换
  public static class Affiliation {

    /// 私有构造函数,防止占位符类实例化。
    ///
    /// @throws AssertionError 总是抛出，因为占位符类不应被实例化
    private Affiliation() {
      throw new AssertionError("占位符类，不应实例化");
    }
  }
}
