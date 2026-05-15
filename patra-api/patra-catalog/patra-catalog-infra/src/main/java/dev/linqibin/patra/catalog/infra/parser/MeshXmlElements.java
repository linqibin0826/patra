package dev.linqibin.patra.catalog.infra.parser;

/// MeSH XML 元素名称常量。
///
/// 集中管理所有 XML 元素名称，避免硬编码字符串分散在代码各处。
/// 按语义分组组织常量，修改 DTD 时只需更新此类。
///
/// **组织结构**：
/// - `Record`: 记录根元素（DescriptorRecord、QualifierRecord 等）
/// - `Identifier`: 唯一标识符元素（DescriptorUI、ConceptUI 等）
/// - `Name`: 名称相关元素（DescriptorName、String 等）
/// - `Date`: 日期相关元素（DateCreated、Year 等）
/// - `List`: 列表容器元素（TreeNumberList、ConceptList 等）
/// - `Referred`: 引用元素（DescriptorReferredTo、ECIN 等）
/// - `Attribute`: XML 属性名称
/// - `Other`: 其他元素
///
/// @author linqibin
/// @since 0.1.0
public final class MeshXmlElements {

  private MeshXmlElements() {
    throw new UnsupportedOperationException("常量类禁止实例化");
  }

  // ========== 记录根元素 ==========

  /// 记录根元素常量。
  public static final class Record {
    public static final String DESCRIPTOR = "DescriptorRecord";
    public static final String QUALIFIER = "QualifierRecord";
    public static final String SUPPLEMENTAL = "SupplementalRecord";
    public static final String CONCEPT = "Concept";
    public static final String TERM = "Term";
    public static final String TREE_NUMBER = "TreeNumber";

    private Record() {}
  }

  // ========== 标识符元素 ==========

  /// 唯一标识符元素常量。
  public static final class Identifier {
    public static final String DESCRIPTOR_UI = "DescriptorUI";
    public static final String QUALIFIER_UI = "QualifierUI";
    public static final String CONCEPT_UI = "ConceptUI";
    public static final String TERM_UI = "TermUI";
    public static final String CONCEPT1_UI = "Concept1UI";
    public static final String CONCEPT2_UI = "Concept2UI";
    public static final String SUPPLEMENTAL_RECORD_UI = "SupplementalRecordUI";

    private Identifier() {}
  }

  // ========== 名称元素 ==========

  /// 名称相关元素常量。
  public static final class Name {
    public static final String DESCRIPTOR_NAME = "DescriptorName";
    public static final String QUALIFIER_NAME = "QualifierName";
    public static final String CONCEPT_NAME = "ConceptName";
    public static final String SUPPLEMENTAL_RECORD_NAME = "SupplementalRecordName";
    public static final String STRING = "String";

    private Name() {}
  }

  // ========== 日期元素 ==========

  /// 日期相关元素常量。
  public static final class Date {
    public static final String DATE_CREATED = "DateCreated";
    public static final String DATE_REVISED = "DateRevised";
    public static final String DATE_ESTABLISHED = "DateEstablished";
    public static final String YEAR = "Year";
    public static final String MONTH = "Month";
    public static final String DAY = "Day";

    private Date() {}
  }

  // ========== 列表容器元素 ==========

  /// 列表容器元素常量。
  public static final class List {
    public static final String TREE_NUMBER_LIST = "TreeNumberList";
    public static final String ALLOWABLE_QUALIFIERS_LIST = "AllowableQualifiersList";
    public static final String PHARMACOLOGICAL_ACTION_LIST = "PharmacologicalActionList";
    public static final String PREVIOUS_INDEXING_LIST = "PreviousIndexingList";
    public static final String SEE_RELATED_LIST = "SeeRelatedList";
    public static final String CONCEPT_LIST = "ConceptList";
    public static final String TERM_LIST = "TermList";
    public static final String ENTRY_COMBINATION_LIST = "EntryCombinationList";
    public static final String REGISTRY_NUMBER_LIST = "RegistryNumberList";
    public static final String RELATED_REGISTRY_NUMBER_LIST = "RelatedRegistryNumberList";
    public static final String CONCEPT_RELATION_LIST = "ConceptRelationList";
    public static final String THESAURUS_ID_LIST = "ThesaurusIDlist";
    // SCR 特有列表
    public static final String HEADING_MAPPED_TO_LIST = "HeadingMappedToList";
    public static final String SOURCE_LIST = "SourceList";
    public static final String INDEXING_INFORMATION_LIST = "IndexingInformationList";

