package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/// APC（文章处理费）信息值对象。封装载体的开放获取费用信息。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 多货币支持：支持 USD 基准价格和多货币价格列表
/// - 可选性：并非所有载体都有 APC 信息
///
/// 数据来源：
///
/// 主要来自 OpenAlex Source 的 `apc_usd` 和 `apc_prices` 字段。
///
/// 使用示例：
///
/// ```java
/// // 创建 APC 信息（美元价格 + 多货币）
/// ApcInfo apc = ApcInfo.of(3000, List.of(
///     ApcInfo.ApcPrice.of(3000, "USD"),
///     ApcInfo.ApcPrice.of(2500, "EUR"),
///     ApcInfo.ApcPrice.of(20000, "CNY")
/// ));
///
/// // 仅美元价格
/// ApcInfo usdOnly = ApcInfo.ofUsd(3000);
/// ```
///
/// @param usd 美元价格（基准价格）
/// @param prices 多货币价格列表
/// @author linqibin
/// @since 0.1.0
public record ApcInfo(Integer usd, List<ApcPrice> prices) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：确保列表不可变。
  public ApcInfo {
    // 防御性复制，确保不可变性
    prices = prices != null ? List.copyOf(prices) : List.of();
  }

  /// 创建 APC 信息（带多货币价格）。
  ///
  /// @param usd 美元价格
  /// @param prices 多货币价格列表
  /// @return APC 信息值对象
  public static ApcInfo of(Integer usd, List<ApcPrice> prices) {
    return new ApcInfo(usd, prices);
  }

  /// 创建仅美元价格的 APC 信息。
  ///
  /// @param usd 美元价格
  /// @return APC 信息值对象
  public static ApcInfo ofUsd(Integer usd) {
    return new ApcInfo(usd, List.of());
  }

  /// 判断是否有美元价格。
  ///
  /// @return true 如果有美元价格
  public boolean hasUsdPrice() {
    return usd != null;
  }

  /// 判断是否有多货币价格。
  ///
  /// @return true 如果有多货币价格
  public boolean hasMultiplePrices() {
    return prices != null && !prices.isEmpty();
  }

  /// 获取指定货币的价格。
  ///
  /// @param currency 货币代码（如 USD、EUR、CNY）
  /// @return 价格，如果不存在则返回 null
  public Integer getPriceByCurrency(String currency) {
    if (prices == null) {
      return null;
    }
    return prices.stream()
        .filter(p -> p.currency().equalsIgnoreCase(currency))
        .map(ApcPrice::price)
        .findFirst()
        .orElse(null);
  }

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
      Assert.isTrue(price >= 0, "APC 价格不能为负数");
      Assert.notBlank(currency, "货币代码不能为空");
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
