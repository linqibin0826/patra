package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PublicationAuthorSnapshot 值对象单元测试。
///
/// @author linqibin
/// @since 0.6.0
@DisplayName("PublicationAuthorSnapshot 值对象")
class PublicationAuthorSnapshotTest {

  @Test
  @DisplayName("姓 + 名应拼为 \"Last Fore\"")
  void deriveDisplayName_lastAndFore() {
    assertThat(PublicationAuthorSnapshot.deriveDisplayName("Smith", "John", "J", null))
        .isEqualTo("Smith John");
  }

  @Test
  @DisplayName("名缺失时应退化为姓 + 缩写")
  void deriveDisplayName_lastAndInitialsOnly() {
    assertThat(PublicationAuthorSnapshot.deriveDisplayName("Smith", null, "JR", null))
        .isEqualTo("Smith JR");
  }

  @Test
  @DisplayName("仅有姓时应单独成名")
  void deriveDisplayName_lastNameOnly() {
    assertThat(PublicationAuthorSnapshot.deriveDisplayName("Smith", null, null, null))
        .isEqualTo("Smith");
  }

  @Test
  @DisplayName("集体作者名优先于个人姓名")
  void deriveDisplayName_collectiveWins() {
    assertThat(
            PublicationAuthorSnapshot.deriveDisplayName("Smith", "John", null, "WHO Study Group"))
        .isEqualTo("WHO Study Group");
  }

  @Test
  @DisplayName("姓名全空白时应返回 null")
  void deriveDisplayName_allBlank_returnsNull() {
    assertThat(PublicationAuthorSnapshot.deriveDisplayName(null, "  ", null, "")).isNull();
  }

  @Test
  @DisplayName("超长展示名应按 200 截断")
  void deriveDisplayName_over200_truncated() {
    String longName = "A".repeat(300);
    assertThat(PublicationAuthorSnapshot.deriveDisplayName(longName, null, null, null))
        .hasSize(200);
  }

  @Test
  @DisplayName("拼接后超长应截断且不留尾随空格")
  void deriveDisplayName_joinedOver200_truncatedAndStripped() {
    // 199 个 A + 空格 + 50 个 B = 250 字符，按 200 截断后第 200 位恰是空格，strip 后为 199 字符
    String displayName =
        PublicationAuthorSnapshot.deriveDisplayName("A".repeat(199), "B".repeat(50), null, null);

    assertThat(displayName).isEqualTo("A".repeat(199)).hasSize(199);
  }

  @Test
  @DisplayName("isCollective 应按集体作者名判定，空白名归一化为 null")
  void isCollective_shouldReflectNormalizedCollectiveName() {
    assertThat(collectiveNameOf("WHO Study Group").isCollective()).isTrue();
    assertThat(collectiveNameOf(null).isCollective()).isFalse();
    assertThat(collectiveNameOf("  ").isCollective()).isFalse();
    assertThat(collectiveNameOf("  ").collectiveName()).isNull();
  }

  @Test
  @DisplayName("机构列表为 null 时应归一化为空列表")
  void snapshot_nullAffiliations_shouldBeEmptyList() {
    var snap = PublicationAuthorSnapshot.builder().order(1).displayName("Smith").build();

    assertThat(snap.affiliations()).isEmpty();
  }

  @Test
  @DisplayName("展示名超过 200 字符时应抛异常")
  void snapshot_displayNameOver200_shouldThrow() {
    assertThatThrownBy(
            () -> PublicationAuthorSnapshot.builder().order(1).displayName("A".repeat(201)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("恰好 200 字符的展示名可被构造器接受（derive 与 ctor 边界一致）")
  void snapshot_displayNameExactly200_shouldBeAccepted() {
    assertThatCode(
            () -> PublicationAuthorSnapshot.builder().order(1).displayName("A".repeat(200)).build())
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("机构列表应防御性拷贝，非法入参应抛异常")
  void snapshot_defensiveCopyAndValidation() {
    var affs = new ArrayList<PublicationAuthorAffiliationSnapshot>();
    affs.add(PublicationAuthorAffiliationSnapshot.of(1, "Harvard"));
    var snap =
        PublicationAuthorSnapshot.builder()
            .order(1)
            .lastName("Smith")
            .displayName("Smith")
            .firstAuthor(true)
            .affiliations(affs)
            .build();
    affs.clear();

    assertThat(snap.affiliations()).hasSize(1);
    assertThatThrownBy(() -> PublicationAuthorSnapshot.builder().order(1).displayName(" ").build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PublicationAuthorAffiliationSnapshot.of(0, "X"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("超长机构原文应按 2000 截断而非抛异常")
  void affiliationSnapshot_overlongString_shouldTruncate() {
    String overlong = "A".repeat(2500);

    var snap = PublicationAuthorAffiliationSnapshot.of(1, overlong);

    assertThat(snap.affiliationString()).hasSize(2000);
  }

  @Test
  @DisplayName("超长前导空白的机构原文应先去空白再截断，不得退化为空串")
  void affiliationSnapshot_leadingWhitespaceBeyondLimit_shouldKeepContent() {
    String padded = " ".repeat(2100) + "Harvard";

    var snap = PublicationAuthorAffiliationSnapshot.of(1, padded);

    assertThat(snap.affiliationString()).isEqualTo("Harvard");
  }

  @Test
  @DisplayName("集体作者名超过 500 字符时应截断，展示名仍受 200 约束")
  void snapshot_collectiveNameOver500_shouldTruncate() {
    String longCollectiveName = "C".repeat(600);

    var snap =
        PublicationAuthorSnapshot.builder()
            .order(1)
            .collectiveName(longCollectiveName)
            .displayName(
                PublicationAuthorSnapshot.deriveDisplayName(null, null, null, longCollectiveName))
            .build();

    assertThat(snap.collectiveName()).hasSize(500);
    assertThat(snap.displayName()).hasSize(200);
  }

  /// 构造仅指定集体作者名的快照。
  ///
  /// @param collectiveName 集体作者名
  /// @return 作者快照
  private static PublicationAuthorSnapshot collectiveNameOf(String collectiveName) {
    return PublicationAuthorSnapshot.builder()
        .order(1)
        .displayName("Smith")
        .collectiveName(collectiveName)
        .build();
  }
}
