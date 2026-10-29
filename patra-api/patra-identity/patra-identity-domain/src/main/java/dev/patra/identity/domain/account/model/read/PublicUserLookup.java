package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.vo.Username;
import java.util.Objects;

/// 按用户名查公开主页的结果：找到了，或者这是个保留期内的旧名、要跳到新名。
/// 不存在和不可见都不是结果，是 404 的异常。
public sealed interface PublicUserLookup permits PublicUserLookup.Found, PublicUserLookup.Moved {

  /// 找到了，已按请求者裁剪。
  ///
  /// @param view 裁剪后的主页
  record Found(PublicUserView view) implements PublicUserLookup {

    /// 校验非空。
    public Found {
      Objects.requireNonNull(view, "view 不能为 null");
    }

    /// 工厂方法。
    ///
    /// @param view 裁剪后的主页
    /// @return 结果
    public static Found of(PublicUserView view) {
      return new Found(view);
    }
  }

  /// 旧名，跳到原主的当前用户名。
  ///
  /// @param current 当前用户名
  record Moved(Username current) implements PublicUserLookup {

    /// 校验非空。
    public Moved {
      Objects.requireNonNull(current, "current 不能为 null");
    }

    /// 工厂方法。
    ///
    /// @param current 当前用户名
    /// @return 结果
    public static Moved of(Username current) {
      return new Moved(current);
    }
  }
}
