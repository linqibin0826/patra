package dev.linqibin.patra.catalog.domain.port.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VenueSnapshotTest {

  @Test
  void of_validArguments_createsSnapshot() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, "1234-5678", "catalog/venue-cover/42.jpg");
    assertThat(snapshot.id()).isEqualTo(42L);
    assertThat(snapshot.issnL()).isEqualTo("1234-5678");
    assertThat(snapshot.existingCoverKey()).isEqualTo("catalog/venue-cover/42.jpg");
  }

  @Test
  void of_nullIssnL_allowedForDebugging() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, null, null);
    assertThat(snapshot.issnL()).isNull();
    assertThat(snapshot.existingCoverKey()).isNull();
  }

  @Test
  void of_nullCoverKey_allowed() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, "1234-5678", null);
    assertThat(snapshot.existingCoverKey()).isNull();
  }

  @Test
  void of_nullId_rejected() {
    assertThatThrownBy(() -> VenueSnapshot.of(null, "1234-5678", null))
        .isInstanceOf(NullPointerException.class);
  }
}
