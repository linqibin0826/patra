package dev.patra.identity.domain.account.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 第三方登录平台。枚举名存进 `idn_user_external_identity.provider`，小写名是对外的 `{provider}` 和注册 ID。
public enum IdentityProvider {
  /// Google（OIDC）。
  GOOGLE("Google"),

  /// GitHub（纯 OAuth2）。
  GITHUB("GitHub"),

  /// ORCID（OIDC）。
  ORCID("ORCID");

  /// ORCID iD：可能带 `https://orcid.org/` 或沙箱的地址前缀，只取末尾的裸 iD。
  private static final Pattern ORCID_ID =
      Pattern.compile(
          "^(?:https?://(?:sandbox\\.)?orcid\\.org/)?(\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX])$");

  private final String displayName;

  /// 创建枚举值。
  ///
  /// @param displayName 给人看的平台名
  IdentityProvider(String displayName) {
    this.displayName = displayName;
  }

  /// 给人看的平台名：进安全邮件。
  ///
  /// @return 平台名
  public String displayName() {
    return displayName;
  }

  /// 对外的小写名：路径里的 `{provider}`、Spring 的注册 ID、JSON 里的 `provider`。
  ///
  /// @return 小写名
  public String registrationId() {
    return name().toLowerCase(Locale.ROOT);
  }

  /// 按小写名找平台。
  ///
  /// @param registrationId 小写名，可以为 `null`
  /// @return 平台；不认识时为空
  public static Optional<IdentityProvider> fromRegistrationId(String registrationId) {
    return Arrays.stream(values())
        .filter(provider -> provider.registrationId().equals(registrationId))
        .findFirst();
  }

  /// 把平台给的主体转成存库的规范形式：ORCID 只留裸 iD，其他平台原样。
  ///
  /// @param rawSubject 平台给的主体，可以为 `null`
  /// @return 规范形式；空白或形状不对时为空
  public Optional<String> normalizeSubject(String rawSubject) {
    if (rawSubject == null || rawSubject.isBlank()) {
      return Optional.empty();
    }
    if (this != ORCID) {
      return Optional.of(rawSubject);
    }
    Matcher matcher = ORCID_ID.matcher(rawSubject);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
