package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.Department;
import dev.patra.identity.domain.account.model.vo.profile.JobTitle;
import dev.patra.identity.domain.account.model.vo.profile.LinkDraft;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.ParsedProfile;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/// 资料与隐私设置的字段规则（release spec 2.1.2 / 2.1.5）。
///
/// 所有字段一起查、错误一次报全（`InvalidUserFieldsException`，422），每个字段只报第一条。字节上限是字素上限 × 4，
/// 与昵称同一比例。和库里存的值规范化后完全相同的文本直接沿用、不过规则：清单类规则（拒绝词）以后会收紧，
/// 不能让存量值撞上新规则的人连只改简介都一直 422；URL 的规则是格式，不会变，照常校验。
public final class ProfilePolicy {

  /// 简介最多几个字素。
  public static final int BIO_MAX_GRAPHEMES = 200;

  /// 简介 UTF-8 最多几个字节。
  public static final int BIO_MAX_BYTES = 800;

  /// 简介最多几行。
  public static final int BIO_MAX_LINES = 3;

  /// 链接标题最多几个字素。
  public static final int LINK_TITLE_MAX_GRAPHEMES = 30;

  /// 链接标题 UTF-8 最多几个字节。
  public static final int LINK_TITLE_MAX_BYTES = 120;

  /// 链接 URL 最多几个码点。
  public static final int LINK_URL_MAX_CODE_POINTS = 300;

  /// 职称最多几个字素。
  public static final int JOB_TITLE_MAX_GRAPHEMES = 50;

  /// 职称 UTF-8 最多几个字节。
  public static final int JOB_TITLE_MAX_BYTES = 200;

  /// 科室最多几个字素。
  public static final int DEPARTMENT_MAX_GRAPHEMES = 100;

  /// 科室 UTF-8 最多几个字节。
  public static final int DEPARTMENT_MAX_BYTES = 400;

  /// 手填机构名最多几个字素。
  public static final int ORGANIZATION_NAME_MAX_GRAPHEMES = 200;

  /// 手填机构名 UTF-8 最多几个字节。
  public static final int ORGANIZATION_NAME_MAX_BYTES = 800;

  private final DisplayNamePolicy displayNamePolicy;

  /// 创建规则。
  ///
  /// @param displayNamePolicy 昵称规则
  public ProfilePolicy(DisplayNamePolicy displayNamePolicy) {
    this.displayNamePolicy =
        Objects.requireNonNull(displayNamePolicy, "displayNamePolicy 不能为 null");
  }

  /// 校验并规范化一份资料草稿。
  ///
  /// @param draft 原始输入
  /// @param current 库里当前的资料，用来判断哪些值没变
  /// @return 规范化之后的值；机构与关注领域还没有名称快照
  /// @throws InvalidUserFieldsException 任何字段不合法，带全部字段错误
  public ParsedProfile parse(ProfileDraft draft, UserProfile current) {
    Objects.requireNonNull(draft, "draft 不能为 null");
    Objects.requireNonNull(current, "current 不能为 null");
    List<FieldViolation> violations = new ArrayList<>();
    DisplayName displayName = parseDisplayName(draft.displayName(), current, violations);
    Bio bio = parseBio(draft.bio(), current, violations);
    List<ProfileLink> links = parseLinks(draft.links(), current, violations);
    Occupation occupation = parseOccupation(draft.occupation(), violations);
    String jobTitle =
        parseLine(
            draft.jobTitle(),
            current.getJobTitle().map(JobTitle::value),
            JOB_TITLE_MAX_GRAPHEMES,
            JOB_TITLE_MAX_BYTES,
            UserFieldViolations::jobTitleInvalidCharacter,
            UserFieldViolations::jobTitleTooLong,
            violations);
    RorId rorId = parseRorId(draft.organizationRorId(), violations);
    String organizationName =
        !isBlank(draft.organizationRorId())
            ? null
            : parseLine(
                draft.organizationName(),
                current
                    .getAffiliation()
                    .filter(Affiliation.FreeText.class::isInstance)
                    .map(Affiliation::name),
                ORGANIZATION_NAME_MAX_GRAPHEMES,
                ORGANIZATION_NAME_MAX_BYTES,
                UserFieldViolations::organizationNameInvalidCharacter,
                UserFieldViolations::organizationNameTooLong,
                violations);
    String department =
        parseLine(
            draft.department(),
            current.getDepartment().map(Department::value),
            DEPARTMENT_MAX_GRAPHEMES,
            DEPARTMENT_MAX_BYTES,
            UserFieldViolations::departmentInvalidCharacter,
            UserFieldViolations::departmentTooLong,
            violations);
    List<MeshUi> interests = parseResearchInterests(draft.researchInterests(), violations);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    return ParsedProfile.builder()
        .displayName(displayName)
        .bio(bio)
        .links(links)
        .occupation(occupation)
        .jobTitle(jobTitle == null ? null : JobTitle.of(jobTitle))
        .organizationRorId(rorId)
        .organizationName(organizationName)
        .department(department == null ? null : Department.of(department))
        .researchInterests(interests)
        .build();
  }

