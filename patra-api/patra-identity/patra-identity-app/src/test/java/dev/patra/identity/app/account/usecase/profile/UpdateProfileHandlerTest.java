package dev.patra.identity.app.account.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.account.policy.DisplayNamePolicy;
import dev.patra.identity.domain.account.policy.ProfilePolicy;
import dev.patra.identity.domain.account.port.catalog.CatalogReferencePort;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;

/// UpdateProfileHandler 单元测试：名称快照来自 catalog、已存的不重新校验、catalog 失败的语义、状态守卫与并发。
@ExtendWith(OutputCaptureExtension.class)
class UpdateProfileHandlerTest {

  private static final long USER_ID = 42L;
  private static final CurrentUser CURRENT =
      CurrentUser.of(USER_ID, 7001L, AccountType.USER, ClientType.WEB);
  private static final RorId PUMCH = RorId.of("04jztag35");
  private static final MeshUi HEART_FAILURE = MeshUi.of("D006333");
  private static final MeshUi AF = MeshUi.of("D001281");
  private static final MeshUi DIABETES = MeshUi.of("D003920");

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final CatalogReferencePort catalog = mock(CatalogReferencePort.class);
  private final UpdateProfileHandler handler =
      new UpdateProfileHandler(
          currentUserPort,
          users,
          profiles,
          new ProfilePolicy(new DisplayNamePolicy()),
          catalog,
          TransactionOperations.withoutTransaction());

  /// 默认：当前用户正常，资料为空；每次读都给新实例（处理器会改聚合），保存原样返回。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(USER_ID)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    stubProfile(() -> UserProfile.restore().id(1L).userId(USER_ID).version(0L).build());
    when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void should_take_organization_name_from_catalog_and_ignore_client_name() {
    when(catalog.organizationName(PUMCH)).thenReturn(Optional.of("北京协和医院"));

    UserProfile saved =
        handler.handle(
            UpdateProfileCommand.of(
                ProfileDraft.builder()
                    .organizationRorId("04jztag35")
                    .organizationName("随便写")
                    .build()));

    assertThat(saved.getAffiliation()).contains(Affiliation.Registered.of(PUMCH, "北京协和医院"));
    assertThat(savedProfile().getAffiliation())
        .contains(Affiliation.Registered.of(PUMCH, "北京协和医院"));
  }

  @Test
  void should_not_call_catalog_when_affiliation_and_interests_are_unchanged() {
    stubProfile(this::profileWithPumchAndTwoInterests);

    handler.handle(
        UpdateProfileCommand.of(
            ProfileDraft.builder()
                .bio("只改简介")
                .organizationRorId("04jztag35")
                .researchInterests(List.of("D006333", "D001281"))
                .build()));

    verifyNoInteractions(catalog);
    UserProfile saved = savedProfile();
    assertThat(saved.getAffiliation()).contains(Affiliation.Registered.of(PUMCH, "北京协和医院"));
    assertThat(saved.getResearchInterests())
        .containsExactly(
            ResearchInterest.of(HEART_FAILURE, "Heart Failure"),
            ResearchInterest.of(AF, "Atrial Fibrillation"));
  }

  @Test
  void should_only_resolve_new_interests() {
    stubProfile(this::profileWithPumchAndTwoInterests);
    when(catalog.meshName(DIABETES)).thenReturn(Optional.of("Diabetes Mellitus"));

    handler.handle(
        UpdateProfileCommand.of(
            ProfileDraft.builder().researchInterests(List.of("D001281", "D003920")).build()));

    verify(catalog).meshName(DIABETES);
    verify(catalog, never()).meshName(AF);
    assertThat(savedProfile().getResearchInterests())
        .containsExactly(
            ResearchInterest.of(AF, "Atrial Fibrillation"),
            ResearchInterest.of(DIABETES, "Diabetes Mellitus"));
  }

  @Test
  void should_save_free_text_affiliation_as_given() {
    handler.handle(
        UpdateProfileCommand.of(ProfileDraft.builder().organizationName(" 某县  人民医院 ").build()));

    verifyNoInteractions(catalog);
    assertThat(savedProfile().getAffiliation()).contains(Affiliation.FreeText.of("某县 人民医院"));
  }

