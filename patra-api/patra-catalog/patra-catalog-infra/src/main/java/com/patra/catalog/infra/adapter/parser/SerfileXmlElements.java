package com.patra.catalog.infra.adapter.parser;

/// NLM Serfile XML 元素名称常量。
///
/// 集中管理所有 Serfile XML 元素名称，避免硬编码字符串分散在代码各处。
/// 按语义分组组织常量，修改 DTD 时只需更新此类。
///
/// **DTD 版本**：nlmserials_230101.dtd
///
/// **组织结构**：
/// - `Record`: 记录根元素（SerialsSet、Serial）
/// - `Identifier`: 标识符元素（NlmUniqueID、Coden 等）
/// - `Name`: 名称元素（Title、MedlineTA 等）
/// - `Publication`: 出版信息元素（PublicationInfo、Country 等）
/// - `Issn`: ISSN 相关元素
/// - `Indexing`: 索引相关元素
/// - `MeSH`: MeSH 主题词元素
/// - `Relation`: 期刊关联元素
/// - `Date`: 日期元素
/// - `Attribute`: XML 属性名称
/// - `Value`: 属性值常量
///
/// @author linqibin
/// @since 0.1.0
public final class SerfileXmlElements {

  private SerfileXmlElements() {
    throw new UnsupportedOperationException("常量类禁止实例化");
  }

  // ========== 记录根元素 ==========

  /// 记录根元素常量。
  public static final class Record {
    /// 根元素，包含多个 Serial
    public static final String SERIALS_SET = "SerialsSet";
    /// 单个期刊记录
    public static final String SERIAL = "Serial";

    private Record() {}
  }

  // ========== 标识符元素 ==========

  /// 标识符元素常量。
  public static final class Identifier {
    /// NLM 工作 ID
    public static final String NLM_WORK_ID = "NlmWorkID";
    /// NLM 唯一标识符（主要使用）
    public static final String NLM_UNIQUE_ID = "NlmUniqueID";
    /// CODEN 编码（6字符）
    public static final String CODEN = "Coden";
    /// 关联记录 ID
    public static final String RECORD_ID = "RecordID";

    private Identifier() {}
  }

  // ========== 名称元素 ==========

  /// 名称相关元素常量。
  public static final class Name {
    /// 期刊标题
    public static final String TITLE = "Title";
    /// MEDLINE 标题缩写
    public static final String MEDLINE_TA = "MedlineTA";
    /// 排序用名称
    public static final String SORT_SERIAL_NAME = "SortSerialName";
    /// 关联期刊标题（在 TitleRelated 内）
    public static final String XR_TITLE = "XrTitle";

    private Name() {}
  }

  // ========== 出版信息元素 ==========

  /// 出版信息元素常量。
  public static final class Publication {
    /// 出版信息容器
    public static final String PUBLICATION_INFO = "PublicationInfo";
    /// 国家
    public static final String COUNTRY = "Country";
    /// 出版地
    public static final String PLACE = "Place";
    /// 出版商
    public static final String PUBLISHER = "Publisher";
    /// 创刊年份
    public static final String PUBLICATION_FIRST_YEAR = "PublicationFirstYear";
    /// 停刊年份
    public static final String PUBLICATION_END_YEAR = "PublicationEndYear";
    /// 出版频率
    public static final String FREQUENCY = "Frequency";
    /// 出版日期描述
    public static final String DATES_OF_SERIAL_PUBLICATION = "DatesOfSerialPublication";

    private Publication() {}
  }

  // ========== ISSN 元素 ==========

  /// ISSN 相关元素常量。
  public static final class Issn {
    /// ISSN（可多个，Print/Electronic）
    public static final String ISSN = "ISSN";
    /// Linking ISSN
    public static final String ISSN_LINKING = "ISSNLinking";

    private Issn() {}
  }

  // ========== 索引相关元素 ==========

