package com.patra.catalog.domain.model.vo.venue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/// 开放获取（Open Access）信息值对象。
///
/// 合并 OA 状态和 APC（文章处理费）定价信息，提供统一的开放获取相关数据访问。
///
/// **设计原则**：
///
/// - 不可变性：Record + 防御性复制
/// - 聚合语义：将原本分散在 VenueDetail（OA 状态）和 ApcInfo（APC 价格）的数据统一管理
/// - 便捷方法：提供 OA 类型判断和价格查询的快捷方法
///
/// **包含的数据**：
///
/// | 分类 | 字段 | 来源 |
/// |------|------|------|
/// | OA 状态 | isOa, isInDoaj | OpenAlex |
/// | OA 类型 | oaType (gold/green/hybrid/bronze/diamond) | OpenAlex |
/// | APC 价格 | apcUsd, apcPrices | OpenAlex/DOAJ |
///
/// **使用示例**：
///
/// ```java
/// // 创建完整的 OA 信息
/// OpenAccessInfo info = OpenAccessInfo.of(
///     true, true, "gold", 3000,
///     List.of(ApcPrice.of(3000, "USD"), ApcPrice.of(2500, "EUR"))
/// );
///
/// // 非 OA 期刊
/// OpenAccessInfo closedAccess = OpenAccessInfo.notOpenAccess();
///
/// // 仅 OA 状态（无 APC 信息）
/// OpenAccessInfo greenOa = OpenAccessInfo.ofOaStatus(true, false, "green");
/// ```
///
/// @param isOa 是否为开放获取
/// @param isInDoaj 是否在 DOAJ（Directory of Open Access Journals）中
/// @param oaType OA 类型（gold/green/hybrid/bronze/diamond）
/// @param apcUsd APC 美元价格（基准价格）
/// @param apcPrices 多货币价格列表
/// @author linqibin
/// @since 0.7.0
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAccessInfo(
    boolean isOa, boolean isInDoaj, String oaType, Integer apcUsd, List<ApcPrice> apcPrices)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：确保列表不可变。
  public OpenAccessInfo {
    apcPrices = apcPrices != null ? List.copyOf(apcPrices) : List.of();
  }

  // ========== 工厂方法 ==========

  /// 创建完整的开放获取信息。
  ///
  /// @param isOa 是否为开放获取
  /// @param isInDoaj 是否在 DOAJ 中
  /// @param oaType OA 类型
  /// @param apcUsd APC 美元价格
  /// @param apcPrices 多货币价格列表
  /// @return 开放获取信息值对象
  public static OpenAccessInfo of(
      boolean isOa, boolean isInDoaj, String oaType, Integer apcUsd, List<ApcPrice> apcPrices) {
    return new OpenAccessInfo(isOa, isInDoaj, oaType, apcUsd, apcPrices);
  }

  /// 创建非开放获取信息（Closed Access）。
  ///
  /// @return 非 OA 信息值对象
  public static OpenAccessInfo notOpenAccess() {
    return new OpenAccessInfo(false, false, null, null, List.of());
  }

  /// 创建仅 OA 状态的信息（无 APC 数据）。
  ///
  /// 适用于 Green OA 等无需支付 APC 的开放获取类型。
  ///
  /// @param isOa 是否为开放获取
  /// @param isInDoaj 是否在 DOAJ 中
  /// @param oaType OA 类型
  /// @return 开放获取信息值对象
  public static OpenAccessInfo ofOaStatus(boolean isOa, boolean isInDoaj, String oaType) {
    return new OpenAccessInfo(isOa, isInDoaj, oaType, null, List.of());
  }

  /// 创建仅 APC 信息的 OA 信息。
  ///
  /// 适用于已知 APC 价格但不确定具体 OA 类型的场景。
  /// 默认 isOa = true（有 APC 通常意味着是 OA 期刊）。
  ///
  /// @param apcUsd APC 美元价格
  /// @param apcPrices 多货币价格列表
  /// @return 开放获取信息值对象
  public static OpenAccessInfo ofApc(Integer apcUsd, List<ApcPrice> apcPrices) {
    return new OpenAccessInfo(true, false, null, apcUsd, apcPrices);
  }

  // ========== OA 类型判断 ==========

  /// 判断是否为 Gold OA（金色开放获取）。
  ///
  /// Gold OA 指文章在发表时即完全开放获取，通常需要支付 APC。
  ///
  /// @return true 如果是 Gold OA
  @JsonIgnore
  public boolean isGoldOa() {
    return "gold".equalsIgnoreCase(oaType);
  }

  /// 判断是否为 Green OA（绿色开放获取）。
  ///
  /// Green OA 指作者可以在机构库或预印本服务器上自行存档。
  ///
  /// @return true 如果是 Green OA
  @JsonIgnore
  public boolean isGreenOa() {
    return "green".equalsIgnoreCase(oaType);
  }

  /// 判断是否为 Hybrid OA（混合开放获取）。
  ///
  /// Hybrid OA 指订阅型期刊中提供开放获取选项的文章。
  ///
  /// @return true 如果是 Hybrid OA
  @JsonIgnore
  public boolean isHybridOa() {
    return "hybrid".equalsIgnoreCase(oaType);
  }

  /// 判断是否为 Bronze OA（青铜开放获取）。
  ///
  /// Bronze OA 指文章可免费阅读但无明确许可证。
  ///
  /// @return true 如果是 Bronze OA
  @JsonIgnore
  public boolean isBronzeOa() {
    return "bronze".equalsIgnoreCase(oaType);
  }

  /// 判断是否为 Diamond OA（钻石开放获取）。
  ///
  /// Diamond OA 指既不向作者收费也不向读者收费的开放获取。
  ///
  /// @return true 如果是 Diamond OA
  @JsonIgnore
  public boolean isDiamondOa() {
    return "diamond".equalsIgnoreCase(oaType);
  }

  // ========== APC 相关方法 ==========

  /// 判断是否有 APC 费用。
  ///
  /// @return true 如果有大于 0 的 APC 费用
  public boolean hasApc() {
    return apcUsd != null && apcUsd > 0;
  }

  /// 判断是否有多货币价格。
  ///
  /// @return true 如果有多货币价格列表
  public boolean hasMultiplePrices() {
    return apcPrices != null && !apcPrices.isEmpty();
  }

  /// 获取指定货币的价格。
  ///
  /// @param currency 货币代码（如 USD、EUR、CNY）
  /// @return 价格，如果不存在则返回 null
  public Integer getPriceByCurrency(String currency) {
    if (apcPrices == null || currency == null) {
      return null;
    }
    return apcPrices.stream()
        .filter(p -> p.currency().equalsIgnoreCase(currency))
        .map(ApcPrice::price)
        .findFirst()
        .orElse(null);
  }

  // ========== 内嵌值对象 ==========

  /// APC 单货币价格值对象。
  ///
  /// @param price 价格金额
  /// @param currency 货币代码（ISO 4217，如 USD、EUR、CNY）
  public record ApcPrice(int price, String currency) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /// 紧凑构造器：验证价格信息。
    ///
    /// @throws IllegalArgumentException 如果价格为负或货币代码为空
    public ApcPrice {
      if (price < 0) {
        throw new IllegalArgumentException("APC 价格不能为负数");
      }
      if (currency == null || currency.isBlank()) {
        throw new IllegalArgumentException("货币代码不能为空");
      }
    }

    /// 创建 APC 价格。
    ///
    /// @param price 价格金额
    /// @param currency 货币代码
    /// @return APC 价格值对象
    public static ApcPrice of(int price, String currency) {
      return new ApcPrice(price, currency);
    }
  }
}
