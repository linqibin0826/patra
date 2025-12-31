package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 唯一标识符值对象。封装 MeSH UI 格式验证和类型区分。
///
/// MeSH UI 格式说明（基于 NLM 官方规范）：
///
/// - **Descriptor UI**：D + 6位数字（旧格式）或 D + 9位数字（新格式），例：D000001, D000009711
/// - **Qualifier UI**：Q + 6位数字（旧格式）或 Q + 9位数字（新格式），例：Q000002, Q000941
/// - **Concept UI**：M + 7位数字（旧格式）或 M + 9位数字（新格式），例：M0000001, M0581777
/// - **SCR UI**：C + 6位数字（旧格式）或 C + 9位数字（新格式），例：C000001, C000000123
///
/// **历史演变**：
///
/// - 2013年4月15日前：Descriptor/Qualifier 为7字符（前缀+6位），Concept 为8字符（前缀+7位）
/// - 2013年4月15日起：新 UI 扩展为10字符（前缀+9位），旧 UI 保持不变
/// - 向后兼容：系统同时支持新旧两种格式
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 格式验证：UI 必须符合 NLM 官方规范
/// - 类型安全：通过前缀识别 UI 类型
/// - 向后兼容：支持新旧两种长度格式
///
/// 使用示例：
///
/// ```java
/// // 创建 Descriptor UI（旧格式）
/// MeshUI descriptorUi = MeshUI.of("D000001");
/// assert descriptorUi.isDescriptor();
///
/// // 创建 Descriptor UI（新格式）
/// MeshUI descriptorUiNew = MeshUI.of("D000009711");
///
/// // 创建 Qualifier UI
/// MeshUI qualifierUi = MeshUI.of("Q000123");
/// assert qualifierUi.isQualifier();
///
/// // 创建 Concept UI（旧格式：7位数字）
/// MeshUI conceptUi = MeshUI.of("M0000001");
/// assert conceptUi.isConcept();
///
/// // 创建 SCR UI
/// MeshUI scrUi = MeshUI.of("C000001");
/// assert scrUi.isScr();
/// ```
///
/// @param ui MeSH 唯一标识符（格式：D/Q + 6或9位数字，M + 7或9位数字，C + 6或9位数字）
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://www.nlm.nih.gov/mesh/xml_data_elements.html">MeSH XML Data Elements</a>
/// @see <a href="https://www.nlm.nih.gov/pubs/techbull/ma13/ma13_mesh_ui_expand.html">MeSH UI
// Length Expansion</a>
public record MeshUI(String ui) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// Descriptor UI 正则表达式：D + 6位数字（旧格式）或 9位数字（新格式）
  /// 示例：D000001, D000009711
  private static final String DESCRIPTOR_PATTERN = "D\\d{6}(\\d{3})?";

  /// Qualifier UI 正则表达式：Q + 6位数字（旧格式）或 9位数字（新格式）
  /// 示例：Q000002, Q000941
  private static final String QUALIFIER_PATTERN = "Q\\d{6}(\\d{3})?";

  /// Concept UI 正则表达式：M + 7位数字（旧格式）或 9位数字（新格式）
  /// 注意：Concept UI 原始格式为 8字符（M + 7位数字），与其他类型不同
  /// 示例：M0000001, M0581777
  private static final String CONCEPT_PATTERN = "M\\d{7}(\\d{2})?";

  /// Term UI 正则表达式：T + 6位数字（旧格式）或 9位数字（新格式）
  /// 示例：T000001, T001124965
  private static final String TERM_PATTERN = "T\\d{6}(\\d{3})?";

  /// SCR (Supplementary Concept Record) UI 正则表达式：C + 6位数字（旧格式）或 9位数字（新格式）
  /// 示例：C000001, C000000123
  private static final String SCR_PATTERN = "C\\d{6}(\\d{3})?";

  /// 紧凑构造器：验证 MeSH UI 的有效性。
  ///
  /// @throws IllegalArgumentException 如果 UI 为空或格式无效
  public MeshUI {
    Assert.notBlank(ui, "MeSH UI 不能为空");

    // 标准化为大写
    ui = ui.toUpperCase();

    // UI 格式验证
    // - Descriptor: D + 6或9位数字
    // - Qualifier: Q + 6或9位数字
    // - Concept: M + 7或9位数字（特殊：原始为8字符）
    // - Term: T + 6或9位数字
    // - SCR: C + 6或9位数字
    Assert.isTrue(
        ui.matches(DESCRIPTOR_PATTERN)
            || ui.matches(QUALIFIER_PATTERN)
            || ui.matches(CONCEPT_PATTERN)
            || ui.matches(TERM_PATTERN)
            || ui.matches(SCR_PATTERN),
        "MeSH UI 格式无效。有效格式：Descriptor(D+6/9位数字), Qualifier(Q+6/9位数字), Concept(M+7/9位数字), Term(T+6/9位数字), SCR(C+6/9位数字)。实际值：%s",
        ui);
  }

  /// 创建 MeSH UI。
  ///
  /// @param ui UI 字符串
  /// @return MeSH UI 值对象
  /// @throws IllegalArgumentException 如果 UI 格式无效
  public static MeshUI of(String ui) {
    return new MeshUI(ui);
  }

  /// 创建 Descriptor UI（旧格式：7字符）。
  ///
  /// @param number UI 编号（1-999999，生成6位数字）
  /// @return Descriptor UI（例：D000001）
  public static MeshUI descriptorOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Descriptor UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("D%06d", number));
  }

  /// 创建 Qualifier UI（旧格式：7字符）。
  ///
  /// @param number UI 编号（1-999999，生成6位数字）
  /// @return Qualifier UI（例：Q000002）
  public static MeshUI qualifierOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Qualifier UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("Q%06d", number));
  }

  /// 创建 Concept UI（旧格式：8字符）。
  ///
  /// @param number UI 编号（1-9999999，生成7位数字）
  /// @return Concept UI（例：M0000001）
  public static MeshUI conceptOf(int number) {
    Assert.isTrue(number >= 1 && number <= 9999999, "Concept UI 编号必须在 1-9999999 范围内");
    return new MeshUI(String.format("M%07d", number));
  }

  /// 创建 Term UI（旧格式：7字符）。
  ///
  /// @param number UI 编号（1-999999，生成6位数字）
  /// @return Term UI（例：T000001）
  public static MeshUI termOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Term UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("T%06d", number));
  }

  /// 创建 SCR UI（旧格式：7字符）。
  ///
  /// @param number UI 编号（1-999999，生成6位数字）
  /// @return SCR UI（例：C000001）
  public static MeshUI scrOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "SCR UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("C%06d", number));
  }

  /// 判断是否为 Descriptor UI。
  ///
  /// @return true 如果为 Descriptor UI
  public boolean isDescriptor() {
    return ui.startsWith("D");
  }

  /// 判断是否为 Qualifier UI。
  ///
  /// @return true 如果为 Qualifier UI
  public boolean isQualifier() {
    return ui.startsWith("Q");
  }

  /// 判断是否为 Concept UI。
  ///
  /// @return true 如果为 Concept UI
  public boolean isConcept() {
    return ui.startsWith("M");
  }

  /// 判断是否为 Term UI。
  ///
  /// @return true 如果为 Term UI
  public boolean isTerm() {
    return ui.startsWith("T");
  }

  /// 判断是否为 SCR (Supplementary Concept Record) UI。
  ///
  /// @return true 如果为 SCR UI
  public boolean isScr() {
    return ui.startsWith("C");
  }

  /// 获取 UI 类型。
  ///
  /// @return UI 类型(Descriptor/Qualifier/Concept/Term/SCR)
  public String getType() {
    if (isDescriptor()) {
      return "Descriptor";
    } else if (isQualifier()) {
      return "Qualifier";
    } else if (isConcept()) {
      return "Concept";
    } else if (isTerm()) {
      return "Term";
    } else {
      return "SCR";
    }
  }

  /// 获取 UI 编号（去掉前缀字母）。
  ///
  /// @return UI 编号（Descriptor/Qualifier: 6或9位数字，Concept: 7或9位数字）
  public long getNumber() {
    return Long.parseLong(ui.substring(1));
  }

  @Override
  public String toString() {
    return ui;
  }
}
