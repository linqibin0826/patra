package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/// MeSH 树形编号实体(Aggregate内实体,不是聚合根)。
///
/// MeSH 树形编号说明：
///
/// - 树形结构：采用层次编号系统(如 C04.557.337.428)
///   - 多位置：一个主题词可以有多个树形位置(平均 2.3 个)
///   - 层级深度：最多 10 层
///   - 示例：
///
/// - C04.557.337.428 - "Lung Neoplasms"(肺肿瘤)
///         - C08.381.540 - "Lung Neoplasms"(同一主题词的另一个位置)
///
/// 业务规则：
///
/// - 同一主题词的不同树形位置表示不同的语义分类角度
///   - is_primary 标记主要位置(检索时优先)
///   - tree_level 通过点号分隔数自动计算
///   - 支持层次查询(如查询 C04 分支下的所有主题词)
///
/// 使用示例：
///
/// ```java
/// // 创建主要位置的树形编号
/// MeshTreeNumber primary = MeshTreeNumber.create("C04.557.337.428", true);
/// assert primary.getTreeLevel() == 4;
/// assert primary.isPrimary();
///
/// // 创建次要位置的树形编号
/// MeshTreeNumber secondary = MeshTreeNumber.create("C08.381.540", false);
/// assert secondary.getTreeLevel() == 3;
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class MeshTreeNumber implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键ID(由Repository在持久化时分配)
  private Long id;

  /// 关联的主题词ID(外键)
  private final Long descriptorId;

  // ========== 业务字段 ==========

  /// 树形编号(如 C04.557.337.428)
  private final String treeNumber;

  /// 层级深度(1-10,通过点号分隔数计算)
  private final int treeLevel;

  /// 是否主要位置(主要位置在检索时优先)
  private final boolean isPrimary;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param descriptorId 主题词ID
  /// @param treeNumber 树形编号
  /// @param treeLevel 层级深度
  /// @param isPrimary 是否主要位置
  private MeshTreeNumber(
      Long id, Long descriptorId, String treeNumber, int treeLevel, boolean isPrimary) {
    // 必填字段验证（descriptorId 在解析阶段可以为 null，后续通过 setDescriptorId 设置）
    Assert.notBlank(treeNumber, "树形编号不能为空");

    // 树形编号格式验证(字母+数字组合,点号分隔)
    Assert.isTrue(
        treeNumber.matches("^[A-Z]\\d{2}(\\.\\d{3})*$"),
        "树形编号格式无效,必须符合 'X00.000.000' 格式：%s",
        treeNumber);

    // 层级深度范围验证
    Assert.isTrue(treeLevel >= 1 && treeLevel <= 10, "层级深度必须在1-10范围内：%d", treeLevel);

    // 赋值
    this.id = id;
    this.descriptorId = descriptorId;
    this.treeNumber = treeNumber;
    this.treeLevel = treeLevel;
    this.isPrimary = isPrimary;
  }

  // ========== 工厂方法 ==========

  /// 创建树形编号(自动计算层级深度)。
  ///
  /// @param treeNumber 树形编号
  /// @param isPrimary 是否主要位置
  /// @return 树形编号实体
  public static MeshTreeNumber create(String treeNumber, boolean isPrimary) {
    int level = calculateLevel(treeNumber);
    return new MeshTreeNumber(null, null, treeNumber, level, isPrimary);
  }

  /// 创建树形编号(指定主题词ID)。
  ///
  /// @param descriptorId 主题词ID
  /// @param treeNumber 树形编号
  /// @param isPrimary 是否主要位置
  /// @return 树形编号实体
  public static MeshTreeNumber create(Long descriptorId, String treeNumber, boolean isPrimary) {
    int level = calculateLevel(treeNumber);
    return new MeshTreeNumber(null, descriptorId, treeNumber, level, isPrimary);
  }

  /// 从持久化状态重建实体(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param descriptorId 主题词ID
  /// @param treeNumber 树形编号
  /// @param treeLevel 层级深度
  /// @param isPrimary 是否主要位置
  /// @return 重建的实体
  public static MeshTreeNumber restore(
      Long id, Long descriptorId, String treeNumber, int treeLevel, boolean isPrimary) {
    return new MeshTreeNumber(id, descriptorId, treeNumber, treeLevel, isPrimary);
  }

  // ========== 业务方法 ==========

  /// 设置ID(由Repository在持久化后回写)。
  ///
  /// @param id 主键ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 获取树形编号的根分类。
  ///
  /// @return 根分类代码(如 "C04" 中的 "C")
  public String getRootCategory() {
    return treeNumber.substring(0, 1);
  }

  /// 获取树形编号的顶层分类。
  ///
  /// @return 顶层分类编号(如 "C04.557.337.428" 中的 "C04")
  public String getTopLevelCategory() {
    int dotIndex = treeNumber.indexOf('.');
    if (dotIndex == -1) {
      return treeNumber;
    }
    return treeNumber.substring(0, dotIndex);
  }

  /// 获取父级树形编号。
  ///
  /// @return 父级树形编号,如果是顶层则返回空
  public String getParentTreeNumber() {
    int lastDotIndex = treeNumber.lastIndexOf('.');
    if (lastDotIndex == -1) {
      return null; // 顶层节点没有父节点
    }
    return treeNumber.substring(0, lastDotIndex);
  }

  /// 判断是否为顶层节点。
  ///
  /// @return true 如果为顶层节点(无点号)
  public boolean isTopLevel() {
    return !treeNumber.contains(".");
  }

  /// 判断是否为某个分支的子节点。
  ///
  /// @param branchPrefix 分支前缀(如 "C04")
  /// @return true 如果属于该分支
  public boolean belongsToBranch(String branchPrefix) {
    return treeNumber.startsWith(branchPrefix);
  }

  // ========== 辅助方法 ==========

  /// 计算树形编号的层级深度。
  ///
  /// @param treeNumber 树形编号
  /// @return 层级深度(点号数量 + 1)
  private static int calculateLevel(String treeNumber) {
    if (StrUtil.isBlank(treeNumber)) {
      return 0;
    }
    return (int) treeNumber.chars().filter(ch -> ch == '.').count() + 1;
  }

  @Override
  public String toString() {
    return String.format(
        "MeshTreeNumber[treeNumber=%s, level=%d, primary=%b]", treeNumber, treeLevel, isPrimary);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MeshTreeNumber that)) {
      return false;
    }
    return treeNumber.equals(that.treeNumber);
  }

  @Override
  public int hashCode() {
    return treeNumber.hashCode();
  }
}
