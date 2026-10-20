import { type InlineNode, parseInlineMarkup } from "@/lib/rich-inline-text";

/** 详情页标题的拆分结果（spec §5.1）：节点树按白名单解析，渲染时不再二次解析。 */
export interface TitleParts {
  /** 主标题 */
  main: InlineNode[];
  /** 冒号后的副标题；没有拆开时为 null */
  sub: InlineNode[] | null;
  /** 字级：`md` 只给没拆开、且纯文本超过 120 字符的超长标题 */
  size: "lg" | "md";
}

const LONG_TITLE = 120;

/** 节点树的纯文本（量长度、找冒号前的词、面包屑短标题用）。 */
export function inlinePlainText(nodes: InlineNode[]): string {
  return nodes
    .map((node) => (node.kind === "text" ? node.value : inlinePlainText(node.children)))
    .join("");
}

/** 去掉末尾的一个英文句点（PubMed 题名惯例）：只看最后一个顶层文本节点，`...` 与 `?`、`!` 保留。 */
function trimTrailingPeriod(nodes: InlineNode[]): InlineNode[] {
  const last = nodes.at(-1);
  if (last?.kind !== "text") return nodes;
  const value = last.value.trimEnd().replace(/(?<!\.)\.$/, "");
  return [...nodes.slice(0, -1), ...(value ? [{ kind: "text" as const, value }] : [])];
}

/** 第一个顶层文本节点里可以拆开的冒号：半角冒号后要跟空白，全角冒号不要求。富文本元素里的冒号不算。 */
function findSplitColon(nodes: InlineNode[]): { node: number; index: number } | null {
  for (let n = 0; n < nodes.length; n += 1) {
    const node = nodes[n];
    if (node?.kind !== "text") continue;
    const match = /：|:(?=\s)/.exec(node.value);
    if (match) return { node: n, index: match.index };
  }
  return null;
}

/**
 * 标题拆分：去掉末尾的一个英文句点；在第一个顶层的「冒号 + 空白」（或全角冒号）处拆成主标题与副标题。
 * 不拆的情况：冒号前只有一个西文单词（`Re:`、`Editorial:`）；整句被方括号包住（PubMed 的译名标题）。
 * 没拆开且纯文本超过 120 字符时降到 `md` 档。字面的 `<`、`>` 由白名单解析照原文保留。
 */
export function splitTitle(title: string): TitleParts {
  const nodes = trimTrailingPeriod(parseInlineMarkup(title.trim()));
  const whole = inlinePlainText(nodes).trim();
  const colon = /^\[.*\]$/.test(whole) ? null : findSplitColon(nodes);
  if (colon) {
    const target = nodes[colon.node] as Extract<InlineNode, { kind: "text" }>;
    const before = target.value.slice(0, colon.index).trimEnd();
    const after = target.value.slice(colon.index + 1).trimStart();
    const main: InlineNode[] = [
      ...nodes.slice(0, colon.node),
      ...(before ? [{ kind: "text" as const, value: before }] : []),
    ];
    const sub: InlineNode[] = [
      ...(after ? [{ kind: "text" as const, value: after }] : []),
      ...nodes.slice(colon.node + 1),
    ];
    const mainPlain = inlinePlainText(main).trim();
    if (mainPlain && inlinePlainText(sub).trim() && !/^[A-Za-z]+$/.test(mainPlain)) {
      return { main, sub, size: "lg" };
    }
  }
  return { main: nodes, sub: null, size: whole.length > LONG_TITLE ? "md" : "lg" };
}

/** 面包屑里的短标题：拆分后主标题的纯文本。 */
export function shortTitle(title: string): string {
  return inlinePlainText(splitTitle(title).main).trim();
}
