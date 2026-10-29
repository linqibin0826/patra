package dev.patra.identity.domain.account.model.vo.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvatarRenditionsTest {

  @Test
  void should_copy_arrays_in_and_out() {
    byte[] large = {1, 2, 3};
    byte[] small = {4, 5};
    AvatarRenditions renditions = AvatarRenditions.of(large, small);

    large[0] = 9;
    renditions.small()[0] = 9;

    assertThat(renditions.large()).containsExactly(1, 2, 3);
    assertThat(renditions.small()).containsExactly(4, 5);
  }

  @Test
  void should_compare_by_content() {
    assertThat(AvatarRenditions.of(new byte[] {1}, new byte[] {2}))
        .isEqualTo(AvatarRenditions.of(new byte[] {1}, new byte[] {2}))
        .hasSameHashCodeAs(AvatarRenditions.of(new byte[] {1}, new byte[] {2}));
  }
}