  /// 索引相关元素常量。
  public static final class Indexing {
    /// 索引历史列表容器
    public static final String INDEXING_HISTORY_LIST = "IndexingHistoryList";
    /// 单个索引历史记录
    public static final String INDEXING_HISTORY = "IndexingHistory";
    /// 操作日期
    public static final String DATE_OF_ACTION = "DateOfAction";
    /// 覆盖范围
    public static final String COVERAGE = "Coverage";
    /// 覆盖范围备注
    public static final String COVERAGE_NOTE = "CoverageNote";
    /// 是否当前正在索引
    public static final String CURRENTLY_INDEXED_YN = "CurrentlyIndexedYN";
    /// 当前索引子集
    public static final String CURRENTLY_INDEXED_FOR_SUBSET = "CurrentlyIndexedForSubset";
    /// 索引子集
    public static final String INDEXING_SUBSET = "IndexingSubset";
    /// 索引起始日期
    public static final String INDEXING_START_DATE = "IndexingStartDate";
    /// 是否在线索引
    public static final String INDEX_ONLINE_YN = "IndexOnlineYN";
    /// 选择性索引 URL
    public static final String INDEXING_SELECTED_URL = "IndexingSelectedURL";
    /// 是否报告给 MEDLINE
    public static final String REPORTED_MEDLINE_YN = "ReportedMedlineYN";
    /// 处理代码
    public static final String PROCESSING_CODE = "ProcessingCode";

    private Indexing() {}
  }

  // ========== MeSH 主题词元素 ==========

  /// MeSH 主题词元素常量。
  public static final class MeSH {
    /// MeSH 主题词列表容器
    public static final String MESH_HEADING_LIST = "MeshHeadingList";
    /// 单个 MeSH 主题词
    public static final String MESH_HEADING = "MeshHeading";
    /// 描述符名称
    public static final String DESCRIPTOR_NAME = "DescriptorName";
    /// 限定符名称
    public static final String QUALIFIER_NAME = "QualifierName";
    /// 广泛期刊类别列表
    public static final String BROAD_JOURNAL_HEADING_LIST = "BroadJournalHeadingList";
    /// 广泛期刊类别
    public static final String BROAD_JOURNAL_HEADING = "BroadJournalHeading";

    private MeSH() {}
  }

  // ========== 期刊关联元素 ==========

  /// 期刊关联元素常量。
  public static final class Relation {
    /// 关联期刊（前身/后继等）
    public static final String TITLE_RELATED = "TitleRelated";
    /// 标题变更标记
    public static final String TITLE_CONTINUATION_YN = "TitleContinuationYN";
    /// 次要标题变更标记
    public static final String MINOR_TITLE_CHANGE_YN = "MinorTitleChangeYN";
    /// 交叉引用列表
    public static final String CROSS_REFERENCE_LIST = "CrossReferenceList";
    /// 交叉引用
    public static final String CROSS_REFERENCE = "CrossReference";
    /// 通用备注
    public static final String GENERAL_NOTE = "GeneralNote";

    private Relation() {}
  }

  // ========== 语言元素 ==========

  /// 语言相关元素常量。
  public static final class Language {
    /// 语言（可多个）
    public static final String LANGUAGE = "Language";

    private Language() {}
  }

  // ========== 日期元素 ==========

  /// 日期相关元素常量。
  public static final class Date {
    public static final String YEAR = "Year";
    public static final String MONTH = "Month";
    public static final String DAY = "Day";
    public static final String HOUR = "Hour";
    public static final String MINUTE = "Minute";
    public static final String SECOND = "Second";
    /// ILS 创建时间戳
    public static final String ILS_CREATED_TIMESTAMP = "IlsCreatedTimestamp";
    /// ILS 更新时间戳
    public static final String ILS_UPDATED_TIMESTAMP = "IlsUpdatedTimestamp";
    /// 删除时间戳
    public static final String DELETED_TIMESTAMP = "DeletedTimestamp";
    /// MEDLINE 数据更新时间戳
    public static final String MEDLINE_DATA_UPDATED_TIMESTAMP = "MedlineDataUpdatedTimestamp";
    /// SEF 创建时间戳
    public static final String SEF_CREATED_TIMESTAMP = "SEFCreatedTimestamp";
    /// SEF 更新时间戳
    public static final String SEF_UPDATED_TIMESTAMP = "SEFUpdatedTimestamp";

