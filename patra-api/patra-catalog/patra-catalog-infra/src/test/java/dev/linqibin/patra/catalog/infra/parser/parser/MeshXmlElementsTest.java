package dev.linqibin.patra.catalog.infra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshXmlElements 常量类单元测试。
///
/// 验证所有 XML 元素名称常量值正确。
@DisplayName("MeshXmlElements 常量类")
class MeshXmlElementsTest {

  @Nested
  @DisplayName("Record 根元素")
  class RecordElements {

    @Test
    @DisplayName("DESCRIPTOR 应为 DescriptorRecord")
    void descriptor_shouldBeDescriptorRecord() {
      assertEquals("DescriptorRecord", MeshXmlElements.Record.DESCRIPTOR);
    }

    @Test
    @DisplayName("QUALIFIER 应为 QualifierRecord")
    void qualifier_shouldBeQualifierRecord() {
      assertEquals("QualifierRecord", MeshXmlElements.Record.QUALIFIER);
    }

    @Test
    @DisplayName("CONCEPT 应为 Concept")
    void concept_shouldBeConcept() {
      assertEquals("Concept", MeshXmlElements.Record.CONCEPT);
    }

    @Test
    @DisplayName("TERM 应为 Term")
    void term_shouldBeTerm() {
      assertEquals("Term", MeshXmlElements.Record.TERM);
    }

    @Test
    @DisplayName("TREE_NUMBER 应为 TreeNumber")
    void treeNumber_shouldBeTreeNumber() {
      assertEquals("TreeNumber", MeshXmlElements.Record.TREE_NUMBER);
    }
  }

  @Nested
  @DisplayName("Identifier 标识符元素")
  class IdentifierElements {

    @Test
    @DisplayName("DESCRIPTOR_UI 应为 DescriptorUI")
    void descriptorUi_shouldBeDescriptorUI() {
      assertEquals("DescriptorUI", MeshXmlElements.Identifier.DESCRIPTOR_UI);
    }

    @Test
    @DisplayName("QUALIFIER_UI 应为 QualifierUI")
    void qualifierUi_shouldBeQualifierUI() {
      assertEquals("QualifierUI", MeshXmlElements.Identifier.QUALIFIER_UI);
    }

    @Test
    @DisplayName("CONCEPT_UI 应为 ConceptUI")
    void conceptUi_shouldBeConceptUI() {
      assertEquals("ConceptUI", MeshXmlElements.Identifier.CONCEPT_UI);
    }

    @Test
    @DisplayName("TERM_UI 应为 TermUI")
    void termUi_shouldBeTermUI() {
      assertEquals("TermUI", MeshXmlElements.Identifier.TERM_UI);
    }

    @Test
    @DisplayName("CONCEPT1_UI 应为 Concept1UI")
    void concept1Ui_shouldBeConcept1UI() {
      assertEquals("Concept1UI", MeshXmlElements.Identifier.CONCEPT1_UI);
    }

    @Test
    @DisplayName("CONCEPT2_UI 应为 Concept2UI")
    void concept2Ui_shouldBeConcept2UI() {
      assertEquals("Concept2UI", MeshXmlElements.Identifier.CONCEPT2_UI);
    }
  }

  @Nested
  @DisplayName("Name 名称元素")
  class NameElements {

    @Test
    @DisplayName("DESCRIPTOR_NAME 应为 DescriptorName")
    void descriptorName_shouldBeDescriptorName() {
      assertEquals("DescriptorName", MeshXmlElements.Name.DESCRIPTOR_NAME);
    }

    @Test
    @DisplayName("QUALIFIER_NAME 应为 QualifierName")
    void qualifierName_shouldBeQualifierName() {
      assertEquals("QualifierName", MeshXmlElements.Name.QUALIFIER_NAME);
    }

    @Test
    @DisplayName("CONCEPT_NAME 应为 ConceptName")
    void conceptName_shouldBeConceptName() {
      assertEquals("ConceptName", MeshXmlElements.Name.CONCEPT_NAME);
    }

