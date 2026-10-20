import { type MathNode, parseMathml } from "@/lib/mathml";
import type {
  FullTextBlock,
  FullTextBody,
  FullTextInline,
  FullTextLicense,
  FullTextSectionNode,
  FullTextTableCell,
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
  /** 标签栏与目录里的序号（`01`、`4.5`） */
  number: string | null;
  label: string;
  children: TocEntry[];
}

function titledSections(sections: FullTextSectionNode[]): FullTextSectionNode[] {
  return sections.filter((s) => s.title.length > 0 && !isSupplementarySection(s));
}

/** 章节在标签栏里的序号，以及去掉原文序号后的标题。 */
export interface SectionHeading {
  label: string;
  title: FullTextInline[];
}

const NUMBER_PREFIX = /^(\d+(?:\.\d+)*)\.?\s+/;

/** 标题开头的原文序号（`1.`、`2.1`、`2.1.`），只看第一个文本节点。 */
function leadingNumber(title: FullTextInline[]): { number: string; rest: FullTextInline[] } | null {
  const [first, ...others] = title;
  if (first?.kind !== "text") return null;
  const match = NUMBER_PREFIX.exec(first.text);
  if (!match) return null;
  const remaining = first.text.slice(match[0].length);
  return {
    number: match[1] ?? "",
    rest: remaining ? [{ kind: "text", text: remaining }, ...others] : others,
  };
}

/** 一级序号补成两位（`1` → `01`）。 */
function padTop(number: string): string {
  return number.includes(".") ? number : number.padStart(2, "0");
}

/**
 * 正文一、二级章节的序号（spec §5.6）：这些章节全都以序号开头时用原文序号并从标题里拿掉，
 * 否则全部按位置编号、标题不动。无标题章节与补充材料不编号、不占序号；三级及以下不编。
 */
export function numberSections(sections: FullTextSectionNode[]): Record<string, SectionHeading> {
  const top = titledSections(sections);
  const levels = top.flatMap((s) => [s, ...titledSections(s.sections)]);
  const useOriginal = levels.length > 0 && levels.every((s) => leadingNumber(s.title) !== null);
  const heading = (s: FullTextSectionNode, positional: string): SectionHeading => {
    const own = useOriginal ? leadingNumber(s.title) : null;
    return own ? { label: own.number, title: own.rest } : { label: positional, title: s.title };
  };
  const out: Record<string, SectionHeading> = {};
  top.forEach((s, i) => {
    const h = heading(s, String(i + 1));
    out[s.id] = { ...h, label: padTop(h.label) };
    titledSections(s.sections).forEach((c, j) => {
      out[c.id] = heading(c, `${i + 1}.${j + 1}`);
    });
  });
  return out;
}

function tocEntry(
  section: FullTextSectionNode,
  numbering: Record<string, SectionHeading>,
  children: TocEntry[],
): TocEntry {
  const heading = numbering[section.id];
  return {
    id: bodyAnchor(section.id),
    number: heading?.label ?? null,
    label: inlineText(heading?.title ?? section.title).trim(),
    children,
  };
}

/**
 * 目录：正文一级章节（带有标题的二级章节作为子项），带序号；文末附加不进目录，由目录尾项「声明与附注」代表。
 * 正文没有带标题的一级章节（述评类不分节）时返回空，页面不显示目录卡与目录按钮。
 */
export function deriveToc(body: FullTextBody): TocEntry[] {
  const top = titledSections(body.sections);
  if (top.length === 0) {
    return [];
  }
  const numbering = numberSections(body.sections);
  return top.map((s) =>
    tocEntry(
      s,
      numbering,
      titledSections(s.sections).map((c) => tocEntry(c, numbering, [])),
    ),
  );
}

/**
 * 目录尾项的计数：「声明与附注 N 项」（有标题的文末节，含附录）与参考文献条数。
 * 没有可进声明区的节（只有附录或只有无标题节）时页面上没有 `#declarations`，计为 0、不列。
 */
export function deriveTocTail(body: FullTextBody): { declarations: number; references: number } {
  const back = splitBack(body.back);
  return {
    declarations:
      back.declarations.length > 0 ? back.declarations.length + back.appendices.length : 0,
    references: body.references.length,
  };
}

const CITATION_GLUE = /^[\s,;–—−-]*$/;

/** 是否是只装着参考文献引用的上标（`<sup>8</sup>`、`<sup>3–5, 9</sup>`）：它前面的空白要去掉。 */
export function isCitationSup(node: FullTextInline): boolean {
  if (node.kind !== "style" || node.style !== "sup") return false;
  let cites = 0;
  for (const child of node.children) {
    if (child.kind === "xref" && child.refType === "bibr") cites += 1;
    else if (!(child.kind === "text" && CITATION_GLUE.test(child.text))) return false;
  }
  return cites > 0;
}