  /// 解析隐私设置，三项都必填，错误一次报全。
  ///
  /// @param profileVisibility 主页可见性：`PUBLIC` / `SIGNED_IN` / `SELF`
  /// @param professionalVisibility 专业背景可见性：`PUBLIC` / `SELF`
  /// @param searchIndexing 是否允许搜索引擎收录
  /// @return 隐私设置
  /// @throws InvalidUserFieldsException 缺失或取值不认识
  public PrivacySettings parsePrivacy(
      String profileVisibility, String professionalVisibility, Boolean searchIndexing) {
    List<FieldViolation> violations = new ArrayList<>();
    ProfileVisibility profile =
        parseEnum(
            profileVisibility,
            ProfileVisibility.class,
            UserFieldViolations.PROFILE_VISIBILITY,
            violations);
    ProfessionalVisibility professional =
        parseEnum(
            professionalVisibility,
            ProfessionalVisibility.class,
            UserFieldViolations.PROFESSIONAL_VISIBILITY,
            violations);
    if (searchIndexing == null) {
      violations.add(UserFieldViolations.privacyRequired(UserFieldViolations.SEARCH_INDEXING));
    }
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    return PrivacySettings.of(profile, professional, searchIndexing);
  }

  /// 昵称：空即清空；和库里相同沿用；否则走昵称规则。
  ///
  /// @param raw 输入
  /// @param current 当前资料
  /// @param violations 收集错误
  /// @return 昵称，清空或出错时为 `null`
  private DisplayName parseDisplayName(
      String raw, UserProfile current, List<FieldViolation> violations) {
    if (raw == null) {
      return null;
    }
    String normalized = DisplayName.normalize(raw);
    if (normalized.isEmpty()) {
      return null;
    }
    Optional<DisplayName> stored = current.getDisplayName();
    if (stored.isPresent() && stored.get().value().equals(normalized)) {
      return stored.get();
    }
    Optional<FieldViolation> violation = displayNamePolicy.validate(raw);
    if (violation.isPresent()) {
      violations.add(violation.get());
      return null;
    }
    return new DisplayName(normalized);
  }

  /// 简介：空即清空；和库里相同沿用；否则查拒绝字符（放行换行）、长度、行数。
  ///
  /// @param raw 输入
  /// @param current 当前资料
  /// @param violations 收集错误
  /// @return 简介，清空或出错时为 `null`
  private static Bio parseBio(String raw, UserProfile current, List<FieldViolation> violations) {
    if (raw == null) {
      return null;
    }
    String normalized = Bio.normalize(raw);
    if (normalized.isEmpty()) {
      return null;
    }
    Optional<Bio> stored = current.getBio();
    if (stored.isPresent() && stored.get().value().equals(normalized)) {
      return stored.get();
    }
    if (ProfileTextRules.hasRejectedCharacter(normalized, true)) {
      violations.add(UserFieldViolations.bioInvalidCharacter());
      return null;
    }
    if (ProfileTextRules.graphemeCount(normalized) > BIO_MAX_GRAPHEMES
        || ProfileTextRules.utf8Length(normalized) > BIO_MAX_BYTES) {
      violations.add(UserFieldViolations.bioTooLong());
      return null;
    }
    if (normalized.chars().filter(c -> c == '\n').count() + 1 > BIO_MAX_LINES) {
      violations.add(UserFieldViolations.bioTooManyLines());
      return null;
    }
    return new Bio(normalized);
  }