  @Test
  void should_report_all_catalog_misses_at_once() {
    when(catalog.organizationName(PUMCH)).thenReturn(Optional.empty());
    when(catalog.meshName(any())).thenReturn(Optional.empty());
    when(catalog.meshName(AF)).thenReturn(Optional.of("Atrial Fibrillation"));

    InvalidUserFieldsException ex =
        catchThrowableOfType(
            () ->
                handler.handle(
                    UpdateProfileCommand.of(
                        ProfileDraft.builder()
                            .organizationRorId("04jztag35")
                            .researchInterests(List.of("D006333", "D001281", "D003920"))
                            .build())),
            InvalidUserFieldsException.class);

    assertThat(ex.getFieldViolations())
        .extracting(v -> v.field() + "/" + v.code())
        .containsExactly(
            "organization.rorId/NOT_FOUND",
            "researchInterests[0]/NOT_FOUND",
            "researchInterests[2]/NOT_FOUND");
    verify(users, never()).findByIdForUpdate(USER_ID);
    verify(profiles, never()).save(any());
  }

  @Test
  void should_fail_with_503_and_stop_calling_on_first_unavailable() {
    when(catalog.meshName(HEART_FAILURE))
        .thenThrow(new CatalogUnavailableException(new IllegalStateException("down")));

    assertThatThrownBy(
            () ->
                handler.handle(
                    UpdateProfileCommand.of(
                        ProfileDraft.builder()
                            .researchInterests(List.of("D006333", "D001281"))
                            .build())))
        .isInstanceOf(CatalogUnavailableException.class);

    verify(catalog, never()).meshName(AF);
    verify(users, never()).findByIdForUpdate(USER_ID);
    verify(profiles, never()).save(any());
  }

  @Test
  void should_reject_pending_deletion_before_calling_catalog() {
    when(users.findById(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    UpdateProfileCommand.of(
                        ProfileDraft.builder().organizationRorId("04jztag35").build())))
        .isInstanceOf(UserPendingDeletionException.class);

    verifyNoInteractions(catalog);
  }

  @Test
  void should_recheck_state_under_lock() {
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(
            () -> handler.handle(UpdateProfileCommand.of(ProfileDraft.builder().bio("x").build())))
        .isInstanceOf(UserPendingDeletionException.class);

    verify(profiles, never()).save(any());
  }

  @Test
  void should_fail_when_kept_snapshot_vanished_concurrently() {
    UserProfile before = profileWithPumchAndTwoInterests();
    UserProfile after = UserProfile.restore().id(1L).userId(USER_ID).version(1L).build();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(before), Optional.of(after));

    assertThatThrownBy(
            () ->
                handler.handle(
                    UpdateProfileCommand.of(
                        ProfileDraft.builder().organizationRorId("04jztag35").build())))
        .isInstanceOf(UserModifiedConcurrentlyException.class);

    verify(profiles, never()).save(any());
  }

  @Test
  void should_report_field_errors_before_calling_catalog() {
    assertThatThrownBy(
            () ->
                handler.handle(
                    UpdateProfileCommand.of(
                        ProfileDraft.builder()
                            .bio("张".repeat(201))
                            .organizationRorId("04jztag35")
                            .build())))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(catalog);
  }

  @Test
  void should_log_previous_and_new_display_name(CapturedOutput output) {
    stubProfile(
        () ->
            UserProfile.restore()
                .id(1L)
                .userId(USER_ID)
                .displayName(new DisplayName("陈宇"))
                .version(0L)
                .build());

    handler.handle(UpdateProfileCommand.of(ProfileDraft.builder().displayName("陈宇二").build()));

    assertThat(output).contains("昵称已修改: userId=42, from=陈宇, to=陈宇二");
  }

  /// 每次读资料都返回一个新实例。
  ///
  /// @param profile 资料的工厂
  private void stubProfile(Supplier<UserProfile> profile) {
    when(profiles.findByUserId(USER_ID)).thenAnswer(invocation -> Optional.of(profile.get()));
  }

  /// 已有协和与两个关注领域的资料。
  ///
  /// @return 资料
  private UserProfile profileWithPumchAndTwoInterests() {
    return UserProfile.restore()
        .id(1L)
        .userId(USER_ID)
        .affiliation(Affiliation.Registered.of(PUMCH, "北京协和医院"))
        .researchInterests(
            List.of(
                ResearchInterest.of(HEART_FAILURE, "Heart Failure"),
                ResearchInterest.of(AF, "Atrial Fibrillation")))
        .version(0L)
        .build();
  }

  /// 抓保存的资料。
  ///
  /// @return 保存的资料
  private UserProfile savedProfile() {
    ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(captor.capture());
    return captor.getValue();
  }

  /// 给定状态的用户。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    return User.restore()
        .id(USER_ID)
        .email(EmailAddress.of("chen.yu@example.com"))
        .username(Username.of("chen-yu"))
        .status(status)
        .deletionDueAt(
            status == UserStatus.PENDING_DELETION ? Instant.parse("2026-11-01T00:00:00Z") : null)
        .version(0L)
        .build();
  }
}
