package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 唯一标识符值对象。封装 MeSH UI 格式验证和类型区分。
///
/// MeSH UI 格式说明：
///
/// - **Descriptor UI**：D000001-D999999(主题词)
///   - **Qualifier UI**：Q000001-Q999999(限定词)
///   - **Concept UI**：M000001-M999999(概念)
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
///   - 格式验证：UI 必须符合 NLM 规范
///   - 类型安全：通过前缀识别 UI 类型
///
/// 使用示例：
///
/// ```java
/// // 创建 Descriptor UI
/// MeshUI descriptorUi = MeshUI.of("D000001");
/// assert descriptorUi.isDescriptor();
///
/// // 创建 Qualifier UI
/// MeshUI qualifierUi = MeshUI.of("Q000123");
/// assert qualifierUi.isQualifier();
///
/// // 创建 Concept UI
/// MeshUI conceptUi = MeshUI.of("M000456");
/// assert conceptUi.isConcept();
/// ```
///
/// @param ui MeSH 唯一标识符(格式：D/Q/M + 6位数字)
/// @author linqibin
/// @since 0.1.0
public record MeshUI(String ui) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// Descriptor UI 正则表达式：D + 6位数字
  private static final String DESCRIPTOR_PATTERN = "D\\d{6}";

  /// Qualifier UI 正则表达式：Q + 6位数字
  private static final String QUALIFIER_PATTERN = "Q\\d{6}";

  /// Concept UI 正则表达式：M + 6位数字
  private static final String CONCEPT_PATTERN = "M\\d{6}";

  /// 紧凑构造器：验证 MeSH UI 的有效性。
  ///
  /// @throws IllegalArgumentException 如果 UI 为空或格式无效
  public MeshUI {
    Assert.notBlank(ui, "MeSH UI 不能为空");

    // UI 格式验证(D/Q/M开头 + 6位数字)
    Assert.isTrue(
        ui.matches(DESCRIPTOR_PATTERN)
            || ui.matches(QUALIFIER_PATTERN)
            || ui.matches(CONCEPT_PATTERN),
        "MeSH UI 格式无效,必须符合 'D/Q/M + 6位数字' 格式：%s",
        ui);

    // 标准化为大写
    ui = ui.toUpperCase();
  }

  /// 创建 MeSH UI。
  ///
  /// @param ui UI 字符串
  /// @return MeSH UI 值对象
  /// @throws IllegalArgumentException 如果 UI 格式无效
  public static MeshUI of(String ui) {
    return new MeshUI(ui);
  }

  /// 创建 Descriptor UI。
  ///
  /// @param number UI 编号(6位数字)
  /// @return Descriptor UI
  public static MeshUI descriptorOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Descriptor UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("D%06d", number));
  }

  /// 创建 Qualifier UI。
  ///
  /// @param number UI 编号(6位数字)
  /// @return Qualifier UI
  public static MeshUI qualifierOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Qualifier UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("Q%06d", number));
  }

  /// 创建 Concept UI。
  ///
  /// @param number UI 编号(6位数字)
  /// @return Concept UI
  public static MeshUI conceptOf(int number) {
    Assert.isTrue(number >= 1 && number <= 999999, "Concept UI 编号必须在 1-999999 范围内");
    return new MeshUI(String.format("M%06d", number));
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

  /// 获取 UI 类型。
  ///
  /// @return UI 类型(Descriptor/Qualifier/Concept)
  public String getType() {
    if (isDescriptor()) {
      return "Descriptor";
    } else if (isQualifier()) {
      return "Qualifier";
    } else {
      return "Concept";
    }
  }

  /// 获取 UI 编号(去掉前缀字母)。
  ///
  /// @return UI 编号(6位数字)
  public int getNumber() {
    return Integer.parseInt(ui.substring(1));
  }

  @Override
  public String toString() {
    return ui;
  }
}
