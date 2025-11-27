package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.AllowableQualifier;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.SeeRelatedDescriptor;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// StAX XML 解析器实现。
///
/// 使用 JDK 内置的 StAX（Streaming API for XML）实现流式解析，支持大文件处理。
///
/// **设计原则**：
///
/// - 流式处理：使用 {@link XMLStreamReader} 逐元素读取，内存占用可控（<2GB）
///   - 惰性求值：返回 {@link Stream}，由调用方控制处理速度
///   - 资源管理：使用 Spliterator 封装，确保流关闭时释放资源
///   - 错误处理：格式错误时跳过记录并记录日志，不中断整个流
///
/// **性能特征**：
///
/// - 内存占用：<2GB（流式处理，不一次性加载整个 XML）
///   - 处理速度：约 1000 条/秒（取决于硬件和批次大小）
///   - 文件大小：支持 299MB 的 XML 文件
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class XmlParserAdapter implements XmlParserPort {

  private static final XMLInputFactory XML_INPUT_FACTORY;

  static {
    XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    // 禁用外部实体引用，防止 XXE 攻击（OWASP A03:2021 Injection）
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // 设置空的 XMLResolver，忽略所有外部 DTD/实体引用（避免 DOCTYPE 解析错误）
    XML_INPUT_FACTORY.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
  }

  @Override
  public Stream<MeshDescriptorAggregate> parseDescriptors(
      InputStream xmlInputStream, String meshVersion) {
    log.info("开始解析 MeSH Descriptor XML 文件，版本：{}", meshVersion);

    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
      DescriptorSpliterator spliterator = new DescriptorSpliterator(reader, meshVersion);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new RuntimeException("XML 解析失败", e);
    }
  }

  @Override
  public Stream<MeshQualifierAggregate> parseQualifiers(Path filePath, String meshVersion) {
    log.info("开始解析 MeSH Qualifier XML 文件：{}，版本：{}", filePath, meshVersion);

    try {
      InputStream inputStream = Files.newInputStream(filePath);
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
      QualifierSpliterator spliterator = new QualifierSpliterator(reader, meshVersion);
      return StreamSupport.stream(spliterator, false)
          .onClose(() -> closeReader(reader))
          .onClose(() -> closeInputStream(inputStream));
    } catch (IOException e) {
      log.error("打开文件失败：{}", filePath, e);
      throw new RuntimeException("打开 XML 文件失败：" + filePath, e);
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new RuntimeException("XML 解析失败", e);
    }
  }

  @Override
  public Stream<MeshTreeNumber> parseTreeNumbers(InputStream xmlInputStream) {
    log.info("开始解析 MeSH TreeNumber XML 文件");

    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
      TreeNumberSpliterator spliterator = new TreeNumberSpliterator(reader);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new RuntimeException("XML 解析失败", e);
    }
  }

  @Override
  public Stream<MeshEntryTerm> parseEntryTerms(InputStream xmlInputStream) {
    log.info("开始解析 MeSH EntryTerm XML 文件");

    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
      EntryTermSpliterator spliterator = new EntryTermSpliterator(reader);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new RuntimeException("XML 解析失败", e);
    }
  }

  @Override
  public Stream<MeshConcept> parseConcepts(InputStream xmlInputStream) {
    log.info("开始解析 MeSH Concept XML 文件");

    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
      ConceptSpliterator spliterator = new ConceptSpliterator(reader);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new RuntimeException("XML 解析失败", e);
    }
  }

  /// 关闭 XMLStreamReader。
  private void closeReader(XMLStreamReader reader) {
    try {
      reader.close();
      log.debug("XMLStreamReader 已关闭");
    } catch (XMLStreamException e) {
      log.warn("关闭 XMLStreamReader 失败", e);
    }
  }

  /// 关闭输入流。
  private void closeInputStream(InputStream inputStream) {
    try {
      inputStream.close();
      log.debug("InputStream 已关闭");
    } catch (IOException e) {
      log.warn("关闭 InputStream 失败", e);
    }
  }

  // ========== Spliterator 实现 ==========

  /// Descriptor Spliterator（用于流式解析 Descriptor）。
  private static class DescriptorSpliterator
      implements Spliterator<MeshDescriptorAggregate> {

    private final XMLStreamReader reader;
    private final String meshVersion;
    private boolean hasNext = true;

    public DescriptorSpliterator(XMLStreamReader reader, String meshVersion) {
      this.reader = reader;
      this.meshVersion = meshVersion;
    }

    @Override
    public boolean tryAdvance(java.util.function.Consumer<? super MeshDescriptorAggregate> action) {
      if (!hasNext) {
        return false;
      }

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT
              && "DescriptorRecord".equals(reader.getLocalName())) {
            // 解析单个 DescriptorRecord
            MeshDescriptorAggregate descriptor = parseDescriptorRecord(reader, meshVersion);
            if (descriptor != null) {
              action.accept(descriptor);
              return true;
            }
          }
        }
        hasNext = false;
        return false;
      } catch (XMLStreamException e) {
        log.error("解析 Descriptor 失败", e);
        throw new RuntimeException("XML 解析失败", e);
      }
    }

    @Override
    public Spliterator<MeshDescriptorAggregate> trySplit() {
      return null; // 不支持并行
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE; // 未知大小
    }

    @Override
    public int characteristics() {
      return ORDERED | NONNULL | IMMUTABLE;
    }

    /// 解析单个 DescriptorRecord。
    private MeshDescriptorAggregate parseDescriptorRecord(
        XMLStreamReader reader, String meshVersion) throws XMLStreamException {
      // 必填字段
      String descriptorUI = null;
      String descriptorName = null;
      DescriptorClass descriptorClass = DescriptorClass.TOPICAL; // 默认值

      // 日期字段
      String dateCreated = null;
      String dateRevised = null;
      String dateEstablished = null;

      // 可选文本字段
      String historyNote = null;
      String onlineNote = null;
      String publicMeshNote = null;
      String nlmClassificationNumber = null;
      String annotation = null;
      String considerAlso = null;
      String scopeNote = null;

      // 集合字段
      List<MeshTreeNumber> treeNumbers = new ArrayList<>();
      List<AllowableQualifier> allowableQualifiers = new ArrayList<>();
      List<PharmacologicalAction> pharmacologicalActions = new ArrayList<>();
      List<String> previousIndexings = new ArrayList<>();
      List<SeeRelatedDescriptor> seeRelatedDescriptors = new ArrayList<>();
      List<MeshConcept> concepts = new ArrayList<>();
      List<MeshEntryTerm> entryTerms = new ArrayList<>();
      List<EntryCombination> entryCombinations = new ArrayList<>();

      // 解析 DescriptorClass 属性
      String descriptorClassAttr = reader.getAttributeValue(null, "DescriptorClass");
      if (descriptorClassAttr != null) {
        try {
          descriptorClass = DescriptorClass.fromCode(descriptorClassAttr);
        } catch (IllegalArgumentException e) {
          log.warn("未知的 DescriptorClass 值：{}，使用默认值 TOPICAL", descriptorClassAttr);
        }
      }

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "DescriptorUI":
              descriptorUI = reader.getElementText();
              break;
            case "DescriptorName":
              // DescriptorName 包含一个 <String> 子元素
              descriptorName = parseNameElement(reader);
              break;
            case "DateCreated":
              dateCreated = parseDate(reader, "DateCreated");
              break;
            case "DateRevised":
              dateRevised = parseDate(reader, "DateRevised");
              break;
            case "DateEstablished":
              dateEstablished = parseDate(reader, "DateEstablished");
              break;
            case "HistoryNote":
              historyNote = reader.getElementText().trim();
              break;
            case "OnlineNote":
              onlineNote = reader.getElementText().trim();
              break;
            case "PublicMeSHNote":
              publicMeshNote = reader.getElementText().trim();
              break;
            case "NLMClassificationNumber":
              nlmClassificationNumber = reader.getElementText().trim();
              break;
            case "Annotation":
              annotation = reader.getElementText().trim();
              break;
            case "ConsiderAlso":
              considerAlso = reader.getElementText().trim();
              break;
            case "ScopeNote":
              scopeNote = reader.getElementText().trim();
              break;
            case "AllowableQualifiersList":
              allowableQualifiers = parseAllowableQualifiersList(reader);
              break;
            case "PharmacologicalActionList":
              pharmacologicalActions = parsePharmacologicalActionList(reader);
              break;
            case "PreviousIndexingList":
              previousIndexings = parsePreviousIndexingList(reader);
              break;
            case "SeeRelatedList":
              seeRelatedDescriptors = parseSeeRelatedList(reader);
              break;
            case "TreeNumberList":
              treeNumbers = parseTreeNumberList(reader, descriptorUI);
              break;
            case "EntryCombinationList":
              entryCombinations = parseEntryCombinationList(reader);
              break;
            case "ConceptList":
              // 解析 ConceptList，提取所有 Concept 和 EntryTerm
              parseConceptListIntoAggregate(reader, concepts, entryTerms);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "DescriptorRecord".equals(reader.getLocalName())) {
          break;
        }
      }

      // 验证必填字段
      if (descriptorUI == null || descriptorName == null) {
        log.warn("跳过无效 Descriptor（缺少必填字段）: UI={}, Name={}", descriptorUI, descriptorName);
        return null;
      }

      // 创建聚合根（使用 Domain 层的工厂方法）
      MeshDescriptorAggregate aggregate =
          MeshDescriptorAggregate.create(
              MeshUI.of(descriptorUI), descriptorName, descriptorClass, meshVersion);

      // 设置日期字段
      if (dateCreated != null) {
        aggregate.setDateCreated(dateCreated);
      }
      if (dateRevised != null) {
        aggregate.setDateRevised(dateRevised);
      }
      if (dateEstablished != null) {
        aggregate.setDateEstablished(dateEstablished);
      }

      // 设置可选文本字段
      if (historyNote != null) {
        aggregate.setHistoryNote(historyNote);
      }
      if (onlineNote != null) {
        aggregate.setOnlineNote(onlineNote);
      }
      if (nlmClassificationNumber != null) {
        aggregate.setNlmClassificationNumber(nlmClassificationNumber);
      }
      if (annotation != null) {
        aggregate.setAnnotation(annotation);
      }
      if (considerAlso != null) {
        aggregate.setConsiderAlso(considerAlso);
      }
      if (scopeNote != null) {
        aggregate.setScopeNote(scopeNote);
      }
      if (publicMeshNote != null) {
        aggregate.setPublicMeshNote(publicMeshNote);
      }

      // 添加集合数据
      if (!treeNumbers.isEmpty()) {
        aggregate.addTreeNumbers(treeNumbers);
      }
      if (!allowableQualifiers.isEmpty()) {
        aggregate.addAllowableQualifiers(allowableQualifiers);
      }
      if (!pharmacologicalActions.isEmpty()) {
        aggregate.addPharmacologicalActions(pharmacologicalActions);
      }
      if (!previousIndexings.isEmpty()) {
        aggregate.addPreviousIndexings(previousIndexings);
      }
      if (!seeRelatedDescriptors.isEmpty()) {
        aggregate.addSeeRelatedDescriptors(seeRelatedDescriptors);
      }
      if (!concepts.isEmpty()) {
        aggregate.addConcepts(concepts);
      }
      if (!entryTerms.isEmpty()) {
        aggregate.addEntryTerms(entryTerms);
      }
      if (!entryCombinations.isEmpty()) {
        aggregate.addEntryCombinations(entryCombinations);
      }

      return aggregate;
    }

    /// 解析 TreeNumberList。
    private List<MeshTreeNumber> parseTreeNumberList(XMLStreamReader reader, String descriptorUI)
        throws XMLStreamException {
      List<MeshTreeNumber> treeNumbers = new ArrayList<>();
      int index = 0;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "TreeNumber".equals(reader.getLocalName())) {
          String treeNumber = reader.getElementText();
          // 第一个树形编号标记为主要位置
          boolean isPrimary = (index == 0);
          treeNumbers.add(MeshTreeNumber.create(treeNumber, isPrimary));
          index++;
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "TreeNumberList".equals(reader.getLocalName())) {
          break;
        }
      }
      return treeNumbers;
    }

    /// 解析 ConceptList，提取 Concept 和 EntryTerm。
    ///
    /// 根据 2025 DTD 结构：
    /// ```
    /// ConceptList
    ///   └── Concept (many)
    ///         ├── ConceptUI
    ///         ├── ConceptName
    ///         ├── RegistryNumber / RegistryNumberList
    ///         ├── ScopeNote
    ///         ├── TranslatorsEnglishScopeNote
    ///         ├── TranslatorsScopeNote
    ///         └── TermList
    ///               └── Term (many - 作为 EntryTerm)
    /// ```
    ///
    /// @param reader XML 读取器
    /// @param concepts 存放解析出的 Concept 列表
    /// @param entryTerms 存放解析出的 EntryTerm 列表
    private void parseConceptListIntoAggregate(
        XMLStreamReader reader, List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms)
        throws XMLStreamException {
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "Concept".equals(reader.getLocalName())) {
          parseConceptAndTerms(reader, concepts, entryTerms);
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "ConceptList".equals(reader.getLocalName())) {
          break;
        }
      }
    }

    /// 解析单个 Concept 及其 TermList。
    private void parseConceptAndTerms(
        XMLStreamReader reader, List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms)
        throws XMLStreamException {
      String conceptUi = null;
      String conceptName = null;
      boolean isPreferred = false;
      List<String> registryNumbers = new ArrayList<>();
      String scopeNote = null;
      String casn1Name = null;
      String conceptStatus = null;
      String translatorsEnglishScopeNote = null;
      String translatorsScopeNote = null;
      List<String> relatedRegistryNumbers = new ArrayList<>();
      List<ConceptRelation> conceptRelations = new ArrayList<>();

      // 解析 PreferredConceptYN 属性
      String preferredAttr = reader.getAttributeValue(null, "PreferredConceptYN");
      if (preferredAttr != null) {
        isPreferred = "Y".equalsIgnoreCase(preferredAttr);
      }

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "ConceptUI":
              conceptUi = reader.getElementText();
              break;
            case "ConceptName":
              conceptName = parseNameElement(reader);
              break;
            case "RegistryNumber":
              // 单个 RegistryNumber（旧版 DTD）
              String regNum = reader.getElementText();
              if (regNum != null && !regNum.trim().isEmpty()) {
                registryNumbers.add(regNum.trim());
              }
              break;
            case "RegistryNumberList":
              // RegistryNumberList（2025 DTD）
              registryNumbers.addAll(parseRegistryNumberList(reader));
              break;
            case "ScopeNote":
              scopeNote = reader.getElementText();
              break;
            case "CASN1Name":
              casn1Name = reader.getElementText();
              break;
            case "ConceptStatus":
              conceptStatus = reader.getElementText();
              break;
            case "TranslatorsEnglishScopeNote":
              translatorsEnglishScopeNote = reader.getElementText();
              break;
            case "TranslatorsScopeNote":
              translatorsScopeNote = reader.getElementText();
              break;
            case "RelatedRegistryNumberList":
              relatedRegistryNumbers = parseRelatedRegistryNumberList(reader);
              break;
            case "ConceptRelationList":
              conceptRelations = parseConceptRelationList(reader);
              break;
            case "TermList":
              // 解析 TermList 中的 EntryTerm，并关联 conceptUi
              parseTermListIntoEntryTerms(reader, entryTerms, conceptUi);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "Concept".equals(reader.getLocalName())) {
          break;
        }
      }

      // 创建并添加 Concept
      if (conceptUi != null && conceptName != null) {
        MeshConcept concept = MeshConcept.create(MeshUI.of(conceptUi), conceptName, isPreferred);
        if (!registryNumbers.isEmpty()) {
          concept.addRegistryNumbers(registryNumbers);
        }
        if (scopeNote != null) {
          concept.withScopeNote(scopeNote);
        }
        if (casn1Name != null) {
          concept.withCasn1Name(casn1Name);
        }
        if (conceptStatus != null) {
          concept.withConceptStatus(conceptStatus);
        }
        if (translatorsEnglishScopeNote != null) {
          concept.withTranslatorsEnglishScopeNote(translatorsEnglishScopeNote);
        }
        if (translatorsScopeNote != null) {
          concept.withTranslatorsScopeNote(translatorsScopeNote);
        }
        if (!relatedRegistryNumbers.isEmpty()) {
          concept.addRelatedRegistryNumbers(relatedRegistryNumbers);
        }
        if (!conceptRelations.isEmpty()) {
          concept.addConceptRelations(conceptRelations);
        }
        concepts.add(concept);
      } else {
        log.warn("跳过无效 Concept（缺少必填字段）: UI={}, Name={}", conceptUi, conceptName);
      }
    }

    /// 解析 RegistryNumberList（2025 DTD）。
    private List<String> parseRegistryNumberList(XMLStreamReader reader) throws XMLStreamException {
      List<String> registryNumbers = new ArrayList<>();
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "RegistryNumber".equals(reader.getLocalName())) {
          String regNum = reader.getElementText();
          if (regNum != null && !regNum.trim().isEmpty()) {
            registryNumbers.add(regNum.trim());
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "RegistryNumberList".equals(reader.getLocalName())) {
          break;
        }
      }
      return registryNumbers;
    }

    /// 解析 TermList 中的 Term 并添加到 EntryTerm 列表。
    private void parseTermListIntoEntryTerms(
        XMLStreamReader reader, List<MeshEntryTerm> entryTerms, String conceptUi)
        throws XMLStreamException {
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT && "Term".equals(reader.getLocalName())) {
          MeshEntryTerm entryTerm = parseTermElement(reader, conceptUi);
          if (entryTerm != null) {
            entryTerms.add(entryTerm);
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "TermList".equals(reader.getLocalName())) {
          break;
        }
      }
    }

    /// 解析 Term 元素（在 Descriptor 解析上下文中）。
    private MeshEntryTerm parseTermElement(XMLStreamReader reader, String conceptUi)
        throws XMLStreamException {
      // 从属性读取字段
      String lexicalTagCode = reader.getAttributeValue(null, "LexicalTag");
      if (lexicalTagCode == null) {
        lexicalTagCode = "NON";
      }

      String recordPreferredAttr = reader.getAttributeValue(null, "RecordPreferredTermYN");
      boolean isRecordPreferred = "Y".equalsIgnoreCase(recordPreferredAttr);

      String conceptPreferredAttr = reader.getAttributeValue(null, "ConceptPreferredTermYN");
      boolean isConceptPreferred = "Y".equalsIgnoreCase(conceptPreferredAttr);

      String isPermutedAttr = reader.getAttributeValue(null, "IsPermutedTermYN");
      boolean isPermutedTerm = "Y".equalsIgnoreCase(isPermutedAttr);

      boolean isPrintFlag = true;

      // 从子元素读取字段
      String termUI = null;
      String term = null;
      String dateCreated = null;
      String entryVersion = null;
      String abbreviation = null;
      String sortVersion = null;
      String termNote = null;
      List<String> thesaurusIds = new ArrayList<>();

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "TermUI":
              termUI = reader.getElementText();
              break;
            case "String":
              term = reader.getElementText();
              break;
            case "DateCreated":
              dateCreated = parseDate(reader, "DateCreated");
              break;
            case "ThesaurusIDlist":
              thesaurusIds = parseThesaurusIdList(reader);
              break;
            case "EntryVersion":
              entryVersion = reader.getElementText();
              break;
            case "Abbreviation":
              abbreviation = reader.getElementText();
              break;
            case "SortVersion":
              sortVersion = reader.getElementText();
              break;
            case "TermNote":
              termNote = reader.getElementText();
              break;
            case "PrintFlagYN":
              String printFlag = reader.getElementText();
              isPrintFlag = "Y".equalsIgnoreCase(printFlag);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "Term".equals(reader.getLocalName())) {
          break;
        }
      }

      // 验证必填字段
      if (term == null) {
        log.warn("跳过无效 EntryTerm（缺少术语文本）");
        return null;
      }

      // 创建实体
      LexicalTag lexicalTag;
      try {
        lexicalTag = LexicalTag.fromCode(lexicalTagCode);
      } catch (IllegalArgumentException e) {
        log.warn("未知的词法标记：{}，使用默认值 NON", lexicalTagCode);
        lexicalTag = LexicalTag.NON;
      }

      MeshUI meshTermUI = termUI != null ? MeshUI.of(termUI) : null;
      MeshEntryTerm entryTerm =
          MeshEntryTerm.create(
              meshTermUI,
              term,
              lexicalTag,
              isRecordPreferred,
              isPrintFlag,
              isConceptPreferred,
              isPermutedTerm);

      // 设置 conceptUi
      if (conceptUi != null) {
        entryTerm.withConceptUi(MeshUI.of(conceptUi));
      }

      // 设置可选字段
      if (dateCreated != null) {
        entryTerm.withDateCreated(dateCreated);
      }
      if (entryVersion != null) {
        entryTerm.withEntryVersion(entryVersion);
      }
      if (abbreviation != null) {
        entryTerm.withAbbreviation(abbreviation);
      }
      if (sortVersion != null) {
        entryTerm.withSortVersion(sortVersion);
      }
      if (termNote != null) {
        entryTerm.withTermNote(termNote);
      }
      if (!thesaurusIds.isEmpty()) {
        entryTerm.addThesaurusIds(thesaurusIds);
      }

      return entryTerm;
    }

    /// 解析 EntryCombinationList。
    private List<EntryCombination> parseEntryCombinationList(XMLStreamReader reader)
        throws XMLStreamException {
      List<EntryCombination> combinations = new ArrayList<>();
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "EntryCombination".equals(reader.getLocalName())) {
          EntryCombination combination = parseEntryCombination(reader);
          if (combination != null) {
            combinations.add(combination);
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "EntryCombinationList".equals(reader.getLocalName())) {
          break;
        }
      }
      return combinations;
    }

    /// 解析单个 EntryCombination。
    ///
    /// 结构示例：
    /// ```xml
    /// <EntryCombination>
    ///   <ECIN>
    ///     <DescriptorReferredTo>
    ///       <DescriptorUI>D000001</DescriptorUI>
    ///       <DescriptorName><String>Calcimycin</String></DescriptorName>
    ///     </DescriptorReferredTo>
    ///     <QualifierReferredTo>
    ///       <QualifierUI>Q000008</QualifierUI>
    ///       <QualifierName><String>administration & dosage</String></QualifierName>
    ///     </QualifierReferredTo>
    ///   </ECIN>
    ///   <ECOUT>
    ///     <DescriptorReferredTo>...</DescriptorReferredTo>
    ///     <QualifierReferredTo>...</QualifierReferredTo>
    ///   </ECOUT>
    /// </EntryCombination>
    /// ```
    private EntryCombination parseEntryCombination(XMLStreamReader reader)
        throws XMLStreamException {
      String ecinDescriptorUi = null;
      String ecinQualifierUi = null;
      String ecoutDescriptorUi = null;
      String ecoutQualifierUi = null;

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          if ("ECIN".equals(localName)) {
            String[] refs = parseEcInOutElement(reader, "ECIN");
            ecinDescriptorUi = refs[0];
            ecinQualifierUi = refs[1];
          } else if ("ECOUT".equals(localName)) {
            String[] refs = parseEcInOutElement(reader, "ECOUT");
            ecoutDescriptorUi = refs[0];
            ecoutQualifierUi = refs[1];
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "EntryCombination".equals(reader.getLocalName())) {
          break;
        }
      }

      // 根据 DTD 定义：ECIN 的 Descriptor 和 Qualifier 都是必填的
      if (ecinDescriptorUi != null && ecinQualifierUi != null && ecoutDescriptorUi != null) {
        return EntryCombination.of(
            MeshUI.of(ecinDescriptorUi),
            MeshUI.of(ecinQualifierUi),
            MeshUI.of(ecoutDescriptorUi),
            ecoutQualifierUi != null ? MeshUI.of(ecoutQualifierUi) : null);
      }

      log.warn(
          "跳过无效 EntryCombination: ecinDescriptorUi={}, ecinQualifierUi={}, ecoutDescriptorUi={}",
          ecinDescriptorUi,
          ecinQualifierUi,
          ecoutDescriptorUi);
      return null;
    }

    /// 解析 ECIN 或 ECOUT 元素，返回 [descriptorUI, qualifierUI]。
    private String[] parseEcInOutElement(XMLStreamReader reader, String elementName)
        throws XMLStreamException {
      String descriptorUi = null;
      String qualifierUi = null;

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          if ("DescriptorReferredTo".equals(localName)) {
            descriptorUi = parseDescriptorReferredTo(reader);
          } else if ("QualifierReferredTo".equals(localName)) {
            qualifierUi = parseQualifierReferredTo(reader);
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && elementName.equals(reader.getLocalName())) {
          break;
        }
      }

      return new String[] {descriptorUi, qualifierUi};
    }

    /// 解析 DescriptorReferredTo 并返回 DescriptorUI。
    private String parseDescriptorReferredTo(XMLStreamReader reader) throws XMLStreamException {
      String descriptorUi = null;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "DescriptorUI".equals(reader.getLocalName())) {
          descriptorUi = reader.getElementText();
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "DescriptorReferredTo".equals(reader.getLocalName())) {
          break;
        }
      }
      return descriptorUi;
    }

    /// 解析 QualifierReferredTo 并返回 QualifierUI。
    private String parseQualifierReferredTo(XMLStreamReader reader) throws XMLStreamException {
      String qualifierUi = null;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "QualifierUI".equals(reader.getLocalName())) {
          qualifierUi = reader.getElementText();
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "QualifierReferredTo".equals(reader.getLocalName())) {
          break;
        }
      }
      return qualifierUi;
    }
  }

  /// Qualifier Spliterator（用于流式解析 Qualifier）。
  private static class QualifierSpliterator
      implements java.util.Spliterator<MeshQualifierAggregate> {

    private final XMLStreamReader reader;
    private final String meshVersion;
    private boolean hasNext = true;

    public QualifierSpliterator(XMLStreamReader reader, String meshVersion) {
      this.reader = reader;
      this.meshVersion = meshVersion;
    }

    @Override
    public boolean tryAdvance(java.util.function.Consumer<? super MeshQualifierAggregate> action) {
      if (!hasNext) {
        return false;
      }

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT
              && "QualifierRecord".equals(reader.getLocalName())) {
            MeshQualifierAggregate qualifier = parseQualifierRecord(reader);
            if (qualifier != null) {
              action.accept(qualifier);
              return true;
            }
          }
        }
        hasNext = false;
        return false;
      } catch (XMLStreamException e) {
        log.error("解析 Qualifier 失败", e);
        throw new RuntimeException("XML 解析失败", e);
      }
    }

    @Override
    public java.util.Spliterator<MeshQualifierAggregate> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return ORDERED | NONNULL | IMMUTABLE;
    }

    /// 解析单个 QualifierRecord 元素。
    private MeshQualifierAggregate parseQualifierRecord(XMLStreamReader reader)
        throws XMLStreamException {
      String qualifierUi = null;
      String name = null;
      String abbreviation = null;
      String annotation = null;
      String dateCreated = null;
      String dateRevised = null;
      String dateEstablished = null;
      String historyNote = null;
      String onlineNote = null;
      List<String> treeNumbers = new ArrayList<>();

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "QualifierUI":
              qualifierUi = reader.getElementText();
              break;
            case "QualifierName":
              name = parseNameElement(reader);
              break;
            case "Annotation":
              annotation = reader.getElementText();
              break;
            case "DateCreated":
              dateCreated = parseDate(reader, "DateCreated");
              break;
            case "DateRevised":
              dateRevised = parseDate(reader, "DateRevised");
              break;
            case "DateEstablished":
              dateEstablished = parseDate(reader, "DateEstablished");
              break;
            case "HistoryNote":
              historyNote = reader.getElementText().trim();
              break;
            case "OnlineNote":
              onlineNote = reader.getElementText().trim();
              break;
            case "TreeNumberList":
              treeNumbers = parseTreeNumberList(reader);
              break;
            case "ConceptList":
              // 从 ConceptList 中提取 Abbreviation
              abbreviation = extractAbbreviationFromPreferredTerm(reader);
              break;
            default:
              // 跳过其他未处理的元素
              skipElement(reader, localName);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "QualifierRecord".equals(reader.getLocalName())) {
          break;
        }
      }

      // 创建聚合根（UI、name、abbreviation 均为必填）
      if (qualifierUi != null && name != null && abbreviation != null && !abbreviation.isBlank()) {
        return MeshQualifierAggregate.create(
                MeshUI.of(qualifierUi), // 直接使用原始 UI，保留格式（支持 7 位和 10 位）
                name,
                abbreviation)
            .withAnnotation(annotation)
            .withDateCreated(dateCreated)
            .withDateRevised(dateRevised)
            .withDateEstablished(dateEstablished)
            .withHistoryNote(historyNote)
            .withOnlineNote(onlineNote)
            .withTreeNumbers(treeNumbers)
            .withActiveStatus(true) // 默认为有效
            .withMeshVersion(meshVersion);
      }

      // 记录详细的跳过原因
      if (qualifierUi == null) {
        log.warn("跳过 Qualifier 记录：缺少 QualifierUI");
      } else if (name == null) {
        log.warn("跳过 Qualifier 记录：UI={}, 缺少 QualifierName", qualifierUi);
      } else {
        log.warn("跳过 Qualifier 记录：UI={}, 缺少 Abbreviation（preferred term 无缩写）", qualifierUi);
      }
      return null;
    }

    /// 解析 TreeNumberList 元素，返回树形编号列表。
    private List<String> parseTreeNumberList(XMLStreamReader reader) throws XMLStreamException {
      List<String> treeNumbers = new ArrayList<>();
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          if ("TreeNumber".equals(reader.getLocalName())) {
            treeNumbers.add(reader.getElementText());
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "TreeNumberList".equals(reader.getLocalName())) {
          break;
        }
      }
      return treeNumbers;
    }

    /// 从 ConceptList 的 preferred term 中提取 Abbreviation。
    ///
    /// 查找 PreferredConceptYN="Y" 的 Concept，
    /// 然后在其 TermList 中找到 RecordPreferredTermYN="Y" 的 Term，
    /// 提取其 Abbreviation 元素。
    private String extractAbbreviationFromPreferredTerm(XMLStreamReader reader)
        throws XMLStreamException {
      String abbreviation = null;
      boolean inPreferredConcept = false;

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          if ("Concept".equals(localName)) {
            // 检查是否为 preferred concept
            String preferredAttr = reader.getAttributeValue(null, "PreferredConceptYN");
            inPreferredConcept = "Y".equals(preferredAttr);
          } else if ("Term".equals(localName) && inPreferredConcept) {
            // 检查是否为 record preferred term
            String recordPreferredAttr = reader.getAttributeValue(null, "RecordPreferredTermYN");
            if ("Y".equals(recordPreferredAttr)) {
              // 进入 Term 元素，查找 Abbreviation
              abbreviation = extractAbbreviationFromTerm(reader);
              if (abbreviation != null) {
                return abbreviation; // 找到后立即返回
              }
            }
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "ConceptList".equals(reader.getLocalName())) {
          break;
        }
      }

      return abbreviation;
    }

    /// 从 Term 元素中提取 Abbreviation。
    private String extractAbbreviationFromTerm(XMLStreamReader reader) throws XMLStreamException {
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "Abbreviation".equals(reader.getLocalName())) {
          return reader.getElementText();
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "Term".equals(reader.getLocalName())) {
          break;
        }
      }
      return null;
    }
  }

  /// TreeNumber Spliterator（用于流式解析 TreeNumber）。
  private static class TreeNumberSpliterator implements java.util.Spliterator<MeshTreeNumber> {

    private final XMLStreamReader reader;
    private boolean hasNext = true;
    private int index = 0;

    public TreeNumberSpliterator(XMLStreamReader reader) {
      this.reader = reader;
    }

    @Override
    public boolean tryAdvance(java.util.function.Consumer<? super MeshTreeNumber> action) {
      if (!hasNext) {
        return false;
      }

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT
              && "TreeNumber".equals(reader.getLocalName())) {
            String treeNumber = reader.getElementText();
            // 第一个树形编号标记为主要位置
            boolean isPrimary = (index == 0);
            MeshTreeNumber entity = MeshTreeNumber.create(treeNumber, isPrimary);
            action.accept(entity);
            index++;
            return true;
          }
        }
        hasNext = false;
        return false;
      } catch (XMLStreamException e) {
        log.error("解析 TreeNumber 失败", e);
        throw new RuntimeException("XML 解析失败", e);
      }
    }

    @Override
    public java.util.Spliterator<MeshTreeNumber> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return ORDERED | NONNULL | IMMUTABLE;
    }
  }

  /// EntryTerm Spliterator（用于流式解析 EntryTerm）。
  private static class EntryTermSpliterator implements java.util.Spliterator<MeshEntryTerm> {

    private final XMLStreamReader reader;
    private boolean hasNext = true;

    public EntryTermSpliterator(XMLStreamReader reader) {
      this.reader = reader;
    }

    @Override
    public boolean tryAdvance(java.util.function.Consumer<? super MeshEntryTerm> action) {
      if (!hasNext) {
        return false;
      }

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT && "Term".equals(reader.getLocalName())) {
            // 解析 Term 元素
            MeshEntryTerm entryTerm = parseTermElement(reader);
            if (entryTerm != null) {
              action.accept(entryTerm);
            }
            return true;
          }
        }
        hasNext = false;
        return false;
      } catch (XMLStreamException e) {
        log.error("解析 EntryTerm 失败", e);
        throw new RuntimeException("XML 解析失败", e);
      }
    }

    /// 解析 Term 元素。
    private MeshEntryTerm parseTermElement(XMLStreamReader reader) throws XMLStreamException {
      // 从属性读取字段
      String lexicalTagCode = reader.getAttributeValue(null, "LexicalTag");
      if (lexicalTagCode == null) {
        lexicalTagCode = "NON"; // 默认值
      }

      String recordPreferredAttr = reader.getAttributeValue(null, "RecordPreferredTermYN");
      boolean isRecordPreferred = "Y".equalsIgnoreCase(recordPreferredAttr);

      String conceptPreferredAttr = reader.getAttributeValue(null, "ConceptPreferredTermYN");
      boolean isConceptPreferred = "Y".equalsIgnoreCase(conceptPreferredAttr);

      String isPermutedAttr = reader.getAttributeValue(null, "IsPermutedTermYN");
      boolean isPermutedTerm = "Y".equalsIgnoreCase(isPermutedAttr);

      // PrintFlagYN 默认为 true（如果属性不存在，则假定可打印）
      boolean isPrintFlag = true;

      // 从子元素读取字段
      String termUI = null;
      String term = null;
      String dateCreated = null;
      String entryVersion = null;
      List<String> thesaurusIds = new ArrayList<>();

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "TermUI":
              termUI = reader.getElementText();
              break;
            case "String":
              term = reader.getElementText();
              break;
            case "DateCreated":
              dateCreated = parseDate(reader, "DateCreated");
              break;
            case "ThesaurusIDlist":
              thesaurusIds = parseThesaurusIdList(reader);
              break;
            case "EntryVersion":
              entryVersion = reader.getElementText();
              break;
            case "PrintFlagYN":
              // 有时 PrintFlagYN 也可能作为子元素出现
              String printFlag = reader.getElementText();
              isPrintFlag = "Y".equalsIgnoreCase(printFlag);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "Term".equals(reader.getLocalName())) {
          break;
        }
      }

      // 验证必填字段
      if (term == null) {
        log.warn("跳过无效 EntryTerm（缺少术语文本）");
        return null;
      }

      // 创建实体
      LexicalTag lexicalTag;
      try {
        lexicalTag = LexicalTag.fromCode(lexicalTagCode);
      } catch (IllegalArgumentException e) {
        log.warn("未知的词法标记：{}，使用默认值 NON", lexicalTagCode);
        lexicalTag = LexicalTag.NON;
      }

      // 使用工厂方法创建 EntryTerm
      MeshUI meshTermUI = termUI != null ? MeshUI.of(termUI) : null;
      MeshEntryTerm entryTerm =
          MeshEntryTerm.create(
              meshTermUI,
              term,
              lexicalTag,
              isRecordPreferred,
              isPrintFlag,
              isConceptPreferred,
              isPermutedTerm);

      // 设置可选字段
      if (dateCreated != null) {
        entryTerm.withDateCreated(dateCreated);
      }
      if (entryVersion != null) {
        entryTerm.withEntryVersion(entryVersion);
      }
      if (!thesaurusIds.isEmpty()) {
        entryTerm.addThesaurusIds(thesaurusIds);
      }

      return entryTerm;
    }

    /// 解析 ThesaurusIDlist。
    private static List<String> parseThesaurusIdList(XMLStreamReader reader)
        throws XMLStreamException {
      List<String> thesaurusIds = new ArrayList<>();
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "ThesaurusID".equals(reader.getLocalName())) {
          String thesaurusId = reader.getElementText();
          if (thesaurusId != null && !thesaurusId.trim().isEmpty()) {
            thesaurusIds.add(thesaurusId.trim());
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "ThesaurusIDlist".equals(reader.getLocalName())) {
          break;
        }
      }
      return thesaurusIds;
    }

    @Override
    public java.util.Spliterator<MeshEntryTerm> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return ORDERED | NONNULL | IMMUTABLE;
    }
  }

  /// Concept Spliterator（用于流式解析 Concept）。
  private static class ConceptSpliterator implements java.util.Spliterator<MeshConcept> {

    private final XMLStreamReader reader;
    private boolean hasNext = true;

    public ConceptSpliterator(XMLStreamReader reader) {
      this.reader = reader;
    }

    @Override
    public boolean tryAdvance(java.util.function.Consumer<? super MeshConcept> action) {
      if (!hasNext) {
        return false;
      }

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT
              && "Concept".equals(reader.getLocalName())) {
            // 解析 Concept 元素
            MeshConcept concept = parseConceptElement(reader);
            if (concept != null) {
              action.accept(concept);
            }
            return true;
          }
        }
        hasNext = false;
        return false;
      } catch (XMLStreamException e) {
        log.error("解析 Concept 失败", e);
        throw new RuntimeException("XML 解析失败", e);
      }
    }

    /// 解析 Concept 元素。
    private MeshConcept parseConceptElement(XMLStreamReader reader) throws XMLStreamException {
      String conceptUi = null;
      String conceptName = null;
      boolean isPreferred = false;
      List<String> registryNumbers = new ArrayList<>();
      String scopeNote = null;
      String casn1Name = null;
      String conceptStatus = null;
      String translatorsEnglishScopeNote = null;
      String translatorsScopeNote = null;
      List<String> relatedRegistryNumbers = new ArrayList<>();
      List<ConceptRelation> conceptRelations = new ArrayList<>();

      // 解析 PreferredConceptYN 属性
      String preferredAttr = reader.getAttributeValue(null, "PreferredConceptYN");
      if (preferredAttr != null) {
        isPreferred = "Y".equalsIgnoreCase(preferredAttr);
      }

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "ConceptUI":
              conceptUi = reader.getElementText();
              break;
            case "ConceptName":
              // ConceptName 包含一个 <String> 子元素
              conceptName = parseNameElement(reader);
              break;
            case "RegistryNumber":
              // 单个 RegistryNumber（旧版 DTD）
              String regNum = reader.getElementText();
              if (regNum != null && !regNum.trim().isEmpty()) {
                registryNumbers.add(regNum.trim());
              }
              break;
            case "RegistryNumberList":
              // RegistryNumberList（2025 DTD）
              registryNumbers.addAll(parseRegistryNumberListElement(reader));
              break;
            case "ScopeNote":
              scopeNote = reader.getElementText();
              break;
            case "CASN1Name":
              casn1Name = reader.getElementText();
              break;
            case "ConceptStatus":
              conceptStatus = reader.getElementText();
              break;
            case "TranslatorsEnglishScopeNote":
              translatorsEnglishScopeNote = reader.getElementText();
              break;
            case "TranslatorsScopeNote":
              translatorsScopeNote = reader.getElementText();
              break;
            case "RelatedRegistryNumberList":
              relatedRegistryNumbers = parseRelatedRegistryNumberList(reader);
              break;
            case "ConceptRelationList":
              conceptRelations = parseConceptRelationList(reader);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "Concept".equals(reader.getLocalName())) {
          break;
        }
      }

      // 验证必填字段
      if (conceptUi == null || conceptName == null) {
        log.warn("跳过无效 Concept（缺少必填字段）: UI={}, Name={}", conceptUi, conceptName);
        return null;
      }

      // 创建实体
      MeshConcept concept = MeshConcept.create(MeshUI.of(conceptUi), conceptName, isPreferred);

      // 设置可选字段
      if (!registryNumbers.isEmpty()) {
        concept.addRegistryNumbers(registryNumbers);
      }
      if (scopeNote != null) {
        concept.withScopeNote(scopeNote);
      }
      if (casn1Name != null) {
        concept.withCasn1Name(casn1Name);
      }
      if (conceptStatus != null) {
        concept.withConceptStatus(conceptStatus);
      }
      if (translatorsEnglishScopeNote != null) {
        concept.withTranslatorsEnglishScopeNote(translatorsEnglishScopeNote);
      }
      if (translatorsScopeNote != null) {
        concept.withTranslatorsScopeNote(translatorsScopeNote);
      }

      // 添加集合数据
      if (!relatedRegistryNumbers.isEmpty()) {
        concept.addRelatedRegistryNumbers(relatedRegistryNumbers);
      }
      if (!conceptRelations.isEmpty()) {
        concept.addConceptRelations(conceptRelations);
      }

      return concept;
    }

    /// 解析 RegistryNumberList（2025 DTD，独立 Concept 解析）。
    private List<String> parseRegistryNumberListElement(XMLStreamReader reader)
        throws XMLStreamException {
      List<String> registryNumbers = new ArrayList<>();
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT
            && "RegistryNumber".equals(reader.getLocalName())) {
          String regNum = reader.getElementText();
          if (regNum != null && !regNum.trim().isEmpty()) {
            registryNumbers.add(regNum.trim());
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "RegistryNumberList".equals(reader.getLocalName())) {
          break;
        }
      }
      return registryNumbers;
    }

    @Override
    public java.util.Spliterator<MeshConcept> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return ORDERED | NONNULL | IMMUTABLE;
    }
  }

  // ========== 辅助方法 ==========

  /// 解析日期元素(Year/Month/Day 结构)。
  ///
  /// @param reader XML 读取器
  /// @param elementName 日期元素名称(如 "DateCreated")
  /// @return 格式化的日期字符串(YYYYMMDD)
  /// @throws XMLStreamException XML 解析异常
  private static String parseDate(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    String year = null;
    String month = null;
    String day = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "Year":
            year = reader.getElementText();
            break;
          case "Month":
            month = reader.getElementText();
            break;
          case "Day":
            day = reader.getElementText();
            break;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && elementName.equals(reader.getLocalName())) {
        break;
      }
    }

    // 组装日期字符串(YYYYMMDD 格式)
    if (year != null && month != null && day != null) {
      return String.format(
          "%s%s%s",
          year, month.length() == 1 ? "0" + month : month, day.length() == 1 ? "0" + day : day);
    }
    return null;
  }

  /// 跳过整个 XML 元素（包括所有子元素）。
  ///
  /// @param reader XML 读取器
  /// @param elementName 要跳过的元素名称
  /// @throws XMLStreamException 如果解析失败
  private static void skipElement(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    int depth = 1; // 当前嵌套深度
    while (reader.hasNext() && depth > 0) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0 && elementName.equals(reader.getLocalName())) {
          break;
        }
      }
    }
  }

  /// 解析 Name 元素（包含 <String> 子元素）。
  private static String parseNameElement(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT && "String".equals(reader.getLocalName())) {
        return reader.getElementText();
      }
    }
    return null;
  }

  /// 解析 AllowableQualifiersList。
  private static List<AllowableQualifier> parseAllowableQualifiersList(XMLStreamReader reader)
      throws XMLStreamException {
    List<AllowableQualifier> qualifiers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "AllowableQualifier".equals(reader.getLocalName())) {
        AllowableQualifier qualifier = parseAllowableQualifier(reader);
        if (qualifier != null) {
          qualifiers.add(qualifier);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "AllowableQualifiersList".equals(reader.getLocalName())) {
        break;
      }
    }
    return qualifiers;
  }

  /// 解析单个 AllowableQualifier。
  private static AllowableQualifier parseAllowableQualifier(XMLStreamReader reader)
      throws XMLStreamException {
    String qualifierUI = null;
    String qualifierName = null;
    String abbreviation = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "QualifierReferredTo":
            // QualifierUI 和 QualifierName 在 QualifierReferredTo 内部
            while (reader.hasNext()) {
              int innerEvent = reader.next();
              if (innerEvent == XMLStreamConstants.START_ELEMENT) {
                String innerName = reader.getLocalName();
                if ("QualifierUI".equals(innerName)) {
                  qualifierUI = reader.getElementText();
                } else if ("QualifierName".equals(innerName)) {
                  qualifierName = parseNameElement(reader);
                }
              } else if (innerEvent == XMLStreamConstants.END_ELEMENT
                  && "QualifierReferredTo".equals(reader.getLocalName())) {
                break;
              }
            }
            break;
          case "Abbreviation":
            abbreviation = reader.getElementText();
            break;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "AllowableQualifier".equals(reader.getLocalName())) {
        break;
      }
    }

    if (qualifierUI != null && qualifierName != null && abbreviation != null) {
      try {
        return AllowableQualifier.of(MeshUI.of(qualifierUI), qualifierName, abbreviation);
      } catch (Exception e) {
        log.warn("解析 AllowableQualifier 失败: UI={}, Name={}", qualifierUI, qualifierName, e);
      }
    }
    return null;
  }

  /// 解析 PharmacologicalActionList。
  private static List<PharmacologicalAction> parsePharmacologicalActionList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PharmacologicalAction> actions = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "PharmacologicalAction".equals(reader.getLocalName())) {
        PharmacologicalAction action = parsePharmacologicalAction(reader);
        if (action != null) {
          actions.add(action);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "PharmacologicalActionList".equals(reader.getLocalName())) {
        break;
      }
    }
    return actions;
  }

  /// 解析单个 PharmacologicalAction。
  private static PharmacologicalAction parsePharmacologicalAction(XMLStreamReader reader)
      throws XMLStreamException {
    String descriptorUI = null;
    String descriptorName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "DescriptorUI":
            descriptorUI = reader.getElementText();
            break;
          case "DescriptorName":
            descriptorName = parseNameElement(reader);
            break;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "PharmacologicalAction".equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorUI != null && descriptorName != null) {
      try {
        return PharmacologicalAction.of(MeshUI.of(descriptorUI), descriptorName);
      } catch (Exception e) {
        log.warn("解析 PharmacologicalAction 失败: UI={}, Name={}", descriptorUI, descriptorName, e);
      }
    }
    return null;
  }

  /// 解析 PreviousIndexingList。
  private static List<String> parsePreviousIndexingList(XMLStreamReader reader)
      throws XMLStreamException {
    List<String> indexings = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "PreviousIndexing".equals(reader.getLocalName())) {
        String indexing = reader.getElementText();
        if (indexing != null && !indexing.trim().isEmpty()) {
          indexings.add(indexing.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "PreviousIndexingList".equals(reader.getLocalName())) {
        break;
      }
    }
    return indexings;
  }

  /// 解析 SeeRelatedList。
  private static List<SeeRelatedDescriptor> parseSeeRelatedList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SeeRelatedDescriptor> descriptors = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "SeeRelatedDescriptor".equals(reader.getLocalName())) {
        SeeRelatedDescriptor descriptor = parseSeeRelatedDescriptor(reader);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "SeeRelatedList".equals(reader.getLocalName())) {
        break;
      }
    }
    return descriptors;
  }

  /// 解析单个 SeeRelatedDescriptor。
  private static SeeRelatedDescriptor parseSeeRelatedDescriptor(XMLStreamReader reader)
      throws XMLStreamException {
    String descriptorUI = null;
    String descriptorName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "DescriptorUI":
            descriptorUI = reader.getElementText();
            break;
          case "DescriptorName":
            descriptorName = parseNameElement(reader);
            break;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "SeeRelatedDescriptor".equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorUI != null && descriptorName != null) {
      try {
        return SeeRelatedDescriptor.of(MeshUI.of(descriptorUI), descriptorName);
      } catch (Exception e) {
        log.warn("解析 SeeRelatedDescriptor 失败: UI={}, Name={}", descriptorUI, descriptorName, e);
      }
    }
    return null;
  }

  /// 解析 RelatedRegistryNumberList。
  private static List<String> parseRelatedRegistryNumberList(XMLStreamReader reader)
      throws XMLStreamException {
    List<String> registryNumbers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "RelatedRegistryNumber".equals(reader.getLocalName())) {
        String registryNumber = reader.getElementText();
        if (registryNumber != null && !registryNumber.trim().isEmpty()) {
          registryNumbers.add(registryNumber.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "RelatedRegistryNumberList".equals(reader.getLocalName())) {
        break;
      }
    }
    return registryNumbers;
  }

  /// 解析 ConceptRelationList。
  private static List<ConceptRelation> parseConceptRelationList(XMLStreamReader reader)
      throws XMLStreamException {
    List<ConceptRelation> relations = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "ConceptRelation".equals(reader.getLocalName())) {
        ConceptRelation relation = parseConceptRelation(reader);
        if (relation != null) {
          relations.add(relation);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "ConceptRelationList".equals(reader.getLocalName())) {
        break;
      }
    }
    return relations;
  }

  /// 解析单个 ConceptRelation。
  private static ConceptRelation parseConceptRelation(XMLStreamReader reader)
      throws XMLStreamException {
    String relationName = null;
    String concept1UI = null;
    String concept2UI = null;

    // 解析 RelationName 属性
    relationName = reader.getAttributeValue(null, "RelationName");

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "Concept1UI":
            concept1UI = reader.getElementText();
            break;
          case "Concept2UI":
            concept2UI = reader.getElementText();
            break;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "ConceptRelation".equals(reader.getLocalName())) {
        break;
      }
    }

    if (relationName != null && concept1UI != null && concept2UI != null) {
      try {
        return ConceptRelation.of(relationName, MeshUI.of(concept1UI), MeshUI.of(concept2UI));
      } catch (Exception e) {
        log.warn(
            "解析 ConceptRelation 失败: RelationName={}, Concept1UI={}, Concept2UI={}",
            relationName,
            concept1UI,
            concept2UI,
            e);
      }
    }
    return null;
  }

  /// 解析 ThesaurusIDlist。
  private static List<String> parseThesaurusIdList(XMLStreamReader reader)
      throws XMLStreamException {
    List<String> thesaurusIds = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && "ThesaurusID".equals(reader.getLocalName())) {
        String thesaurusId = reader.getElementText();
        if (thesaurusId != null && !thesaurusId.trim().isEmpty()) {
          thesaurusIds.add(thesaurusId.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && "ThesaurusIDlist".equals(reader.getLocalName())) {
        break;
      }
    }
    return thesaurusIds;
  }
}
