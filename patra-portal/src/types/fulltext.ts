/**
 * 全文接口 `GET /portal/publications/{id}/fulltext` 的响应与正文结构。
 * 形状以后端工程 spec §6.2（正文）与 §7.3（接口）为准；运行时由 `fullTextSchema` 校验。
 */

/** 行内样式（JATS 的 italic / bold / sup / sub / underline / sc / monospace）。 */
export type FullTextInlineStyle =
  | "italic"
  | "bold"
  | "sup"
  | "sub"
  | "underline"
  | "smallcaps"
  | "monospace";

/** 交叉引用类型：参考文献、图、表、行间公式、章节、脚注、文本框。 */
export type FullTextXrefType =
  | "bibr"
  | "fig"
  | "table"
  | "disp-formula"
  | "sec"
  | "fn"
  | "boxed-text";

export type FullTextInline =
  | { kind: "text"; text: string }
  | { kind: "style"; style: FullTextInlineStyle; children: FullTextInline[] }
  | { kind: "xref"; refType: FullTextXrefType; targets: string[]; children: FullTextInline[] }
  | { kind: "link"; href: string; children: FullTextInline[] }
  | { kind: "math"; mathml: string }
  | { kind: "break" };

export interface FullTextTableCell {
  content: FullTextInline[];
  colspan: number;
  rowspan: number;
  header: boolean;
}

export type FullTextBlock =
  | { kind: "paragraph"; content: FullTextInline[] }
  | {
      kind: "figure";
      id: string | null;
      label: string | null;
      caption: FullTextBlock[];
      /** 图片文件名，经 `/api/papers/{id}/figures/{file}` 取图；匹配不到文件时为空 */
      image: string | null;
      width: number | null;
      height: number | null;
    }
  | {
      kind: "table";
      id: string | null;
      label: string | null;
      caption: FullTextBlock[];
      head: FullTextTableCell[][];
      body: FullTextTableCell[][];
      footnotes: FullTextBlock[];
    }
  | {
      kind: "formula";
      id: string | null;
      label: string | null;
      /** MathML 字符串（已去命名空间前缀）；只有公式图片时为空，改用 image */
      mathml: string | null;
      image: string | null;
      width: number | null;
      height: number | null;
    }
  | { kind: "list"; ordered: boolean; items: FullTextBlock[][] }
  | { kind: "quote"; blocks: FullTextBlock[] }
  | { kind: "box"; id: string | null; title: FullTextInline[]; blocks: FullTextBlock[] }
  | { kind: "code"; text: string };

export interface FullTextSectionNode {
  id: string;
  /** 空数组 = 无标题章节（不渲染标题、不进目录） */
  title: FullTextInline[];
  sections: FullTextSectionNode[];
  blocks: FullTextBlock[];
}

export interface FullTextReference {
  id: string | null;
  label: string | null;
  content: FullTextInline[];
  pmid: string | null;
  doi: string | null;
}

export interface FullTextBody {
  schemaVersion: number;
  /** `<body>` 里第一个章节之前的内容 */
  blocks: FullTextBlock[];
  sections: FullTextSectionNode[];
  /** 致谢、脚注、附录等文末附加 */
  back: FullTextSectionNode[];
  references: FullTextReference[];
  wordCount: number;
}

export interface FullTextLicense {
  /** 如 `CC BY`、`CC BY-NC-ND` */
  code: string;
  url: string | null;
}

export interface FullText {
  publicationId: string;
  pmcid: string;
  pmcUrl: string;
  /** 许可为空或不认识时为 null，页面写「许可见原文」 */
  license: FullTextLicense | null;
  /** 如 `数据来源：美国国家医学图书馆（NLM）PubMed Central` */
  source: string;
  fetchedAt: string | null;
  wordCount: number | null;
  estimatedReadMin: number | null;
  isManuscript: boolean | null;
  /** 只有 PDF、没有正文时为 null */
  body: FullTextBody | null;
}
