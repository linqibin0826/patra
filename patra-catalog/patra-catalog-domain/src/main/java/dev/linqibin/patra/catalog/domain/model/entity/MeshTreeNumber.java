package dev.linqibin.patra.catalog.domain.model.entity;

import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/// MeSH 树形编号实体(Aggregate内实体,不是聚合根)。
///
/// MeSH 树形编号说明：
///
/// - 树形结构：采用层次编号系统(如 C04.557.337.428)
///   - 多位置：一个主题词可以有多个树形位置(平均 2.3 个)
///   - 层级深度：最多 15 层（实际数据中最大约 13 层）
///   - 格式：`X00[.000]*`，首段固定 3 字符（字母+2位数字），后续段 1-3 位数字
///   - 示例：
///
/// - C04.557.337.428 - "Lung Neoplasms"(肺肿瘤)
/// - C08.381.540 - "Lung Neoplasms"(同一主题词的另一个位置)
/// - B04.820.578.688.2.150 - MeSH 2026 新格式（支持 1-2 位数字段）
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

  /// 关联的主题词UI(格式:D000001)
  private final MeshUI descriptorUi;

  // ========== 业务字段 ==========

  /// 树形编号(如 C04.557.337.428)
  private final String treeNumber;

  /// 层级深度(1-15,通过点号分隔数计算)
  private final int treeLevel;

  /// 是否主要位置(主要位置在检索时优先)
  private final boolean isPrimary;

  /// 私有构造函数。
  ///
  /// @param id 主键ID(新建时为null)
  /// @param descriptorUi 主题词UI
  /// @param treeNumber 树形编号
  /// @param treeLevel 层级深度
  /// @param isPrimary 是否主要位置
  private MeshTreeNumber(
      Long id, MeshUI descriptorUi, String treeNumber, int treeLevel, boolean isPrimary) {
    // 必填字段验证（descriptorUi 在解析阶段可以为 null，后续由聚合根设置）
    if (treeNumber == null || treeNumber.isBlank()) {
      throw new IllegalArgumentException("树形编号不能为空");
    }

    // 树形编号格式验证(字母+数字组合,点号分隔,每段1-3位数字)
    // MeSH 2026 起支持 1-2 位数字的子分类段（如 B04.820.578.688.2.150）
    if (!treeNumber.matches("^[A-Z]\\d{2}(\\.\\d{1,3})*$")) {
      throw new IllegalArgumentException(
          String.format("树形编号格式无效,必须符合 'X00[.000]*' 格式（后续段支持1-3位数字）：%s", treeNumber));
    }

    // 层级深度范围验证（MeSH 实际数据中最大深度约 13 层，预留余量到 15 层）
    if (treeLevel < 1 || treeLevel > 15) {
      throw new IllegalArgumentException(String.format("层级深度必须在1-15范围内：%d", treeLevel));
    }

    // 赋值
    this.id = id;
    this.descriptorUi = descriptorUi;
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

  /// 创建树形编号(指定主题词UI)。
  ///
  /// @param descriptorUi 主题词UI
  /// @param treeNumber 树形编号
  /// @param isPrimary 是否主要位置
  /// @return 树形编号实体
  public static MeshTreeNumber create(MeshUI descriptorUi, String treeNumber, boolean isPrimary) {
    int level = calculateLevel(treeNumber);
    return new MeshTreeNumber(null, descriptorUi, treeNumber, level, isPrimary);
  }

  /// 从持久化状态重建实体(由Repository使用)。
  ///
  /// @param id 主键ID
  /// @param descriptorUi 主题词UI
  /// @param treeNumber 树形编号
  /// @param treeLevel 层级深度
  /// @param isPrimary 是否主要位置
  /// @return 重建的实体
  public static MeshTreeNumber restore(
      Long id, MeshUI descriptorUi, String treeNumber, int treeLevel, boolean isPrimary) {
    return new MeshTreeNumber(id, descriptorUi, treeNumber, treeLevel, isPrimary);
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
    if (treeNumber == null || treeNumber.isBlank()) {
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
    // MeSH 2026 起支持共享树形位置，需同时比较 treeNumber 和 descriptorUi
    if (!treeNumber.equals(that.treeNumber)) {
      return false;
    }
    // 如果两者的 descriptorUi 都不为 null，则需要相等
    if (descriptorUi != null && that.descriptorUi != null) {
      return descriptorUi.equals(that.descriptorUi);
    }
    // 如果任一方 descriptorUi 为 null，则只比较 treeNumber（聚合内部场景）
    return true;
  }

  @Override
  public int hashCode() {
    // hashCode 只使用 treeNumber，保证与 equals 的宽松匹配兼容
    return treeNumber.hashCode();
  }
}
