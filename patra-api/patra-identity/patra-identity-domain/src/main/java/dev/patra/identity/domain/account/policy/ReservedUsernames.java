package dev.patra.identity.domain.account.policy;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// 不能注册的用户名：门户的路由段、角色词、站名与数据源名。
///
/// 匹配前先做同形归一（`0 → o`、`1 → i`、`3 → e`、`5 → s`）再去掉 `_` 和 `-`，与清单整词比较：
/// `adm1n`、`ad-min`、`a_d_m_i_n` 都命中 `admin`，`admin2` 不命中。`l` 不归一到 `i`：它是合法字母，
/// 归一会误伤 `hello`、`lily` 这类名字。清单条目在这里以折叠后的形式保存。
public final class ReservedUsernames {

  private static final List<String> ROUTE_SEGMENTS =
      List.of(
          "api",
          "app",
          "auth",
          "login",
          "logout",
          "register",
          "signup",
          "signin",
          "signout",
          "settings",
          "account",
          "profile",
          "me",
          "u",
          "user",
          "users",
          "papers",
          "paper",
          "journals",
          "journal",
          "search",
          "missing",
          "about",
          "help",
          "docs",
          "terms",
          "privacy",
          "legal",
          "contact",
          "static",
          "assets",
          "public",
          "admin",
          "dashboard",
          "console",
          "internal",
          "actuator",
          "health",
          "status",
          "blog",
          "news",
          "feed",
          "explore",
          "notifications",
          "messages",
          "inbox",
          "collections",
          "favorites",
          "bookmarks",
          "following",
          "followers",
          "discussions",
          "comments",
          "tags",
          "topics",
          "verify",
          "reset",
          "password",
          "email");

  private static final List<String> ROLE_WORDS =
      List.of(
          "admin",
          "administrator",
          "staff",
          "official",
          "system",
          "support",
          "moderator",
          "mod",
          "root",
          "owner",
          "operator",
          "security",
          "team",
          "bot",
          "robot",
          "anonymous",
          "guest",
          "nobody",
          "everyone",
          "null",
          "undefined",
          "deleted",
          "deleted-user",
          "unknown",
          "test");

  private static final List<String> SITE_AND_SOURCE_NAMES =
      List.of(
          "patra",
          "papertrace",
          "pubmed",
          "ncbi",
          "nlm",
          "nih",
          "pmc",
          "europepmc",
          "epmc",
          "crossref",
          "medline",
          "mesh",
          "doi",
          "orcid",
          "ror",
          "openalex",
          "scopus",
          "wos",
          "clarivate",
          "elsevier",
          "springer",
          "wiley",
          "nature",
          "lancet",
          "nejm",
          "jama",
          "bmj",
          "who",
          "cdc",
          "fda",
          "nmpa",
          "nhc",
          "cnki",
          "wanfang");

  private static final Set<String> FOLDED_SITE_AND_SOURCE_NAMES = foldAll(SITE_AND_SOURCE_NAMES);

  private static final Set<String> ALL =
      foldAll(
          Stream.of(ROUTE_SEGMENTS, ROLE_WORDS, SITE_AND_SOURCE_NAMES)
              .flatMap(List::stream)
              .toList());

  /// 只有静态方法。
  private ReservedUsernames() {}

  /// 同形归一并去掉分隔符。
  ///
  /// @param normalized 规范化之后（小写）的用户名
  /// @return 折叠后的形式
  public static String fold(String normalized) {
    return normalized
        .replace('0', 'o')
        .replace('1', 'i')
        .replace('3', 'e')
        .replace('5', 's')
        .replace("_", "")
        .replace("-", "");
  }

  /// 是否命中保留字。
  ///
  /// @param normalized 规范化之后（小写）的用户名
  /// @return 折叠后整词命中清单时为 `true`
  public static boolean contains(String normalized) {
    return ALL.contains(fold(normalized));
  }

  /// 站名与数据源名这一组（折叠后），昵称的拒绝词复用。
  ///
  /// @return 不可变集合的副本
  public static Set<String> siteAndSourceNames() {
    return Set.copyOf(FOLDED_SITE_AND_SOURCE_NAMES);
  }

  /// 把清单逐条折叠成集合。
  ///
  /// @param words 清单
  /// @return 折叠后的集合，重复的只留一个
  private static Set<String> foldAll(List<String> words) {
    return Set.copyOf(words.stream().map(ReservedUsernames::fold).toList());
  }
}
