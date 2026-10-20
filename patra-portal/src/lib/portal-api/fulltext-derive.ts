import { type MathNode, parseMathml } from "@/lib/mathml";
import type {
  FullTextBlock,
  FullTextBody,
  FullTextInline,
  FullTextLicense,
  FullTextSectionNode,
} from "@/types/fulltext";
import type { PaperDetail } from "@/types/portal";

/**
 * 详情页的全文状态（设计简报 §3.1、§3.5）：
 * - `full`：有正文可读
 * - `pdf-only`：没有可读正文，但能下载 PDF
 * - `pmc-only`：PMC 收录了，但没有开放许可或尚未开放，不能转载
 * - `not-oa`：不在 PMC 开放获取子集（也包括还没抓到、抓取出错）
 * - `retracted`：已撤稿，不展示全文
 */
export type FullTextState = "full" | "pdf-only" | "pmc-only" | "not-oa" | "retracted";

/** 按详情字段判断全文状态；撤稿优先（后端读端同口径下线全文）。 */
export function deriveFullTextState(paper: PaperDetail): FullTextState {
  if (paper.retracted) {
    return "retracted";
  }
  if (paper.hasFullText) {
    return "full";
  }
  if (paper.hasPdf) {
    return "pdf-only";
  }
  return paper.pmcid ? "pmc-only" : "not-oa";
}

/** 下载按钮旁的文件大小：十进制换算（与系统文件管理器一致），≥ 1 MB 保留一位小数，否则按 KB 取整。 */
export function formatFileSize(bytes: string | null): string | null {
  const n = bytes === null ? Number.NaN : Number(bytes);
  if (!Number.isFinite(n) || n < 0) {
    return null;
  }
  if (n >= 1_000_000) {
    return `${(n / 1_000_000).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(n / 1000))} KB`;
}

const BEIJING_DATE = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** 抓取日期：按北京时间取 `YYYY-MM-DD`（en-CA 的日期格式正好是这个形状）。 */
export function formatFetchedDate(iso: string | null): string | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : BEIJING_DATE.format(date);
}

/** 许可胶囊文字：代码 + 许可地址里的版本号（`CC BY 4.0`）；许可为空时「许可见原文」。 */
export function licenseLabel(license: FullTextLicense | null): string {
  if (!license) {
    return "许可见原文";
  }
  const version = license.url ? /\/(\d+\.\d+)\/?$/.exec(license.url)?.[1] : undefined;
  return version ? `${license.code} ${version}` : license.code;
}

/** 正文里元素的页内锚点（加前缀，避免与页面自己的 `fulltext`、`references` 等 id 撞车）。 */
export function bodyAnchor(id: string): string {
  return `ft-${id}`;
}

/** 参考文献条目的页内锚点。 */
export function referenceAnchor(id: string): string {
  return `ref-${id}`;
}

/** 正文图片（与只有图片的公式）的同源地址：门户 Route Handler 转发到 catalog（决策 F）。 */
export function figureSrc(publicationId: string, file: string): string {
  return `/api/papers/${publicationId}/figures/${encodeURIComponent(file)}`;
}

function mathText(nodes: MathNode[]): string {
  return nodes.map((n) => (n.kind === "text" ? n.value.trim() : mathText(n.children))).join("");
}

/** 行内节点的纯文本（目录、图片 alt 等只收字符串的位置用）：公式取其中的文字，换行不计。 */
export function inlineText(nodes: FullTextInline[]): string {
  return nodes
    .map((node) => {
      switch (node.kind) {
        case "text":
          return node.text;
        case "style":
        case "xref":
        case "link":
          return inlineText(node.children);
        case "math":
          return mathText(parseMathml(node.mathml));
        default:
          return "";
      }
    })
    .join("");
}

const SUPPLEMENTARY_TITLE =
  /^(electronic\s+)?(supplementary|supplemental|supporting)\s+(materials?|information|data|files?)$/i;

/** 补充材料一节：职责边界 E，整节不渲染、不进目录（后端已跳过附件本身，章节标题还在）。 */
export function isSupplementarySection(section: FullTextSectionNode): boolean {
  return SUPPLEMENTARY_TITLE.test(inlineText(section.title).trim());
}

export interface TocEntry {
  /** 目标元素的 DOM id */
  id: string;
  label: string;
  children: TocEntry[];
}

function titledSections(sections: FullTextSectionNode[]): FullTextSectionNode[] {
  return sections.filter((s) => s.title.length > 0 && !isSupplementarySection(s));
}

function tocEntry(section: FullTextSectionNode, children: TocEntry[]): TocEntry {
  return { id: bodyAnchor(section.id), label: inlineText(section.title).trim(), children };
}

/**
 * 目录：正文一级章节（带有标题的二级章节作为子项）+ 文末附加里有标题的章节。
 * 正文没有带标题的一级章节（述评类不分节）时返回空，页面不显示目录卡与目录按钮。
 */
export function deriveToc(body: FullTextBody): TocEntry[] {
  const top = titledSections(body.sections);
  if (top.length === 0) {
    return [];
  }
  return [
    ...top.map((s) =>
      tocEntry(
        s,
        titledSections(s.sections).map((c) => tocEntry(c, [])),
      ),
    ),
    ...titledSections(body.back).map((s) => tocEntry(s, [])),
  ];
}

export interface FullTextStats {
  figures: number;
  tables: number;
  references: number;
}

function countBlocks(blocks: FullTextBlock[], kind: "figure" | "table"): number {
  return blocks.reduce((sum, block) => {
    switch (block.kind) {
      case "figure":
      case "table":
        return sum + (block.kind === kind ? 1 : 0);
      case "list":
        return sum + block.items.reduce((n, item) => n + countBlocks(item, kind), 0);
      case "quote":
      case "box":
        return sum + countBlocks(block.blocks, kind);
      default:
        return sum;
    }
  }, 0);
}

function countInSections(sections: FullTextSectionNode[], kind: "figure" | "table"): number {
  return sections
    .filter((s) => !isSupplementarySection(s))
    .reduce((sum, s) => sum + countBlocks(s.blocks, kind) + countInSections(s.sections, kind), 0);
}

/** 区块头统计行的图、表、参考文献数（只数页面上会渲染的部分）。 */
export function deriveFullTextStats(body: FullTextBody): FullTextStats {
  const count = (kind: "figure" | "table") =>
    countBlocks(body.blocks, kind) +
    countInSections(body.sections, kind) +
    countInSections(body.back, kind);
  return { figures: count("figure"), tables: count("table"), references: body.references.length };
}
