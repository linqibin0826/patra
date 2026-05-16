package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// SCR 数据来源值对象。
///
/// 表示 SCR（补充概念记录）的数据来源，如 NCI、FDA、OMIM 等。
/// 每个 SCR 可以有多个来源，来源信息用于追溯数据的权威性。
///
/// 常见来源示例：
///
/// - NCI2004_11_17（美国国家癌症研究所）
/// - FDA SRS (2023)（FDA 物质注册系统）
/// - OMIM (2013)（人类孟德尔遗传在线）
/// - DrugBank（药物数据库）
///
/// @param source 来源名称（必需）
/// @param orderNum 排序号（可选，用于保持原始顺序）
/// @author linqibin
/// @since 0.1.0
public record ScrSource(String source, Integer orderNum) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数有效性。
  public ScrSource {
    Assert.isTrue(StrUtil.isNotBlank(source), "来源不能为空");
  }

  /// 创建来源（不带排序号）。
  ///
  /// @param source 来源名称
  /// @return 来源值对象
  public static ScrSource of(String source) {
    return new ScrSource(source, null);
  }

  /// 创建来源（带排序号）。
  ///
  /// @param source 来源名称
  /// @param orderNum 排序号
  /// @return 来源值对象
  public static ScrSource of(String source, Integer orderNum) {
    return new ScrSource(source, orderNum);
  }

  @Override
  public String toString() {
    if (orderNum != null) {
      return String.format("ScrSource[%s, order=%d]", source, orderNum);
    }
    return String.format("ScrSource[%s]", source);
  }
}
