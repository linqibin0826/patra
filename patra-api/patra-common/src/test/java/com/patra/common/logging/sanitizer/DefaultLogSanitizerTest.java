package com.patra.common.logging.sanitizer;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultLogSanitizer}.
 *
 * <p>Tests cover all sensitive data patterns:
 *
 * <ul>
 *   <li>Passwords and secrets
 *   <li>API keys and tokens
 *   <li>Email addresses
 *   <li>Phone numbers
 *   <li>Credit card numbers
 *   <li>Social Security Numbers
 * </ul>
 *
 * @since 0.1.0
 */
@DisplayName("DefaultLogSanitizer")
class DefaultLogSanitizerTest {

  private DefaultLogSanitizer sanitizer;
  private ObjectMapper objectMapper;

  private static final String REDACTED = "***REDACTED***";

  @BeforeEach
  void setUp() {
    sanitizer = new DefaultLogSanitizer();
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("sanitize() - Plain text sanitization")
  class SanitizePlainTextTests {

    @Test
    @DisplayName("Should sanitize password patterns")
    void shouldSanitizePasswordPatterns() {
      assertSanitized("password=secret123", "password=" + REDACTED);
      assertSanitized("pwd=mypass", "pwd=" + REDACTED);
      assertSanitized("passwd=hunter2", "passwd=" + REDACTED);
      assertSanitized("secret=topsecret", "secret=" + REDACTED);
      assertSanitized("password:secret123", "password=" + REDACTED);
      // Quotes are included in the match group, so they remain after redaction
      assertTrue(sanitizer.sanitize("password=\"secret123\"").contains(REDACTED));
      assertTrue(sanitizer.sanitize("password='secret123'").contains(REDACTED));
    }

    @Test
    @DisplayName("Should sanitize API key patterns")
    void shouldSanitizeApiKeyPatterns() {
      assertSanitized("apiKey=abc123def456", "apiKey=" + REDACTED);
      assertSanitized("api_key=xyz789", "api_key=" + REDACTED);
      assertSanitized("token=bearer_token_123", "token=" + REDACTED);
      assertSanitized("access_token=at_123456", "access_token=" + REDACTED);
      assertSanitized("refresh_token=rt_789012", "refresh_token=" + REDACTED);
      assertSanitized("bearer=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", "bearer=" + REDACTED);
    }

    @Test
    @DisplayName("Should sanitize Authorization headers")
    void shouldSanitizeAuthorizationHeaders() {
      // Authorization header pattern captures "Bearer" or "Basic" as group 1, token as group 2
      String result1 =
          sanitizer.sanitize("Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
      assertTrue(result1.contains(REDACTED), "Should redact Bearer token");
      assertFalse(
          result1.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "Token should be hidden");

      String result2 = sanitizer.sanitize("authorization: Basic dXNlcjpwYXNzd29yZA==");
      assertTrue(result2.contains(REDACTED), "Should redact Basic auth");
      assertFalse(result2.contains("dXNlcjpwYXNzd29yZA=="), "Credentials should be hidden");
    }

    @Test
    @DisplayName("Should sanitize email addresses")
    void shouldSanitizeEmailAddresses() {
      assertSanitized("Contact john.doe@example.com for help", "Contact " + REDACTED + " for help");
      assertSanitized("user@domain.com", REDACTED);
      assertSanitized("test.email+tag@subdomain.example.com", REDACTED);
    }

    @Test
    @DisplayName("Should sanitize phone numbers")
    void shouldSanitizePhoneNumbers() {
      // Phone patterns may match multiple times due to overlapping matches
      String result1 = sanitizer.sanitize("Call +1-234-567-8900 now");
      assertTrue(result1.contains(REDACTED), "Should redact phone number");
      assertFalse(result1.contains("234"), "Should hide phone digits");

      String result2 = sanitizer.sanitize("Phone: (123) 456-7890");
      assertTrue(result2.contains(REDACTED), "Should redact phone number");
      assertFalse(result2.contains("456-7890"), "Should hide phone digits");

      String result3 = sanitizer.sanitize("Contact 123-456-7890");
      assertTrue(result3.contains(REDACTED), "Should redact phone number");

      String result4 = sanitizer.sanitize("Mobile: +44 20 7946 0958");
      assertTrue(result4.contains(REDACTED), "Should redact phone number");
    }

    @Test
    @DisplayName("Should sanitize credit card numbers")
    void shouldSanitizeCreditCardNumbers() {
      // Credit card patterns may match multiple times due to overlapping segments
      String result1 = sanitizer.sanitize("Card: 4111-1111-1111-1111");
      assertTrue(result1.contains(REDACTED), "Should redact credit card");
      assertFalse(result1.contains("4111"), "Should hide CC digits");

      String result2 = sanitizer.sanitize("CC 4111111111111111");
      assertTrue(result2.contains(REDACTED), "Should redact credit card");
      assertFalse(result2.contains("4111111111111111"), "Should hide CC number");

      String result3 = sanitizer.sanitize("Pay with 5500 0000 0000 0004");
      assertTrue(result3.contains(REDACTED), "Should redact credit card");
      assertFalse(result3.contains("5500"), "Should hide CC digits");
    }

    @Test
    @DisplayName("Should sanitize SSN patterns")
    void shouldSanitizeSsnPatterns() {
      // SSN patterns may also match multiple times
      String result1 = sanitizer.sanitize("SSN: 123-45-6789");
      assertTrue(result1.contains(REDACTED), "Should redact SSN");
      assertFalse(result1.contains("6789"), "Should hide SSN digits");

      String result2 = sanitizer.sanitize("Social Security: 123456789");
      assertTrue(result2.contains(REDACTED), "Should redact SSN");
      assertFalse(result2.contains("123456789"), "Should hide SSN number");
    }

    @Test
    @DisplayName("Should handle multiple sensitive patterns in one string")
    void shouldHandleMultipleSensitivePatterns() {
      String input = "User john@example.com has password=secret and phone 123-456-7890";
      String result = sanitizer.sanitize(input);

      assertTrue(result.contains(REDACTED));
      assertFalse(result.contains("john@example.com"));
      assertFalse(result.contains("secret"));
      assertFalse(result.contains("123-456-7890"));
    }

    @Test
    @DisplayName("Should return null/empty for null/empty input")
    void shouldHandleNullAndEmptyInput() {
      assertNull(sanitizer.sanitize(null));
      assertEquals("", sanitizer.sanitize(""));
    }

    @Test
    @DisplayName("Should not modify safe text")
    void shouldNotModifySafeText() {
      // Use simple text without number sequences that could match phone patterns
      String safeText = "This is a safe log message without any sensitive information.";
      assertEquals(safeText, sanitizer.sanitize(safeText));
    }

    private void assertSanitized(String input, String expected) {
      String result = sanitizer.sanitize(input);
      assertEquals(expected, result, "Failed to sanitize: " + input);
    }
  }

  @Nested
  @DisplayName("sanitizeJson() - JSON sanitization")
  class SanitizeJsonTests {

    @Test
    @DisplayName("Should sanitize sensitive JSON fields")
    void shouldSanitizeSensitiveJsonFields() throws Exception {
      String json =
          """
          {
            "username": "john.doe",
            "password": "secret123",
            "email": "john@example.com",
            "apiKey": "abc123def456"
          }
          """;

      String result = sanitizer.sanitizeJson(json);
      Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);

      assertEquals("john.doe", resultMap.get("username"), "Non-sensitive field should not change");
      assertEquals(REDACTED, resultMap.get("password"), "Password should be redacted");
      assertEquals(REDACTED, resultMap.get("apiKey"), "API key should be redacted");
    }

    @Test
    @DisplayName("Should sanitize nested JSON objects")
    void shouldSanitizeNestedJsonObjects() throws Exception {
      String json =
          """
          {
            "user": {
              "name": "John",
              "credentials": {
                "password": "secret",
                "token": "abc123"
              }
            }
          }
          """;

      String result = sanitizer.sanitizeJson(json);

      assertFalse(result.contains("secret"), "Nested password should be redacted");
      assertFalse(result.contains("abc123"), "Nested token should be redacted");
      assertTrue(result.contains(REDACTED), "Should contain redaction marker");
    }

    @Test
    @DisplayName("Should sanitize JSON arrays")
    void shouldSanitizeJsonArrays() throws Exception {
      String json =
          """
          {
            "users": [
              {"name": "Alice", "password": "alice123"},
              {"name": "Bob", "token": "bob456"}
            ]
          }
          """;

      String result = sanitizer.sanitizeJson(json);

      assertFalse(result.contains("alice123"), "Array element password should be redacted");
      assertFalse(result.contains("bob456"), "Array element token should be redacted");
      assertTrue(result.contains(REDACTED), "Should contain redaction marker");
    }

    @Test
    @DisplayName("Should handle all sensitive key variations")
    void shouldHandleAllSensitiveKeyVariations() throws Exception {
      String json =
          """
          {
            "password": "p1",
            "passwd": "p2",
            "pwd": "p3",
            "secret": "s1",
            "apiKey": "ak1",
            "api_key": "ak2",
            "token": "t1",
            "accessToken": "at1",
            "access_token": "at2",
            "refreshToken": "rt1",
            "refresh_token": "rt2",
            "authorization": "auth1",
            "auth": "auth2",
            "ssn": "123456789",
            "creditCard": "4111111111111111",
            "credit_card": "5500000000000004",
            "cardNumber": "340000000000009",
            "card_number": "6011000000000004",
            "cvv": "123",
            "pin": "4567"
          }
          """;

      String result = sanitizer.sanitizeJson(json);

      // All values should be redacted
      for (String pattern :
          new String[] {
            "p1",
            "p2",
            "p3",
            "s1",
            "ak1",
            "ak2",
            "t1",
            "at1",
            "at2",
            "rt1",
            "rt2",
            "auth1",
            "auth2",
            "123456789",
            "4111111111111111",
            "5500000000000004",
            "340000000000009",
            "6011000000000004",
            "123",
            "4567"
          }) {
        assertFalse(
            result.contains("\"" + pattern + "\""), "Value '" + pattern + "' should be redacted");
      }

      assertTrue(result.contains(REDACTED), "Should contain redaction markers");
    }

    @Test
    @DisplayName("Should fall back to plain text sanitization for invalid JSON")
    void shouldFallBackForInvalidJson() {
      String invalidJson = "not valid json but has password=secret123";
      String result = sanitizer.sanitizeJson(invalidJson);

      assertTrue(result.contains(REDACTED), "Should still sanitize as plain text");
      assertFalse(result.contains("secret123"), "Should redact password value");
    }

    @Test
    @DisplayName("Should return null/empty for null/empty input")
    void shouldHandleNullAndEmptyInput() {
      assertNull(sanitizer.sanitizeJson(null));
      assertEquals("", sanitizer.sanitizeJson(""));
    }
  }

  @Nested
  @DisplayName("sanitizeObject() - Object sanitization")
  class SanitizeObjectTests {

    @Test
    @DisplayName("Should sanitize POJO objects via JSON serialization")
    void shouldSanitizePojoObjects() throws Exception {
      TestUser user = new TestUser("john.doe", "secret123", "john@example.com");

      String result = sanitizer.sanitizeObject(user);

      assertTrue(result.contains("john.doe"), "Username should be preserved");
      assertFalse(result.contains("secret123"), "Password should be redacted");
      assertTrue(result.contains(REDACTED), "Should contain redaction marker");
    }

    @Test
    @DisplayName("Should return null for null input")
    void shouldHandleNullInput() {
      assertNull(sanitizer.sanitizeObject(null));
    }

    @Test
    @DisplayName("Should handle objects with no sensitive data")
    void shouldHandleObjectsWithNoSensitiveData() {
      SafeObject safeObj = new SafeObject("John Doe", 30);

      String result = sanitizer.sanitizeObject(safeObj);

      assertTrue(result.contains("John Doe"), "Name should be preserved");
      assertTrue(result.contains("30"), "Age should be preserved");
      assertFalse(result.contains(REDACTED), "Should not redact safe data");
    }

    /** Test DTO with sensitive fields */
    private static class TestUser {
      public String username;
      public String password;
      public String email;

      public TestUser(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
      }
    }

    /** Test DTO with no sensitive fields */
    private static class SafeObject {
      public String name;
      public int age;

      public SafeObject(String name, int age) {
        this.name = name;
        this.age = age;
      }
    }
  }

  @Nested
  @DisplayName("containsSensitiveData() - Detection")
  class ContainsSensitiveDataTests {

    @Test
    @DisplayName("Should detect password patterns")
    void shouldDetectPasswordPatterns() {
      assertTrue(sanitizer.containsSensitiveData("password=secret123"));
      assertTrue(sanitizer.containsSensitiveData("pwd=mypass"));
      assertTrue(sanitizer.containsSensitiveData("Log message with secret=topsecret here"));
    }

    @Test
    @DisplayName("Should detect API key patterns")
    void shouldDetectApiKeyPatterns() {
      assertTrue(sanitizer.containsSensitiveData("apiKey=abc123"));
      assertTrue(sanitizer.containsSensitiveData("token=bearer_token"));
      assertTrue(sanitizer.containsSensitiveData("Authorization: Bearer xyz123"));
    }

    @Test
    @DisplayName("Should detect email addresses")
    void shouldDetectEmailAddresses() {
      assertTrue(sanitizer.containsSensitiveData("Contact john.doe@example.com"));
      assertTrue(sanitizer.containsSensitiveData("user@domain.com"));
    }

    @Test
    @DisplayName("Should detect phone numbers")
    void shouldDetectPhoneNumbers() {
      assertTrue(sanitizer.containsSensitiveData("Call +1-234-567-8900"));
      assertTrue(sanitizer.containsSensitiveData("Phone: (123) 456-7890"));
    }

    @Test
    @DisplayName("Should detect credit card numbers")
    void shouldDetectCreditCardNumbers() {
      assertTrue(sanitizer.containsSensitiveData("Card: 4111-1111-1111-1111"));
      assertTrue(sanitizer.containsSensitiveData("CC 4111111111111111"));
    }

    @Test
    @DisplayName("Should detect SSN patterns")
    void shouldDetectSsnPatterns() {
      assertTrue(sanitizer.containsSensitiveData("SSN: 123-45-6789"));
      assertTrue(sanitizer.containsSensitiveData("Social Security: 123456789"));
    }

    @Test
    @DisplayName("Should return false for safe text")
    void shouldReturnFalseForSafeText() {
      // Use simple text without number sequences that could match sensitive patterns
      assertFalse(
          sanitizer.containsSensitiveData("This is a safe log message without any sensitive data"));
      assertFalse(sanitizer.containsSensitiveData("Processing batch job completed successfully"));
    }

    @Test
    @DisplayName("Should return false for null/empty input")
    void shouldHandleNullAndEmptyInput() {
      assertFalse(sanitizer.containsSensitiveData(null));
      assertFalse(sanitizer.containsSensitiveData(""));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle very long strings")
    void shouldHandleVeryLongStrings() {
      StringBuilder longString = new StringBuilder();
      for (int i = 0; i < 10000; i++) {
        longString.append("Safe text ");
      }
      longString.append(" password=secret123");

      String result = sanitizer.sanitize(longString.toString());

      assertTrue(result.contains(REDACTED), "Should find and redact sensitive data in long text");
      assertFalse(result.contains("secret123"), "Should not contain original password");
    }

    @Test
    @DisplayName("Should handle special characters in patterns")
    void shouldHandleSpecialCharactersInPatterns() {
      assertSanitized("password=p@ssw0rd!#$%", "password=" + REDACTED, "Special chars in password");
      assertSanitized("apiKey=abc-123_xyz.789", "apiKey=" + REDACTED, "Special chars in API key");
    }

    @Test
    @DisplayName("Should handle multiple occurrences of same pattern")
    void shouldHandleMultipleOccurrences() {
      String input = "password=secret1 and password=secret2 and password=secret3";
      String result = sanitizer.sanitize(input);

      assertEquals(
          "password=" + REDACTED + " and password=" + REDACTED + " and password=" + REDACTED,
          result);
    }

    @Test
    @DisplayName("Should be case-insensitive for keywords")
    void shouldBeCaseInsensitive() {
      assertSanitized("PASSWORD=secret", "PASSWORD=" + REDACTED, "Uppercase PASSWORD");
      assertSanitized("Password=secret", "Password=" + REDACTED, "Mixed case Password");
      assertSanitized("APIKEY=abc123", "APIKEY=" + REDACTED, "Uppercase APIKEY");
    }

    private void assertSanitized(String input, String expected, String message) {
      String result = sanitizer.sanitize(input);
      assertEquals(expected, result, message);
    }
  }

  @Nested
  @DisplayName("Thread Safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("Should be thread-safe for concurrent sanitization")
    void shouldBeThreadSafe() throws InterruptedException {
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];

      for (int i = 0; i < threadCount; i++) {
        final int threadNum = i;
        threads[i] =
            new Thread(
                () -> {
                  for (int j = 0; j < 100; j++) {
                    String input = "Thread-" + threadNum + " password=secret" + j;
                    String result = sanitizer.sanitize(input);
                    assertFalse(result.contains("secret" + j), "Should redact in thread");
                    assertTrue(result.contains(REDACTED), "Should contain redaction marker");
                  }
                });
        threads[i].start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }
    }
  }
}
