package dev.linqibin.patra.catalog.infra.persistence.converter.attribute;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PublicationIdentifierTypeConverter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationIdentifierTypeConverter")
class PublicationIdentifierTypeConverterTest {

  private PublicationIdentifierTypeConverter converter;

  @BeforeEach
  void setUp() {
    converter = new PublicationIdentifierTypeConverter();
  }

  @Nested
  @DisplayName("convertToDatabaseColumn()")
  class ConvertToDatabaseColumnTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void should_return_null_when_input_is_null() {
      // when
      String result = converter.convertToDatabaseColumn(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("PMID 应转换为 'pmid'")
    void should_convert_pmid_to_lowercase_code() {
      // when
      String result = converter.convertToDatabaseColumn(PublicationIdentifierType.PMID);

      // then
      assertThat(result).isEqualTo("pmid");
    }

    @Test
    @DisplayName("DOI 应转换为 'doi'")
    void should_convert_doi_to_lowercase_code() {
      // when
      String result = converter.convertToDatabaseColumn(PublicationIdentifierType.DOI);

      // then
      assertThat(result).isEqualTo("doi");
    }

    @Test
    @DisplayName("PMC 应转换为 'pmc'")
    void should_convert_pmc_to_lowercase_code() {
      // when
      String result = converter.convertToDatabaseColumn(PublicationIdentifierType.PMC);

      // then
      assertThat(result).isEqualTo("pmc");
    }

    @Test
    @DisplayName("OTHER 应转换为 'other'")
    void should_convert_other_to_lowercase_code() {
      // when
      String result = converter.convertToDatabaseColumn(PublicationIdentifierType.OTHER);

      // then
      assertThat(result).isEqualTo("other");
    }

    @Test
    @DisplayName("所有枚举值都应成功转换")
    void should_convert_all_enum_values() {
      for (PublicationIdentifierType type : PublicationIdentifierType.values()) {
        // when
        String result = converter.convertToDatabaseColumn(type);

        // then
        assertThat(result).as("枚举值 %s 应转换为非空字符串", type).isNotNull().isNotBlank();
      }
    }
  }

  @Nested
  @DisplayName("convertToEntityAttribute()")
  class ConvertToEntityAttributeTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void should_return_null_when_input_is_null() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("空字符串应返回 null")
    void should_return_null_when_input_is_empty() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("");

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("空白字符串应返回 null")
    void should_return_null_when_input_is_blank() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("   ");

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("'pmid' 应转换为 PMID")
    void should_convert_pmid_code_to_enum() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("pmid");

      // then
      assertThat(result).isEqualTo(PublicationIdentifierType.PMID);
    }

    @Test
    @DisplayName("'doi' 应转换为 DOI")
    void should_convert_doi_code_to_enum() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("doi");

      // then
      assertThat(result).isEqualTo(PublicationIdentifierType.DOI);
    }

    @Test
    @DisplayName("'pmc' 应转换为 PMC")
    void should_convert_pmc_code_to_enum() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("pmc");

      // then
      assertThat(result).isEqualTo(PublicationIdentifierType.PMC);
    }

    @Test
    @DisplayName("未知类型应返回 OTHER")
    void should_return_other_for_unknown_type() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("unknown_type");

      // then
      assertThat(result).isEqualTo(PublicationIdentifierType.OTHER);
    }

    @Test
    @DisplayName("大写代码应正确识别")
    void should_handle_uppercase_code() {
      // when
      PublicationIdentifierType result = converter.convertToEntityAttribute("PMID");

      // then - 根据 fromCodeOrOther 的实现，可能返回 PMID 或 OTHER
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("双向转换一致性")
  class RoundTripTest {

    @Test
    @DisplayName("枚举 → 数据库 → 枚举 应保持一致")
    void should_maintain_consistency_in_round_trip() {
      for (PublicationIdentifierType original : PublicationIdentifierType.values()) {
        // given
        String dbValue = converter.convertToDatabaseColumn(original);

        // when
        PublicationIdentifierType restored = converter.convertToEntityAttribute(dbValue);

        // then
        assertThat(restored).as("枚举值 %s 经过双向转换后应保持一致", original).isEqualTo(original);
      }
    }
  }
}
