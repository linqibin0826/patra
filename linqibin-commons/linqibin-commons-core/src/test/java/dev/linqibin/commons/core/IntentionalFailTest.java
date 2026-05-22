package dev.linqibin.commons.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/// Intentional fail test to verify required-check gating works.
/// Will be removed after verification.
class IntentionalFailTest {

  @Test
  void mustFailToVerifyRequiredCheckGating() {
    assertEquals(1, 2, "intentional fail to verify required-check works");
  }
}