    private List() {}
  }

  // ========== 引用元素 ==========

  /// 引用相关元素常量。
  public static final class Referred {
    public static final String DESCRIPTOR_REFERRED_TO = "DescriptorReferredTo";
    public static final String QUALIFIER_REFERRED_TO = "QualifierReferredTo";
    public static final String ECIN = "ECIN";
    public static final String ECOUT = "ECOUT";
    // SCR 特有引用
    public static final String SUPPLEMENTAL_RECORD_REFERRED_TO = "SupplementalRecordReferredTo";

    private Referred() {}
  }

  // ========== 属性名称 ==========

  /// XML 属性名称常量。
  public static final class Attribute {
    public static final String DESCRIPTOR_CLASS = "DescriptorClass";
    public static final String PREFERRED_CONCEPT_YN = "PreferredConceptYN";
    public static final String RECORD_PREFERRED_TERM_YN = "RecordPreferredTermYN";
    public static final String CONCEPT_PREFERRED_TERM_YN = "ConceptPreferredTermYN";
    public static final String IS_PERMUTED_TERM_YN = "IsPermutedTermYN";
    public static final String LEXICAL_TAG = "LexicalTag";
    public static final String RELATION_NAME = "RelationName";
    public static final String PRINT_FLAG_YN = "PrintFlagYN";
    // SCR 特有属性
    public static final String SCR_CLASS = "SCRClass";

    private Attribute() {}
  }

  // ========== 其他元素 ==========

  /// 其他元素常量。
  public static final class Other {
    // 文本注释类
    public static final String HISTORY_NOTE = "HistoryNote";
    public static final String ONLINE_NOTE = "OnlineNote";
    public static final String PUBLIC_MESH_NOTE = "PublicMeSHNote";
    public static final String NLM_CLASSIFICATION_NUMBER = "NLMClassificationNumber";
    public static final String ANNOTATION = "Annotation";
    public static final String CONSIDER_ALSO = "ConsiderAlso";
    public static final String SCOPE_NOTE = "ScopeNote";
    public static final String NOTE = "Note";
    public static final String FREQUENCY = "Frequency";

    // 列表项类
    public static final String ALLOWABLE_QUALIFIER = "AllowableQualifier";
    public static final String PHARMACOLOGICAL_ACTION = "PharmacologicalAction";
    public static final String PREVIOUS_INDEXING = "PreviousIndexing";
    public static final String SEE_RELATED_DESCRIPTOR = "SeeRelatedDescriptor";
    public static final String ENTRY_COMBINATION = "EntryCombination";
    public static final String REGISTRY_NUMBER = "RegistryNumber";
    public static final String RELATED_REGISTRY_NUMBER = "RelatedRegistryNumber";
    public static final String CONCEPT_RELATION = "ConceptRelation";
    public static final String THESAURUS_ID = "ThesaurusID";

    // Term 相关
    public static final String ABBREVIATION = "Abbreviation";
    public static final String ENTRY_VERSION = "EntryVersion";
    public static final String SORT_VERSION = "SortVersion";
    public static final String TERM_NOTE = "TermNote";

    // Concept 相关
    public static final String CASN1_NAME = "CASN1Name";
    public static final String CONCEPT_STATUS = "ConceptStatus";
    public static final String TRANSLATORS_ENGLISH_SCOPE_NOTE = "TranslatorsEnglishScopeNote";
    public static final String TRANSLATORS_SCOPE_NOTE = "TranslatorsScopeNote";

    // SCR 特有元素
    public static final String HEADING_MAPPED_TO = "HeadingMappedTo";
    public static final String SOURCE = "Source";
    public static final String INDEXING_INFORMATION = "IndexingInformation";

    private Other() {}
  }
}
