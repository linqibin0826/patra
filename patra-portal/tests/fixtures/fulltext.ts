import type {
  FullText,
  FullTextBlock,
  FullTextBody,
  FullTextInline,
  FullTextReference,
  FullTextSectionNode,
} from "@/types/fulltext";

/** 纯文本行内节点。 */
export function text(value: string): FullTextInline {
  return { kind: "text", text: value };
}

/** 只含纯文本的段落。 */
export function para(...content: (string | FullTextInline)[]): FullTextBlock {
  return {
    kind: "paragraph",
    content: content.map((c) => (typeof c === "string" ? text(c) : c)),
  };
}

/** 章节：标题为纯文本，空字符串表示无标题章节。 */
export function section(
  id: string,
  title: string,
  blocks: FullTextBlock[] = [],
  sections: FullTextSectionNode[] = [],
): FullTextSectionNode {
  return { id, title: title ? [text(title)] : [], blocks, sections };
}

/** 参考文献条目。 */
export function reference(id: string, label: string, overrides: Partial<FullTextReference> = {}) {
  return {
    id,
    label,
    content: [text(`Author ${label}. Title ${label}. Journal. 2020;1:1-2.`)],
    pmid: null,
    doi: null,
    ...overrides,
  } satisfies FullTextReference;
}

/** 正文结构：默认两节、两条参考文献，形状同 mini 上的真实响应（spec §6.2）。 */
export function makeBody(overrides: Partial<FullTextBody> = {}): FullTextBody {
  return {
    schemaVersion: 1,
    blocks: [],
    sections: [
      section("s1", "Introduction", [para("Intro text.")]),
      section("s2", "Methods", [para("Methods text.")]),
    ],
    back: [],
    references: [reference("r1", "1"), reference("r2", "2")],
    wordCount: 5701,
    ...overrides,
  };
}

/** 全文接口响应：默认是 PMC13583816 那样的有全文、CC BY。 */
export function makeFullText(overrides: Partial<FullText> = {}): FullText {
  return {
    publicationId: "352303012816019646",
    pmcid: "PMC13583816",
    pmcUrl: "https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/",
    license: { code: "CC BY", url: "https://creativecommons.org/licenses/by/4.0/" },
    source: "数据来源：美国国家医学图书馆（NLM）PubMed Central",
    fetchedAt: "2026-10-18T05:12:00Z",
    wordCount: 5701,
    estimatedReadMin: 25,
    isManuscript: false,
    body: makeBody(),
    ...overrides,
  };
}
