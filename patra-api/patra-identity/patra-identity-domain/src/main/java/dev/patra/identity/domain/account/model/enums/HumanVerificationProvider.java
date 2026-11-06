package dev.patra.identity.domain.account.model.enums;

/// 人机验证的厂商。没有“关闭”这个值，避免配置失误把验证关掉。
public enum HumanVerificationProvider {
  /// Cloudflare Turnstile，默认通道。
  TURNSTILE("turnstile"),
  /// TRUSTTURN，备用通道。运营方和数据处理方式都没有公开说明。
  TRUST_TURN("trustturn");

  private final String wireValue;

  /// 创建枚举值。
  ///
  /// @param wireValue 接口里的取值
  HumanVerificationProvider(String wireValue) {
    this.wireValue = wireValue;
  }

  /// 接口里的取值。
  ///
  /// @return 取值
  public String wireValue() {
    return wireValue;
  }
}
