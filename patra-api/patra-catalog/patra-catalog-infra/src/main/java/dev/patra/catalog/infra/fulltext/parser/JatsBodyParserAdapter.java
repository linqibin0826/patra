package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.model.vo.body.Reference;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/// JATS XML 解析器（JDK DOM）。
///
/// 只取 `<article>` 的直接子元素 `<body>` / `<back>`（`<sub-article>` 里的审稿意见等不取）。
/// JATS 文件普遍带 DOCTYPE，所以不禁用 DOCTYPE，而是关闭外部 DTD 加载与外部实体，
/// 解析 DOCTYPE 但不下载 DTD。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
public class JatsBodyParserAdapter implements JatsBodyParserPort {

  /// 会出现在结果里、可以被交叉引用的元素（`disp-formula` 另判：只有 TeX 时输出为不带 id 的代码块）。
  private static final Set<String> TARGETS =
      Set.of("sec", "fig", "table-wrap", "boxed-text", "ref", "app");

  /// 解析一篇文章。
  ///
  /// @param xml JATS XML 输入流
  /// @param media 图片清单
  /// @return 解析结果
  @Override
  public JatsParseResult parse(InputStream xml, List<FullTextMedia> media) {
    Document doc = read(xml);
    Element article = doc.getDocumentElement();
    Optional<Element> body = JatsDom.child(article, "body");
    if (body.isEmpty() || JatsDom.text(body.get()).isEmpty()) {
      return JatsParseResult.ofNoBody();
    }
    Optional<Element> back = JatsDom.child(article, "back");

    JatsParseContext ctx = new JatsParseContext(media);
    registerTargets(body.get(), ctx, false);
    back.ifPresent(b -> registerTargets(b, ctx, true));
    JatsMathReader math = new JatsMathReader(ctx);
    JatsInlineReader inline = new JatsInlineReader(ctx, math);
    JatsBlockReader blocks = new JatsBlockReader(ctx, inline, math);

    List<Block> topBlocks = new ArrayList<>();
    List<Section> sections = new ArrayList<>();
    readBody(body.get(), ctx, blocks, topBlocks, sections);
    List<Section> backSections = new ArrayList<>();
    List<Reference> references = new ArrayList<>();
    back.ifPresent(
        b -> readBack(b, ctx, blocks, new JatsReferenceReader(inline), backSections, references));

    FullTextBody result =
        FullTextBody.builder()
            .schemaVersion(FullTextBody.SCHEMA_VERSION)
            .blocks(topBlocks)
            .sections(sections)
            .back(backSections)
            .references(references)
            .wordCount(JatsWordCounter.count(topBlocks, sections, backSections))
            .build();
    if (ctx.warningCount() > 0) {
      log.debug("JATS 解析警告 {} 条", ctx.warningCount());
    }
    return JatsParseResult.ofBody(result, ctx.warningCount());
  }

  /// `<body>`：第一个 `<sec>` 之前的直接内容进顶层块；之后又出现的直接内容归入无标题章节。
  private static void readBody(
      Element body,
      JatsParseContext ctx,
      JatsBlockReader reader,
      List<Block> topBlocks,
      List<Section> sections) {
    List<Block> trailing = new ArrayList<>();
    for (Element child : JatsDom.children(body)) {
      if ("sec".equals(JatsDom.name(child))) {
        flushTrailing(ctx, trailing, sections);
        reader.readSection(child).ifPresent(sections::add);
      } else if (sections.isEmpty()) {
        reader.readBlock(child, topBlocks);
      } else {
        reader.readBlock(child, trailing);
      }
    }
    flushTrailing(ctx, trailing, sections);
  }

  /// `<back>`：致谢、脚注组、附录、注释与 `<sec>` 进 back 章节，`ref-list` 进参考文献，其余按未知块处理。
  private static void readBack(
      Element back,
      JatsParseContext ctx,
      JatsBlockReader reader,
      JatsReferenceReader referenceReader,
      List<Section> backSections,
      List<Reference> references) {
    List<Block> unknown = new ArrayList<>();
    for (Element child : JatsDom.children(back)) {
      switch (JatsDom.name(child)) {
        case "ack", "notes", "sec", "app" -> reader.readSection(child).ifPresent(backSections::add);
        case "app-group" -> {
          for (Element app : JatsDom.children(child)) {
            if ("app".equals(JatsDom.name(app))) {
              reader.readSection(app).ifPresent(backSections::add);
            }
          }
        }
        case "fn-group" -> reader.readFootnoteGroup(child).ifPresent(backSections::add);
        case "ref-list" -> referenceReader.read(child, references);
        case "title", "label" -> {
          // back 本身的标题不输出
        }
        default -> reader.readBlock(child, unknown);
      }
    }
    flushTrailing(ctx, unknown, backSections);
  }

  /// 把积攒的直接内容收成一个无标题章节。
  private static void flushTrailing(
      JatsParseContext ctx, List<Block> trailing, List<Section> sections) {
    if (!trailing.isEmpty()) {
      sections.add(
          Section.of(ctx.idOrGenerate(null, "sec"), List.of(), List.of(), List.copyOf(trailing)));
      trailing.clear();
    }
  }

  /// 预先登记会出现在结果里的目标 id，让正文里的前向引用也能识别。
  private static void registerTargets(Element root, JatsParseContext ctx, boolean inBack) {
    for (Element child : JatsDom.children(root)) {
      String name = JatsDom.name(child);
      String type = JatsDom.attr(child, "sec-type");
      if ("sec".equals(name) && type != null && type.contains("supplementary")) {
        continue;
      }
      if ("supplementary-material".equals(name) || "sub-article".equals(name)) {
        continue;
      }
      boolean target =
          "disp-formula".equals(name)
              ? JatsMathReader.rendersAsFormula(child)
              : TARGETS.contains(name) || (inBack && "fn".equals(name) && !insideTableFoot(child));
      if (target) {
        ctx.registerTarget(JatsDom.attr(child, "id"));
      }
      registerTargets(child, ctx, inBack);
    }
  }

  /// 是否表注里的脚注（表注不带 id 输出）。
  private static boolean insideTableFoot(Element fn) {
    for (Node p = fn.getParentNode(); p != null; p = p.getParentNode()) {
      if ("table-wrap-foot".equals(JatsDom.name(p))) {
        return true;
      }
    }
    return false;
  }

  /// 安全地读入 DOM。
  private static Document read(InputStream xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
      builder.setErrorHandler(STRICT);
      return builder.parse(xml);
    } catch (SAXException | IOException e) {
      throw new XmlParseException("JATS XML 不合法：" + e.getMessage(), e);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("JDK DOM 解析器配置失败", e);
    }
  }

  /// 致命错误与错误都抛出，警告忽略（不往标准错误打印）。
  private static final ErrorHandler STRICT =
      new ErrorHandler() {
        /// 警告忽略。
        ///
        /// @param e 警告
        @Override
        public void warning(SAXParseException e) {}

        /// 错误直接抛出。
        ///
        /// @param e 错误
        /// @throws SAXException 原错误
        @Override
        public void error(SAXParseException e) throws SAXException {
          throw e;
        }

        /// 致命错误直接抛出。
        ///
        /// @param e 致命错误
        /// @throws SAXException 原错误
        @Override
        public void fatalError(SAXParseException e) throws SAXException {
          throw e;
        }
      };
}
