package dev.linqibin.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/// 数据来源信息值对象。封装数据的来源和同步状态。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 来源追溯：记录数据来自哪个外部数据源
/// - 时间追踪：记录最后同步时间
/// - 类型安全：使用 [ProvenanceCode] 枚举确保来源代码有效
///
/// 使用示例：
///
/// ```java
/// // 创建来源信息
/// ProvenanceInfo provenance = ProvenanceInfo.of(
///     ProvenanceCode.PUBMED,
///     Instant.now()
/// );
///
/// // 使用工厂方法（自动设置当前时间）
/// ProvenanceInfo pubmed = ProvenanceInfo.forPubMed();
///
/// // 仅来源代码（无时间信息）
/// ProvenanceInfo simple = ProvenanceInfo.ofCode(ProvenanceCode.MANUAL);
/// ```
///
/// @param code 来源代码枚举
/// @param lastSyncedAt 最后同步时间（可选）
/// @author linqibin
/// @since 0.1.0
public record ProvenanceInfo(ProvenanceCode code, Instant lastSyncedAt) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证来源信息的有效性。
  ///
  /// @throws IllegalArgumentException 如果来源代码为空
  public ProvenanceInfo {
    Assert.notNull(code, "来源代码不能为空");
  }

  /// 创建完整来源信息。
  ///
  /// @param code 来源代码
  /// @param lastSyncedAt 最后同步时间
  /// @return 来源信息值对象
  public static ProvenanceInfo of(ProvenanceCode code, Instant lastSyncedAt) {
    return new ProvenanceInfo(code, lastSyncedAt);
  }

  /// 创建仅来源代码的来源信息（无时间信息）。
  ///
  /// @param code 来源代码
  /// @return 来源信息值对象
  public static ProvenanceInfo ofCode(ProvenanceCode code) {
    return new ProvenanceInfo(code, null);
  }

  /// 创建 PubMed 来源信息。
  ///
  /// @return 来源信息值对象
  public static ProvenanceInfo forPubMed() {
    return new ProvenanceInfo(ProvenanceCode.PUBMED, Instant.now());
  }

  /// 创建手动录入来源信息。
  ///
  /// @return 来源信息值对象
  public static ProvenanceInfo forManual() {
    return new ProvenanceInfo(ProvenanceCode.MANUAL, Instant.now());
  }

  /// 判断是否来自 PubMed。
  ///
  /// @return true 如果来自 PubMed
  public boolean isFromPubMed() {
    return code == ProvenanceCode.PUBMED;
  }

  /// 判断是否手动录入。
  ///
  /// @return true 如果手动录入
  public boolean isManual() {
    return code == ProvenanceCode.MANUAL;
  }

  /// 更新最后同步时间。
  ///
  /// @return 新的来源信息值对象（更新了同步时间）
  public ProvenanceInfo withSyncedNow() {
    return new ProvenanceInfo(code, Instant.now());
  }

  /// 获取来源代码字符串（用于持久化）。
  ///
  /// @return 来源代码字符串
  public String codeAsString() {
    return code.getCode();
  }
}
