package dev.linqibin.patra.catalog.domain.model.vo.author;

import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/// 机构标识符集合值对象。
///
/// 封装 PubMed 等外部数据源提供的机构标识符，用于消歧匹配。
///
/// 支持的标识符类型：
///
/// - **ROR ID**：Research Organization Registry 标识符，格式如 `03vek6s52`
/// - **Ringgold ID**：Ringgold 机构标识符，常见于 PubMed 数据
/// - **GRID ID**：Global Research Identifier Database 标识符（已废弃，2021年合并到 ROR）
///
/// 消歧优先级：
///
/// ```
/// ROR ID (精确) > Ringgold ID (高) > GRID ID (历史)
/// ```
///
/// 使用示例：
///
/// ```java
/// // 从 CanonicalPublication.Identifier 列表创建
/// var identifiers = AffiliationIdentifiers.fromCanonicalIdentifiers(affiliation.identifiers());
///
/// // 检查是否有可用标识符
/// if (identifiers != null && identifiers.hasRorId()) {
///     // 使用 ROR ID 进行精确匹配
///     String rorId = identifiers.rorId();
/// }
/// ```
///
/// @param rorId ROR ID（格式：03vek6s52）
/// @param ringgoldId Ringgold ID
/// @param gridId GRID ID（历史数据，已废弃）
/// @author linqibin
/// @since 0.1.0
public record AffiliationIdentifiers(String rorId, String ringgoldId, String gridId)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// ROR ID 的类型标识（小写）
  private static final String TYPE_ROR = "ror";

  /// Ringgold ID 的类型标识（小写）
  private static final String TYPE_RINGGOLD = "ringgold";

  /// GRID ID 的类型标识（小写）
  private static final String TYPE_GRID = "grid";

  /// 静态工厂方法：从标识符值创建。
  ///
  /// @param rorId ROR ID
  /// @param ringgoldId Ringgold ID
  /// @param gridId GRID ID
  /// @return AffiliationIdentifiers 实例，如果所有标识符都为空则返回 null
  public static AffiliationIdentifiers of(String rorId, String ringgoldId, String gridId) {
    // 标准化空白字符串为 null
    String normalizedRor = StrUtil.isBlank(rorId) ? null : rorId;
    String normalizedRinggold = StrUtil.isBlank(ringgoldId) ? null : ringgoldId;
    String normalizedGrid = StrUtil.isBlank(gridId) ? null : gridId;

    // 如果所有标识符都为空，返回 null
    if (normalizedRor == null && normalizedRinggold == null && normalizedGrid == null) {
      return null;
    }

    return new AffiliationIdentifiers(normalizedRor, normalizedRinggold, normalizedGrid);
  }

  /// 从通用标识符列表创建。
  ///
  /// 解析 CanonicalPublication.Affiliation.identifiers 中的标识符，
  /// 识别 ROR、Ringgold、GRID 类型并提取值。
  ///
  /// @param identifiers 标识符列表（每个元素需有 type 和 value 属性）
  /// @param <T> 标识符类型，需提供 type() 和 value() 方法
  /// @return AffiliationIdentifiers 实例，如果无可用标识符则返回 null
  public static <T extends IdentifierLike> AffiliationIdentifiers fromIdentifiers(
      List<T> identifiers) {
    if (identifiers == null || identifiers.isEmpty()) {
      return null;
    }

    String rorId = null;
    String ringgoldId = null;
    String gridId = null;

    for (T id : identifiers) {
      if (id == null || StrUtil.isBlank(id.type()) || StrUtil.isBlank(id.value())) {
        continue;
      }

      String type = id.type().toLowerCase(Locale.ROOT).trim();
      String value = id.value().trim();

      switch (type) {
        case TYPE_ROR -> rorId = value;
        case TYPE_RINGGOLD -> ringgoldId = value;
        case TYPE_GRID -> gridId = value;
        default -> {
          // 忽略未知类型
        }
      }
    }

    return of(rorId, ringgoldId, gridId);
  }

  /// 判断是否有 ROR ID。
  ///
  /// @return true 如果有 ROR ID
  public boolean hasRorId() {
    return StrUtil.isNotBlank(rorId);
  }

  /// 判断是否有 Ringgold ID。
  ///
  /// @return true 如果有 Ringgold ID
  public boolean hasRinggoldId() {
    return StrUtil.isNotBlank(ringgoldId);
  }

  /// 判断是否有 GRID ID。
  ///
  /// @return true 如果有 GRID ID
  public boolean hasGridId() {
    return StrUtil.isNotBlank(gridId);
  }

  /// 判断是否有任何可用标识符。
  ///
  /// @return true 如果至少有一个标识符
  public boolean hasAny() {
    return hasRorId() || hasRinggoldId() || hasGridId();
  }

  /// 判断是否有高优先级标识符（ROR 或 Ringgold）。
  ///
  /// @return true 如果有 ROR 或 Ringgold ID
  public boolean hasHighPriorityId() {
    return hasRorId() || hasRinggoldId();
  }

  /// 获取最高优先级的标识符值。
  ///
  /// 优先级：ROR > Ringgold > GRID
  ///
  /// @return 最高优先级的标识符值，如果都没有则返回 null
  public String getBestIdentifier() {
    if (hasRorId()) {
      return rorId;
    }
    if (hasRinggoldId()) {
      return ringgoldId;
    }
    if (hasGridId()) {
      return gridId;
    }
    return null;
  }

  /// 获取最高优先级标识符的类型。
  ///
  /// @return 标识符类型（"ror"、"ringgold"、"grid"），如果都没有则返回 null
  public String getBestIdentifierType() {
    if (hasRorId()) {
      return TYPE_ROR;
    }
    if (hasRinggoldId()) {
      return TYPE_RINGGOLD;
    }
    if (hasGridId()) {
      return TYPE_GRID;
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("AffiliationIdentifiers{");
    boolean first = true;
    if (hasRorId()) {
      sb.append("ror=").append(rorId);
      first = false;
    }
    if (hasRinggoldId()) {
      if (!first) sb.append(", ");
      sb.append("ringgold=").append(ringgoldId);
      first = false;
    }
    if (hasGridId()) {
      if (!first) sb.append(", ");
      sb.append("grid=").append(gridId);
    }
    return sb.append("}").toString();
  }

  /// 标识符接口，用于泛型解析。
  ///
  /// 实现此接口的类需提供 type() 和 value() 方法。
  public interface IdentifierLike {

    /// 获取标识符类型（如 "ror"、"ringgold"）。
    String type();

    /// 获取标识符值。
    String value();
  }
}
