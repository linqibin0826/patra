package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.LinkDraft;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.ParsedProfile;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/// ProfilePolicy 单元测试：每个字段的边界与原因码、一次报全、与库里相同的值不重新校验、隐私设置的解析。
///
/// 不可见字符一律用码点构造（`cp(...)`），原因见 `DisplayNamePolicyTest`。
class ProfilePolicyTest {

  /// 👨‍👩‍👧：5 个码点、18 字节、1 个字素。
  private static final String FAMILY = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467);

  private final ProfilePolicy policy = new ProfilePolicy(new DisplayNamePolicy());

  private final UserProfile empty = UserProfile.create(1L);

  // ===== 简介 =====

  @Test
  void should_accept_bio_at_200_graphemes() {
    assertThat(parse(draft().bio("张".repeat(200))).bio()).isEqualTo(new Bio("张".repeat(200)));
  }

  @Test
  void should_reject_bio_over_200_graphemes() {
    assertThat(violations(draft().bio("张".repeat(201)))).containsExactly("bio/TOO_LONG");
  }

  @Test
  void should_reject_bio_over_800_bytes() {
    assertThat(violations(draft().bio(FAMILY.repeat(45)))).containsExactly("bio/TOO_LONG");
  }

  @Test
  void should_reject_fourth_line() {
    assertThat(violations(draft().bio("a\nb\nc\nd"))).containsExactly("bio/TOO_MANY_LINES");
  }

  @Test
  void should_count_crlf_as_one_line_break() {
    assertThat(parse(draft().bio("a\r\nb\r\nc")).bio()).isEqualTo(new Bio("a\nb\nc"));
  }

  @Test
  void should_clear_blank_bio() {
    assertThat(parse(draft().bio("  \n ")).bio()).isNull();
  }

  @Test
  void should_reject_control_character_in_bio() {
    assertThat(violations(draft().bio("a" + cp(0x07) + "b")))
        .containsExactly("bio/INVALID_CHARACTER");
  }

  // ===== 链接 =====

  @Test
  void should_reject_sixth_link() {
    List<LinkDraft> six =
        IntStream.range(0, 6).mapToObj(i -> LinkDraft.of(null, "https://a.org/" + i)).toList();

    assertThat(violations(draft().links(six))).containsExactly("links/TOO_MANY");
  }

  @Test
  void should_accept_url_at_300_code_points() {
    String url = "https://a.org/" + "x".repeat(286);

    assertThat(parse(draft().links(List.of(LinkDraft.of(null, url)))).links())
        .containsExactly(ProfileLink.of(null, url));
  }

  @Test
  void should_reject_url_over_300() {
    String url = "https://a.org/" + "x".repeat(287);

    assertThat(violations(draft().links(List.of(LinkDraft.of(null, url)))))
        .containsExactly("links[0].url/TOO_LONG");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://x.org",
        "javascript:alert(1)",
        "data:text/html,x",
        "https:///x",
        "https://patra.dev@evil.example",
        "https://a.org/x y",
        "not a url"
      })
  void should_reject_malformed_or_unsafe_urls(String url) {
    assertThat(violations(draft().links(List.of(LinkDraft.of(null, url)))))
        .containsExactly("links[0].url/INVALID_FORMAT");
  }

  @Test
  void should_accept_upper_case_scheme_and_strip_url() {
    assertThat(
            parse(draft().links(List.of(LinkDraft.of(null, "  HTTPS://Example.org/A  ")))).links())
        .containsExactly(ProfileLink.of(null, "HTTPS://Example.org/A"));
  }

  @Test
  void should_reject_duplicate_url_case_insensitively() {
    List<LinkDraft> links =
        List.of(LinkDraft.of(null, "https://a.org/x"), LinkDraft.of(null, "HTTPS://A.ORG/x"));

    assertThat(violations(draft().links(links))).containsExactly("links[1].url/DUPLICATE");
  }

  @Test
  void should_reject_empty_url() {
    assertThat(violations(draft().links(List.of(LinkDraft.of("标题", "  ")))))
        .containsExactly("links[0].url/REQUIRED");
  }

  @Test
  void should_allow_missing_title_and_normalize_title() {
    List<LinkDraft> links =
        List.of(LinkDraft.of("  ", "https://a.org"), LinkDraft.of(" 实验室  主页 ", "https://b.org"));

    assertThat(parse(draft().links(links)).links())
        .containsExactly(
            ProfileLink.of(null, "https://a.org"), ProfileLink.of("实验室 主页", "https://b.org"));
  }

  @Test
  void should_reject_title_over_30_graphemes() {
    assertThat(violations(draft().links(List.of(LinkDraft.of("字".repeat(31), "https://a.org")))))
        .containsExactly("links[0].title/TOO_LONG");
  }

  // ===== 身份大类、职称、科室 =====

  @ParameterizedTest
  @EnumSource(Occupation.class)
  void should_accept_every_occupation(Occupation occupation) {
    assertThat(parse(draft().occupation(occupation.name())).occupation()).isEqualTo(occupation);
  }

  @Test
  void should_reject_lower_case_occupation() {
    assertThat(violations(draft().occupation("physician")))
        .containsExactly("occupation/INVALID_FORMAT");
  }

  @Test
  void should_clear_blank_occupation() {
    assertThat(parse(draft().occupation(" ")).occupation()).isNull();
  }

  @Test
  void should_bound_job_title_at_50_graphemes() {
    assertThat(parse(draft().jobTitle("职".repeat(50))).jobTitle().value()).hasSize(50);
    assertThat(violations(draft().jobTitle("职".repeat(51)))).containsExactly("jobTitle/TOO_LONG");
  }

  @Test
  void should_bound_department_at_100_graphemes() {
    assertThat(parse(draft().department("科".repeat(100))).department().value()).hasSize(100);
    assertThat(violations(draft().department("科".repeat(101))))
        .containsExactly("department/TOO_LONG");
  }

  @Test
  void should_reject_zero_width_space_in_job_title() {
    assertThat(violations(draft().jobTitle("主治" + cp(0x200B) + "医师")))
        .containsExactly("jobTitle/INVALID_CHARACTER");
  }

  // ===== 机构 =====

  @Test
  void should_fold_ror_id_to_lower_case() {
    ParsedProfile parsed = parse(draft().organizationRorId("04JZTAG35"));

    assertThat(parsed.organizationRorId()).isEqualTo(RorId.of("04jztag35"));
    assertThat(parsed.organizationName()).isNull();
  }

  @Test
  void should_reject_ror_url() {
    assertThat(violations(draft().organizationRorId("https://ror.org/04jztag35")))
        .containsExactly("organization.rorId/INVALID_FORMAT");
  }

  @Test
  void should_ignore_name_when_ror_id_present() {
    ParsedProfile parsed =
        parse(draft().organizationRorId("04jztag35").organizationName("字".repeat(201)));

    assertThat(parsed.organizationRorId()).isEqualTo(RorId.of("04jztag35"));
    assertThat(parsed.organizationName()).isNull();
  }

  @Test
  void should_bound_free_text_organization_at_200() {
    assertThat(parse(draft().organizationName("院".repeat(200))).organizationName()).hasSize(200);
    assertThat(violations(draft().organizationName("院".repeat(201))))
        .containsExactly("organization.name/TOO_LONG");
  }

  @Test
  void should_have_no_organization_when_both_blank() {
    ParsedProfile parsed = parse(draft().organizationRorId(" ").organizationName(""));

    assertThat(parsed.organizationRorId()).isNull();
    assertThat(parsed.organizationName()).isNull();
  }

  // ===== 关注领域 =====

  @Test
  void should_bound_research_interests_at_ten() {
    List<String> ten = IntStream.range(1, 11).mapToObj("D%06d"::formatted).toList();
    List<String> eleven = IntStream.range(1, 12).mapToObj("D%06d"::formatted).toList();

    assertThat(parse(draft().researchInterests(ten)).researchInterests()).hasSize(10);
    assertThat(violations(draft().researchInterests(eleven)))
        .containsExactly("researchInterests/TOO_MANY");
  }

  @Test
  void should_reject_malformed_duplicate_and_empty_uis() {
    assertThat(violations(draft().researchInterests(List.of("d003920"))))
        .containsExactly("researchInterests[0]/INVALID_FORMAT");
    assertThat(violations(draft().researchInterests(List.of("D003920", " D003920 "))))
        .containsExactly("researchInterests[1]/DUPLICATE");
    assertThat(violations(draft().researchInterests(List.of(" "))))
        .containsExactly("researchInterests[0]/REQUIRED");
  }

  @Test
  void should_keep_interest_order_and_accept_nine_digit_ui() {
    assertThat(
            parse(draft().researchInterests(List.of("D000072716", "D003920"))).researchInterests())
        .containsExactly(MeshUi.of("D000072716"), MeshUi.of("D003920"));
  }

  // ===== 昵称 =====

  @Test
  void should_clear_null_or_blank_display_name() {
    assertThat(parse(draft().displayName(null)).displayName()).isNull();
    assertThat(parse(draft().displayName("  ")).displayName()).isNull();
  }

  @Test
  void should_validate_display_name_with_display_name_policy() {
    assertThat(violations(draft().displayName("张医生"))).containsExactly("displayName/RESERVED");
    assertThat(parse(draft().displayName(" 陈宇 ")).displayName()).isEqualTo(new DisplayName("陈宇"));
  }

  // ===== 一次报全 =====

  @Test
  void should_report_all_fields_at_once() {
    ProfileDraft.ProfileDraftBuilder draft =
        draft()
            .bio("张".repeat(201))
            .links(List.of(LinkDraft.of(null, "https://a.org"), LinkDraft.of(null, "ftp://b")))
            .researchInterests(List.of("D000001", "D000002", "D000003", "bad"));

    assertThat(violations(draft))
        .containsExactly(
            "bio/TOO_LONG", "links[1].url/INVALID_FORMAT", "researchInterests[3]/INVALID_FORMAT");
  }

  // ===== 与库里相同的值不重新校验 =====

  @Test
  void should_keep_stored_display_name_even_if_rules_tightened() {
    UserProfile current = stored().displayName(new DisplayName("Dr Strange")).build();

    assertThat(policy.parse(draft().displayName("Dr Strange").build(), current).displayName())
        .isEqualTo(new DisplayName("Dr Strange"));
    assertThat(violations(draft().displayName("Dr Who"), current))
        .containsExactly("displayName/RESERVED");
  }

  @Test
  void should_keep_stored_bio_verbatim() {
    UserProfile current = stored().bio(new Bio("a\nb\nc\nd")).build();

    assertThat(policy.parse(draft().bio("a\nb\nc\nd").build(), current).bio())
        .isEqualTo(new Bio("a\nb\nc\nd"));
  }

  @Test
  void should_still_validate_unchanged_url() {
    UserProfile current = stored().links(List.of(ProfileLink.of(null, "ftp://x.org"))).build();

    assertThat(violations(draft().links(List.of(LinkDraft.of(null, "ftp://x.org"))), current))
        .containsExactly("links[0].url/INVALID_FORMAT");
  }

  // ===== 隐私设置 =====

  @Test
  void should_parse_privacy() {
    assertThat(policy.parsePrivacy("SIGNED_IN", "SELF", true))
        .isEqualTo(
            PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true));
  }

  @Test
  void should_report_missing_privacy_fields_together() {
    assertThat(privacyViolations(null, null, null))
        .containsExactly(
            "profileVisibility/REQUIRED",
            "professionalVisibility/REQUIRED",
            "searchIndexing/REQUIRED");
  }

  @Test
  void should_reject_unknown_privacy_values() {
    assertThat(privacyViolations("PRIVATE", "SIGNED_IN", false))
        .containsExactly(
            "profileVisibility/INVALID_FORMAT", "professionalVisibility/INVALID_FORMAT");
  }

  // ===== 从网页复制来的不换行空格 =====

  @Test
  void should_strip_no_break_spaces_around_url_ror_id_and_mesh_ui() {
    String nbsp = cp(0x00A0);
    ParsedProfile parsed =
        parse(
            draft()
                .links(List.of(LinkDraft.of(null, nbsp + "https://a.org/x" + nbsp)))
                .organizationRorId(nbsp + "04jztag35" + nbsp)
                .researchInterests(List.of(nbsp + "D003920" + nbsp)));

    assertThat(parsed.links()).containsExactly(ProfileLink.of(null, "https://a.org/x"));
    assertThat(parsed.organizationRorId()).isEqualTo(RorId.of("04jztag35"));
    assertThat(parsed.researchInterests()).containsExactly(MeshUi.of("D003920"));
  }

  // ===== 辅助方法 =====

  /// 空草稿的构建器。
  ///
  /// @return 构建器
  private static ProfileDraft.ProfileDraftBuilder draft() {
    return ProfileDraft.builder();
  }

  /// 库里的资料的构建器，只带必填字段。
  ///
  /// @return 构建器
  private static UserProfile.UserProfileBuilder stored() {
    return UserProfile.restore().id(1L).userId(1L).version(0L);
  }

  /// 对空资料解析草稿，期望成功。
  ///
  /// @param draft 草稿构建器
  /// @return 解析结果
  private ParsedProfile parse(ProfileDraft.ProfileDraftBuilder draft) {
    return policy.parse(draft.build(), empty);
  }

  /// 对空资料解析草稿，期望失败，返回「字段/原因码」列表。
  ///
  /// @param draft 草稿构建器
  /// @return 字段错误
  private List<String> violations(ProfileDraft.ProfileDraftBuilder draft) {
    return violations(draft, empty);
  }

  /// 对给定资料解析草稿，期望失败，返回「字段/原因码」列表。
  ///
  /// @param draft 草稿构建器
  /// @param current 库里的资料
  /// @return 字段错误
  private List<String> violations(ProfileDraft.ProfileDraftBuilder draft, UserProfile current) {
    InvalidUserFieldsException ex =
        catchThrowableOfType(
            () -> policy.parse(draft.build(), current), InvalidUserFieldsException.class);
    assertThat(ex).isNotNull();
    return ex.getFieldViolations().stream().map(v -> v.field() + "/" + v.code()).toList();
  }

  /// 解析隐私设置，期望失败，返回「字段/原因码」列表。
  ///
  /// @param profile 主页可见性
  /// @param professional 专业背景可见性
  /// @param searchIndexing 是否允许收录
  /// @return 字段错误
  private List<String> privacyViolations(
      String profile, String professional, Boolean searchIndexing) {
    InvalidUserFieldsException ex =
        catchThrowableOfType(
            () -> policy.parsePrivacy(profile, professional, searchIndexing),
            InvalidUserFieldsException.class);
    assertThat(ex).isNotNull();
    return ex.getFieldViolations().stream().map(v -> v.field() + "/" + v.code()).toList();
  }

  /// 按码点拼字符串。
  ///
  /// @param codePoints 码点
  /// @return 字符串
  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }
}
