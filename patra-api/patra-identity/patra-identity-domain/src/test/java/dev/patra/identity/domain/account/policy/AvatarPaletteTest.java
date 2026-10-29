package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvatarPaletteTest {

  /// 固定向量：算法上线后不能改，改了所有人的默认头像都会变色。
  @Test
  void should_keep_fixed_vectors() {
    assertThat(AvatarPalette.of(0L)).isEqualTo(7);
    assertThat(AvatarPalette.of(1L)).isEqualTo(5);
    assertThat(AvatarPalette.of(352303128713027974L)).isEqualTo(7);
    assertThat(AvatarPalette.of(Long.MAX_VALUE)).isEqualTo(3);
  }

  /// 雪花式 ID 只在序列位以上递增，取模前不混合会偏。
  @Test
  void should_spread_evenly_over_snowflake_ids() {
    int[] counts = new int[12];
    for (int i = 0; i < 120_000; i++) {
      int palette = AvatarPalette.of(352303128713027974L + 4096L * i);
      assertThat(palette).isBetween(0, 11);
      counts[palette]++;
    }

    for (int count : counts) {
      assertThat(count).isBetween(9_500, 10_500);
    }
  }
}
