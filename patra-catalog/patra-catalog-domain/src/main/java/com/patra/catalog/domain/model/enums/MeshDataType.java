package com.patra.catalog.domain.model.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MeSH 数据类型枚举。
 *
 * <p>定义 MeSH 数据库的 5 种核心数据类型及其导入顺序。 导入顺序由 {@link #importOrder} 定义，必须按照依赖关系执行。
 *
 * <p><strong>导入依赖关系：</strong>
 *
 * <ol>
 *   <li>QUALIFIER（限定词） - 无依赖，必须最先导入
 *   <li>DESCRIPTOR（主题词） - 依赖 QUALIFIER
 *   <li>TREE_NUMBER（树形编号） - 依赖 DESCRIPTOR
 *   <li>ENTRY_TERM（入口术语） - 依赖 DESCRIPTOR
 *   <li>CONCEPT（概念） - 依赖 DESCRIPTOR
 * </ol>
 *
 * @author Patra
 */
@Getter
@RequiredArgsConstructor
public enum MeshDataType {

  /** 限定词 - 用于修饰主题词的限定条件 */
  QUALIFIER("qualifier", "限定词", 1),

  /** 主题词 - MeSH 的核心概念 */
  DESCRIPTOR("descriptor", "主题词", 2),

  /** 树形编号 - 主题词的层次结构编码 */
  TREE_NUMBER("tree-number", "树形编号", 3),

  /** 入口术语 - 主题词的同义词和变体 */
  ENTRY_TERM("entry-term", "入口术语", 4),

  /** 概念 - 主题词包含的细粒度概念 */
  CONCEPT("concept", "概念", 5);

  /**
   * 数据类型代码（用于配置文件、Map key、数据库表名）。
   *
   * <p>此值必须与以下保持一致：
   *
   * <ul>
   *   <li>配置文件中的 key（如 {@code patra.catalog.mesh.import.batch-size-map.descriptor}）
   *   <li>{@link com.patra.catalog.app.config.MeshImportConfig} 中的 Map key
   *   <li>{@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate#updateTableProgress}
   *       的参数
   * </ul>
   */
  private final String code;

  /** 数据类型中文名（用于日志和用户展示）。 */
  private final String displayName;

  /**
   * 导入顺序（1-5，数字越小越先导入）。
   *
   * <p>必须严格按照此顺序导入，否则会违反外键约束。
   */
  private final int importOrder;

  /**
   * 根据 code 查找枚举。
   *
   * @param code 数据类型代码
   * @return 对应的枚举值
   * @throws IllegalArgumentException 如果 code 不存在
   */
  public static MeshDataType fromCode(String code) {
    for (MeshDataType type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 MeSH 数据类型: " + code);
  }

  /**
   * 获取所有数据类型的 code 列表（按导入顺序）。
   *
   * @return 按 importOrder 排序的 code 列表
   */
  public static List<String> getAllCodes() {
    return Arrays.stream(values())
        .sorted(Comparator.comparingInt(MeshDataType::getImportOrder))
        .map(MeshDataType::getCode)
        .toList();
  }
}
