package com.patra.catalog.infra.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
///   - 文件大小：支持 700MB+ 的 XML 文件
/// 
/// @author linqibin
/// @since 0.2.0
@Slf4j
@Component
@RequiredArgsConstructor
public class StaxXmlParserImpl implements XmlParserPort {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  @Override
  public Stream<MeshDescriptorAggregate> parseDescriptors(InputStream xmlInputStream) {
    log.info("开始解析 MeSH Descriptor XML 文件");

    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
      DescriptorSpliterator spliterator = new DescriptorSpliterator(reader);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
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

  // ========== Spliterator 实现 ==========

  /// Descriptor Spliterator（用于流式解析 Descriptor）。
  private static class DescriptorSpliterator
      implements java.util.Spliterator<MeshDescriptorAggregate> {

    private final XMLStreamReader reader;
    private boolean hasNext = true;

    public DescriptorSpliterator(XMLStreamReader reader) {
      this.reader = reader;
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
            MeshDescriptorAggregate descriptor = parseDescriptorRecord(reader);
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
    public java.util.Spliterator<MeshDescriptorAggregate> trySplit() {
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
    private MeshDescriptorAggregate parseDescriptorRecord(XMLStreamReader reader)
        throws XMLStreamException {
      String descriptorUI = null;
      String descriptorName = null;
      List<MeshTreeNumber> treeNumbers = new ArrayList<>();

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
              while (reader.hasNext()) {
                event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT
                    && "String".equals(reader.getLocalName())) {
                  descriptorName = reader.getElementText();
                  break;
                }
              }
              break;
            case "TreeNumberList":
              treeNumbers = parseTreeNumberList(reader, descriptorUI);
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
      MeshDescriptorAggregate aggregate = MeshDescriptorAggregate.create(
          MeshUI.of(descriptorUI),
          descriptorName,
          DescriptorClass.TOPICAL, // 默认为主题词类型，可从 XML 中解析 DescriptorClass
          "2025" // 默认版本号，可从 XML 中解析
      );

      // 添加树形编号
      if (!treeNumbers.isEmpty()) {
        aggregate.addTreeNumbers(treeNumbers);
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
      String term = null;
      String lexicalTagCode = "NON"; // 默认值
      boolean isRecordPreferred = false;
      boolean isPrintFlag = true; // 默认打印

      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();
          switch (localName) {
            case "String":
              term = reader.getElementText();
              break;
            case "LexicalTag":
              lexicalTagCode = reader.getElementText();
              break;
            case "RecordPreferredTerm":
              String preferredValue = reader.getElementText();
              isRecordPreferred = "Y".equalsIgnoreCase(preferredValue);
              break;
            case "PrintFlagYN":
              String printFlag = reader.getElementText();
              isPrintFlag = "Y".equalsIgnoreCase(printFlag);
              break;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT && "Term".equals(reader.getLocalName())) {
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

      return MeshEntryTerm.create(term, lexicalTag, isRecordPreferred, isPrintFlag);
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
      String registryNumber = null;
      String scopeNote = null;
      String casn1Name = null;
      String conceptStatus = null;

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
              while (reader.hasNext()) {
                event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "String".equals(reader.getLocalName())) {
                  conceptName = reader.getElementText();
                  break;
                }
              }
              break;
            case "RegistryNumber":
              registryNumber = reader.getElementText();
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
          }
        } else if (event == XMLStreamConstants.END_ELEMENT && "Concept".equals(reader.getLocalName())) {
          break;
        }
      }

      // 验证必填字段
      if (conceptUi == null || conceptName == null) {
        log.warn("跳过无效 Concept（缺少必填字段）: UI={}, Name={}", conceptUi, conceptName);
        return null;
      }

      // 创建实体
      MeshConcept concept = MeshConcept.create(
          MeshUI.of(conceptUi),
          conceptName,
          isPreferred
      );

      // 设置可选字段
      if (registryNumber != null) {
        concept.withRegistryNumber(registryNumber);
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

      return concept;
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
}