    private Date() {}
  }

  // ========== XML 属性名称 ==========

  /// XML 属性名称常量。
  public static final class Attribute {
    /// ISSN 类型（Print/Electronic/Undetermined）
    public static final String ISSN_TYPE = "IssnType";
    /// 关联类型（Preceding/Succeeding/Absorbed 等）
    public static final String TITLE_TYPE = "TitleType";
    /// 是否主要主题（Y/N）
    public static final String MAJOR_TOPIC_YN = "MajorTopicYN";
    /// 引用子集（IM/AIM/D 等）
    public static final String CITATION_SUBSET = "CitationSubset";
    /// 索引处理方式（Full/Selective 等）
    public static final String INDEXING_TREATMENT = "IndexingTreatment";
    /// 索引状态（Currently-indexed/Ceased-publication 等）
    public static final String INDEXING_STATUS = "IndexingStatus";
    /// 语言类型（Primary/Summary）
    public static final String LANG_TYPE = "LangType";
    /// 频率类型（Current/Former）
    public static final String FREQUENCY_TYPE = "FrequencyType";
    /// 数据创建方法
    public static final String DATA_CREATION_METHOD = "DataCreationMethod";
    /// 是否 MEDLINE 打印版
    public static final String MED_PRINT_YN = "MedPrintYN";
    /// PMC 状态
    public static final String PMC = "PMC";
    /// 状态（NLMCollection/NotNLMCollection）
    public static final String STATUS = "Status";
    /// 交叉引用类型
    public static final String XR_TYPE = "XrType";
    /// 记录 ID 来源
    public static final String SOURCE = "Source";
    /// 备注类型
    public static final String NOTE_TYPE = "NoteType";
    /// 当前子集
    public static final String CURRENT_SUBSET = "CurrentSubset";
    /// 当前索引处理
    public static final String CURRENT_INDEXING_TREATMENT = "CurrentIndexingTreatment";
    /// 描述符类型（Geographic）
    public static final String TYPE = "Type";
    /// 描述符 UI（唯一标识符）
    public static final String UI = "UI";

    private Attribute() {}
  }

  // ========== 属性值常量 ==========

  /// 属性值常量。
  public static final class Value {
    // ISSN 类型
    public static final String ISSN_TYPE_PRINT = "Print";
    public static final String ISSN_TYPE_ELECTRONIC = "Electronic";
    public static final String ISSN_TYPE_UNDETERMINED = "Undetermined";

    // 主要主题标记
    public static final String YES = "Y";
    public static final String NO = "N";

    // 语言类型
    public static final String LANG_TYPE_PRIMARY = "Primary";
    public static final String LANG_TYPE_SUMMARY = "Summary";

    // 索引处理方式
    public static final String INDEXING_FULL = "Full";
    public static final String INDEXING_SELECTIVE = "Selective";
    public static final String INDEXING_UNKNOWN = "Unknown";
    public static final String INDEXING_REFERENCED_IN = "ReferencedIn";
    public static final String INDEXING_REFERENCED_IN_NO_DETAILS = "ReferencedInNoDetails";

    // 索引状态
    public static final String STATUS_CURRENTLY_INDEXED = "Currently-indexed";
    public static final String STATUS_CEASED_PUBLICATION = "Ceased-publication";
    public static final String STATUS_CONTINUED_BY_ANOTHER = "Continued-by-another-title";
    public static final String STATUS_DESELECTED = "Deselected";

    private Value() {}
  }
}