    @Test
    @DisplayName("STRING 应为 String")
    void string_shouldBeString() {
      assertEquals("String", MeshXmlElements.Name.STRING);
    }
  }

  @Nested
  @DisplayName("Date 日期元素")
  class DateElements {

    @Test
    @DisplayName("DATE_CREATED 应为 DateCreated")
    void dateCreated_shouldBeDateCreated() {
      assertEquals("DateCreated", MeshXmlElements.Date.DATE_CREATED);
    }

    @Test
    @DisplayName("DATE_REVISED 应为 DateRevised")
    void dateRevised_shouldBeDateRevised() {
      assertEquals("DateRevised", MeshXmlElements.Date.DATE_REVISED);
    }

    @Test
    @DisplayName("DATE_ESTABLISHED 应为 DateEstablished")
    void dateEstablished_shouldBeDateEstablished() {
      assertEquals("DateEstablished", MeshXmlElements.Date.DATE_ESTABLISHED);
    }

    @Test
    @DisplayName("YEAR 应为 Year")
    void year_shouldBeYear() {
      assertEquals("Year", MeshXmlElements.Date.YEAR);
    }

    @Test
    @DisplayName("MONTH 应为 Month")
    void month_shouldBeMonth() {
      assertEquals("Month", MeshXmlElements.Date.MONTH);
    }

    @Test
    @DisplayName("DAY 应为 Day")
    void day_shouldBeDay() {
      assertEquals("Day", MeshXmlElements.Date.DAY);
    }
  }

  @Nested
  @DisplayName("List 列表容器元素")
  class ListElements {

    @Test
    @DisplayName("TREE_NUMBER_LIST 应为 TreeNumberList")
    void treeNumberList_shouldBeTreeNumberList() {
      assertEquals("TreeNumberList", MeshXmlElements.List.TREE_NUMBER_LIST);
    }

    @Test
    @DisplayName("CONCEPT_LIST 应为 ConceptList")
    void conceptList_shouldBeConceptList() {
      assertEquals("ConceptList", MeshXmlElements.List.CONCEPT_LIST);
    }

    @Test
    @DisplayName("TERM_LIST 应为 TermList")
    void termList_shouldBeTermList() {
      assertEquals("TermList", MeshXmlElements.List.TERM_LIST);
    }

    @Test
    @DisplayName("REGISTRY_NUMBER_LIST 应为 RegistryNumberList")
    void registryNumberList_shouldBeRegistryNumberList() {
      assertEquals("RegistryNumberList", MeshXmlElements.List.REGISTRY_NUMBER_LIST);
    }

    @Test
    @DisplayName("THESAURUS_ID_LIST 应为 ThesaurusIDlist")
    void thesaurusIdList_shouldBeThesaurusIDlist() {
      assertEquals("ThesaurusIDlist", MeshXmlElements.List.THESAURUS_ID_LIST);
    }
  }

  @Nested
  @DisplayName("Attribute 属性名")
  class AttributeElements {

    @Test
    @DisplayName("DESCRIPTOR_CLASS 应为 DescriptorClass")
    void descriptorClass_shouldBeDescriptorClass() {
      assertEquals("DescriptorClass", MeshXmlElements.Attribute.DESCRIPTOR_CLASS);
    }

    @Test
    @DisplayName("PREFERRED_CONCEPT_YN 应为 PreferredConceptYN")
    void preferredConceptYn_shouldBePreferredConceptYN() {
      assertEquals("PreferredConceptYN", MeshXmlElements.Attribute.PREFERRED_CONCEPT_YN);
    }

    @Test
    @DisplayName("LEXICAL_TAG 应为 LexicalTag")
    void lexicalTag_shouldBeLexicalTag() {
      assertEquals("LexicalTag", MeshXmlElements.Attribute.LEXICAL_TAG);
    }
  }
}
