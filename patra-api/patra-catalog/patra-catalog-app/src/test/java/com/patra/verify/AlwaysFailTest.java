package com.patra.verify;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AlwaysFailTest {
  @Test
  void thisAlwaysFails() {
    fail("Intentional fail for PAP-15 D5 verification — will be reverted");
  }
}