function hasFigureOrTable(section: FullTextSectionNode): boolean {
  return (
    countBlocks(section.blocks, "figure") +
      countBlocks(section.blocks, "table") +
      countInSections(section.sections, "figure") +
      countInSections(section.sections, "table") >
    0
  );
}

/** 文末附加的分组（spec §5.11）：有标题的进「声明与附注」，无标题的接在后面，含图表的按附录单独渲染。 */
export function splitBack(back: FullTextSectionNode[]): {
  declarations: FullTextSectionNode[];
  untitled: FullTextSectionNode[];
  appendices: FullTextSectionNode[];
} {
  const kept = back.filter((s) => !isSupplementarySection(s));
  return {
    declarations: kept.filter((s) => s.title.length > 0 && !hasFigureOrTable(s)),
    untitled: kept.filter((s) => s.title.length === 0),
    appendices: kept.filter((s) => s.title.length > 0 && hasFigureOrTable(s)),
  };
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

/** 插图的宽度分类（spec §5.8）：竖图 / 正文宽 / 加宽 / 加宽并常显「放大查看」。 */
export type FigureLayout = "text" | "wide" | "wide-zoom" | "portrait";

/** 按宽高比选插图宽度：< 0.8 竖图，[0.8, 1.5) 正文宽，[1.5, 2.4] 加宽，> 2.4 超宽；宽高缺失按正文宽。 */
export function figureLayout(width: number | null, height: number | null): FigureLayout {
  if (!width || !height) {
    return "text";
  }
  const ratio = width / height;
  if (ratio < 0.8) return "portrait";
  if (ratio < 1.5) return "text";
  if (ratio <= 2.4) return "wide";
  return "wide-zoom";
}

/** 图、表标签放进标签栏时去掉原文末尾的句点（`Figure 1.` → `Figure 1`），其余照原文。 */
export function figureLabel(label: string | null): string | null {
  return label === null ? null : label.replace(/\.$/, "");
}

/** 数字格：数、百分数、`—`、均值 ± 标准差、带括号区间的比值。 */
const NUMERIC_CELL = /^[−–-]?\d[\d.,]*\s?%?(\s?±\s?\d[\d.,]*%?)?(\s?\(.*\))?$|^[—–-]$/;

/**
 * 每个单元格的起始列位：同一行按 colspan 累加，并跳过上方行的 rowspan 还占着的列
 * （浏览器的表格布局就是这样排的）。`numericColumns` 与 `TableBlock` 共用。
 */
export function resolveColumns(rows: { colspan: number; rowspan: number }[][]): number[][] {
  let pending: number[] = [];
  return rows.map((row) => {
    const occupied = pending.map((n) => n > 0);
    const next = pending.map((n) => Math.max(n - 1, 0));
    let col = 0;
    const starts = row.map((cell) => {
      while (occupied[col]) col += 1;
      const start = col;
      for (let c = start; c < start + cell.colspan; c += 1) {
        if (cell.rowspan > 1) next[c] = Math.max(next[c] ?? 0, cell.rowspan - 1);
      }
      col += cell.colspan;
      return start;
    });
    pending = next;
    return starts;
  });
}

/**
 * 数据表哪些列右对齐：一列里（不含表头）超过一半的单元格是数字格就算数字列。
 * 跨列的单元格不参与判定；列位按 `resolveColumns`（含 rowspan 占位）计算。
 */
export function numericColumns(
  head: FullTextTableCell[][],
  body: FullTextTableCell[][],
): boolean[] {
  const columns = resolveColumns(body);
  const width = Math.max(
    0,
    ...head.map((row) => row.reduce((n, c) => n + c.colspan, 0)),
    ...columns.flatMap((starts, r) => starts.map((s, i) => s + (body[r]?.[i]?.colspan ?? 1))),
  );
  const numeric = Array.from({ length: width }, () => 0);
  const total = Array.from({ length: width }, () => 0);
  body.forEach((row, r) => {
    row.forEach((c, i) => {
      if (c.colspan !== 1) return;
      const col = columns[r]?.[i] ?? 0;
      total[col] = (total[col] ?? 0) + 1;
      if (NUMERIC_CELL.test(inlineText(c.content).trim())) numeric[col] = (numeric[col] ?? 0) + 1;
    });
  });
  return total.map((t, i) => t > 0 && (numeric[i] ?? 0) * 2 > t);
}