  /// 链接：超过 5 条只报一条 `TOO_MANY`；否则逐条查 URL 与标题。标题按下标对照库里同一下标的旧标题。
  ///
  /// @param drafts 输入
  /// @param current 当前资料
  /// @param violations 收集错误
  /// @return 合法的链接
  private static List<ProfileLink> parseLinks(
      List<LinkDraft> drafts, UserProfile current, List<FieldViolation> violations) {
    if (drafts.size() > UserProfile.MAX_LINKS) {
      violations.add(UserFieldViolations.linksTooMany());
      return List.of();
    }
    List<ProfileLink> stored = current.getLinks();
    List<ProfileLink> links = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < drafts.size(); i++) {
      LinkDraft draft = drafts.get(i);
      String url = parseUrl(draft.url(), i, seen, violations);
      int index = i;
      String title =
          parseLine(
              draft.title(),
              i < stored.size() ? Optional.ofNullable(stored.get(i).title()) : Optional.empty(),
              LINK_TITLE_MAX_GRAPHEMES,
              LINK_TITLE_MAX_BYTES,
              () -> UserFieldViolations.linkTitleInvalidCharacter(index),
              () -> UserFieldViolations.linkTitleTooLong(index),
              violations);
      if (url != null) {
        links.add(ProfileLink.of(title, url));
      }
    }
    return links;
  }

  /// 一条 URL：空、太长、格式不对、与前面重复（大小写不敏感）。
  ///
  /// @param raw 输入
  /// @param index 下标
  /// @param seen 前面出现过的 URL（小写）
  /// @param violations 收集错误
  /// @return 去首尾空白后的 URL，出错时为 `null`
  private static String parseUrl(
      String raw, int index, Set<String> seen, List<FieldViolation> violations) {
    String url = raw == null ? "" : ProfileTextRules.stripSpaces(raw);
    if (url.isEmpty()) {
      violations.add(UserFieldViolations.linkUrlRequired(index));
      return null;
    }
    if (url.codePointCount(0, url.length()) > LINK_URL_MAX_CODE_POINTS) {
      violations.add(UserFieldViolations.linkUrlTooLong(index));
      return null;
    }
    if (!isAcceptableUrl(url)) {
      violations.add(UserFieldViolations.linkUrlInvalidFormat(index));
      return null;
    }
    if (!seen.add(url.toLowerCase(Locale.ROOT))) {
      violations.add(UserFieldViolations.linkUrlDuplicate(index));
      return null;
    }
    return url;
  }

  /// URL 是否可收：不含空白与控制字符；能按 RFC 3986 解析；scheme 是 `http` / `https`；有主机名；不带用户信息
  /// （`https://patra.dev@evil.example` 这类冒充）。
  ///
  /// @param url 去首尾空白后的 URL
  /// @return 可收时为 `true`
  private static boolean isAcceptableUrl(String url) {
    if (url.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp))) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    return scheme != null
        && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        && uri.getHost() != null
        && uri.getRawUserInfo() == null;
  }

  /// 身份大类：空即清空；必须是八个取值之一（区分大小写）。
  ///
  /// @param raw 输入
  /// @param violations 收集错误
  /// @return 身份大类，清空或出错时为 `null`
  private static Occupation parseOccupation(String raw, List<FieldViolation> violations) {
    if (isBlank(raw)) {
      return null;
    }
    try {
      return Occupation.valueOf(raw.strip());
    } catch (IllegalArgumentException e) {
      violations.add(UserFieldViolations.occupationInvalidFormat());
      return null;
    }
  }

  /// 机构的 ROR ID：空即没有；否则去空白转小写后查格式。
  ///
  /// @param raw 输入
  /// @param violations 收集错误
  /// @return ROR ID，没有或出错时为 `null`
  private static RorId parseRorId(String raw, List<FieldViolation> violations) {
    if (isBlank(raw)) {
      return null;
    }
    String normalized = RorId.normalize(raw);
    if (!RorId.isValid(normalized)) {
      violations.add(UserFieldViolations.organizationRorIdInvalidFormat());
      return null;
    }
    return new RorId(normalized);
  }

  /// 关注领域：超过 10 个只报一条 `TOO_MANY`；否则逐个查空、格式、重复。
  ///
  /// @param raws 输入
  /// @param violations 收集错误
  /// @return 合法的主题词 UI，按输入顺序
  private static List<MeshUi> parseResearchInterests(
      List<String> raws, List<FieldViolation> violations) {
    if (raws.size() > UserProfile.MAX_RESEARCH_INTERESTS) {
      violations.add(UserFieldViolations.researchInterestsTooMany());
      return List.of();
    }
    List<MeshUi> uis = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < raws.size(); i++) {
      String stripped = raws.get(i) == null ? "" : ProfileTextRules.stripSpaces(raws.get(i));
      if (stripped.isEmpty()) {
        violations.add(UserFieldViolations.researchInterestRequired(i));
      } else if (!MeshUi.isValid(stripped)) {
        violations.add(UserFieldViolations.researchInterestInvalidFormat(i));
      } else if (!seen.add(stripped)) {
        violations.add(UserFieldViolations.researchInterestDuplicate(i));
      } else {
        uis.add(new MeshUi(stripped));
      }
    }
    return uis;
  }

  /// 单行文本（职称、科室、手填机构名、链接标题）：规范化同昵称；空即清空；和库里相同沿用；
  /// 否则查拒绝字符、字素与字节上限。
  ///
  /// @param raw 输入
  /// @param stored 库里的值
  /// @param maxGraphemes 字素上限
  /// @param maxBytes 字节上限
  /// @param invalidCharacter 拒绝字符时的错误
  /// @param tooLong 太长时的错误
  /// @param violations 收集错误
  /// @return 规范化之后的值，清空或出错时为 `null`
  private static String parseLine(
      String raw,
      Optional<String> stored,
      int maxGraphemes,
      int maxBytes,
      Supplier<FieldViolation> invalidCharacter,
      Supplier<FieldViolation> tooLong,
      List<FieldViolation> violations) {
    if (raw == null) {
      return null;
    }
    String normalized = DisplayName.normalize(raw);
    if (normalized.isEmpty()) {
      return null;
    }
    if (stored.filter(normalized::equals).isPresent()) {
      return normalized;
    }
    if (ProfileTextRules.hasRejectedCharacter(normalized, false)) {
      violations.add(invalidCharacter.get());
      return null;
    }
    if (ProfileTextRules.graphemeCount(normalized) > maxGraphemes
        || ProfileTextRules.utf8Length(normalized) > maxBytes) {
      violations.add(tooLong.get());
      return null;
    }
    return normalized;
  }

  /// 隐私设置的枚举项：缺失 `REQUIRED`，取值不认识 `INVALID_FORMAT`（区分大小写）。
  ///
  /// @param raw 输入
  /// @param type 枚举类型
  /// @param field 字段名
  /// @param violations 收集错误
  /// @param <E> 枚举类型
  /// @return 取值，出错时为 `null`
  private static <E extends Enum<E>> E parseEnum(
      String raw, Class<E> type, String field, List<FieldViolation> violations) {
    if (isBlank(raw)) {
      violations.add(UserFieldViolations.privacyRequired(field));
      return null;
    }
    try {
      return Enum.valueOf(type, raw.strip());
    } catch (IllegalArgumentException e) {
      violations.add(UserFieldViolations.privacyInvalidFormat(field));
      return null;
    }
  }

  /// `null` 或只有空白。
  ///
  /// @param raw 输入
  /// @return 为空时为 `true`
  private static boolean isBlank(String raw) {
    return raw == null || raw.isBlank();
  }
}
