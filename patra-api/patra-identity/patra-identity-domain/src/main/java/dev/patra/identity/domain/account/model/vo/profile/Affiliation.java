package dev.patra.identity.domain.account.model.vo.profile;

import java.util.Objects;

/// 机构：从 ROR 选的（存 ROR ID + catalog 给的名称快照），或搜不到时手填的自由文本。
public sealed interface Affiliation permits Affiliation.Registered, Affiliation.FreeText {

  /// 展示用的名称。
  ///
  /// @return 名称
  String name();

  /// 从 ROR 选的机构。
  ///
  /// @param rorId ROR ID
  /// @param name catalog 给的名称快照（中文名优先）
  record Registered(RorId rorId, String name) implements Affiliation {

    /// 校验非空。
    public Registered {
      Objects.requireNonNull(rorId, "rorId 不能为 null");
      Objects.requireNonNull(name, "name 不能为 null");
    }

    /// 工厂方法。
    ///
    /// @param rorId ROR ID
    /// @param name 名称快照
    /// @return 机构
    public static Registered of(RorId rorId, String name) {
      return new Registered(rorId, name);
    }
  }

  /// 手填的机构名。
  ///
  /// @param name 规范化之后的名称
  record FreeText(String name) implements Affiliation {

    /// 校验非空。
    public FreeText {
      Objects.requireNonNull(name, "name 不能为 null");
    }

    /// 工厂方法。
    ///
    /// @param name 名称
    /// @return 机构
    public static FreeText of(String name) {
      return new FreeText(name);
    }
  }
}
