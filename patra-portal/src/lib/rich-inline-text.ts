/** 内联标记节点：文本（实体已解码）或白名单元素（属性已剥）。 */
export type InlineNode =
  | { kind: "text"; value: string }
  | { kind: "element"; tag: string; children: InlineNode[] };

/** 常见命名实体 → 真字符。用 Map 避免对象原型链被外部输入命中。 */
const NAMED_ENTITIES = new Map<string, string>([
  ["amp", "&"],
  ["lt", "<"],
  ["gt", ">"],
  ["quot", '"'],
  ["apos", "'"],
  ["nbsp", "\u00a0"],
]);

/**
 * 解码常见 HTML 实体（命名 + 十进制 + 十六进制数字实体）。
 * 未知实体与孤立 & 原样保留。
 */
export function decodeEntities(text: string): string {
  return text.replace(/&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);/g, (match, body: string) => {
    if (body.startsWith("#")) {
      const isHex = body.charAt(1) === "x" || body.charAt(1) === "X";
      const code = Number.parseInt(body.slice(isHex ? 2 : 1), isHex ? 16 : 10);
      // 正则已保证至少一位数字，parseInt 不会产出 NaN；0、代理区（U+D800-U+DFFF）、越界码点原样保留。
      if (code === 0 || (code >= 0xd800 && code <= 0xdfff) || code > 0x10ffff) {
        return match;
      }
      return String.fromCodePoint(code);
    }
    return NAMED_ENTITIES.get(body) ?? match;
  });
}

type ElementNode = Extract<InlineNode, { kind: "element" }>;

/** 排版组白名单：任何位置放行。 */
const FORMATTING_TAGS = new Set(["i", "b", "sub", "sup", "u"]);

/** 公式组白名单：仅列只能出现在 <math> 子树内的标签（math 根标签在 isAllowed 单独放行）。 */
const MATH_TAGS = new Set([
  "mrow",
  "mi",
  "mo",
  "mn",
  "msub",
  "msup",
  "msubsup",
  "mtext",
  "mspace",
  "mstyle",
  "mover",
  "munder",
  "munderover",
  "mfrac",
  "msqrt",
  "semantics",
  "annotation",
]);

/** 标签形态：`<` 后紧跟可选 `/` 与标签名（`<`、`</` 后跟空白按 HTML 规范视为文本）、可选属性段（不含尖括号）、可选自闭合 `/`、`>`。不支持连字符标签名（如 annotation-xml，按字面降级；生产库实查无此标签）。 */
const TAG_RE = /^<(\/?)([a-zA-Z][a-zA-Z0-9]*)(?:\s[^<>]*)?\s*\/?\s*>/;

/** 解析深度上限：超过后的开标签按字面输出，防止畸形深嵌套压垮后处理与渲染递归。 */
const MAX_DEPTH = 64;

/**
 * 把带内联标记的文本解析为节点树（栈式单遍扫描）。
 *
 * 铁律：白名单外的一切（未知标签、裸 `<`、孤儿闭合标签）按字面文本输出，
 * 一个字符都不吞；未闭合标签自动闭合到段尾；属性全剥；标签名大小写不敏感。
 * 实体解码发生在文本片段层（flushText），晚于标签识别，解码产物不可能再被识别为标签。
 */
export function parseInlineMarkup(text: string): InlineNode[] {
  const rootChildren: InlineNode[] = [];
  const stack: ElementNode[] = [];
  let buf = "";
  let i = 0;

  const currentChildren = (): InlineNode[] => stack.at(-1)?.children ?? rootChildren;

  const flushText = (): void => {
    if (buf.length > 0) {
      currentChildren().push({ kind: "text", value: decodeEntities(buf) });
      buf = "";
    }
  };

  const inMath = (): boolean => stack.some((element) => element.tag === "math");

  const isAllowed = (tag: string): boolean => {
    if (FORMATTING_TAGS.has(tag)) {
      return true;
    }
    if (tag === "math") {
      return true;
    }
    return MATH_TAGS.has(tag) && inMath();
  };

  while (i < text.length) {
    const ch = text.charAt(i);
    if (ch !== "<") {
      buf += ch;
      i += 1;
      continue;
    }
    const m = TAG_RE.exec(text.slice(i));
    if (m === null) {
      // 裸 `<`（接不出合法标签形态）→ 字面
      buf += ch;
      i += 1;
      continue;
    }
    const isClosing = m[1] === "/";
    const tag = (m[2] ?? "").toLowerCase();
    if (isClosing) {
      let openIdx = -1;
      for (let s = stack.length - 1; s >= 0; s -= 1) {
        if (stack[s]?.tag === tag) {
          openIdx = s;
          break;
        }
      }
      if (openIdx === -1) {
        // 孤儿闭合标签 → 字面（`<` 进 buf，其余字符随后续扫描继续进 buf）
        buf += ch;
        i += 1;
        continue;
      }
      flushText();
      // 弹栈到匹配层：中间未闭合的标签自动闭合
      stack.length = openIdx;
      i += m[0].length;
      continue;
    }
    if (!isAllowed(tag)) {
      // 白名单外标签 → 字面
      buf += ch;
      i += 1;
      continue;
    }
    if (stack.length >= MAX_DEPTH) {
      // 超深开标签 → 字面
      buf += ch;
      i += 1;
      continue;
    }
    flushText();
    const element: ElementNode = { kind: "element", tag, children: [] };
    currentChildren().push(element);
    // 自闭合（如 <mspace/>）不进栈
    if (!/\/\s*>$/.test(m[0])) {
      stack.push(element);
    }
    i += m[0].length;
  }
  flushText();
  // 段尾仍在栈中的元素 = 未被显式/祖先闭合；未闭合的 annotation 只展开不删除
  const unclosed = new Set<InlineNode>(stack);
  return stripAnnotations(rootChildren, unclosed);
}

/**
 * 处理 annotation 元素——它是同一公式的 LaTeX 重复表述：
 * 已闭合的整体丢弃（渲染会使公式显示两遍）；
 * 未闭合的只展开其子节点（上游截断时不吞正文，一个字符都不丢）。
 */
function stripAnnotations(nodes: InlineNode[], unclosed: ReadonlySet<InlineNode>): InlineNode[] {
  const out: InlineNode[] = [];
  for (const node of nodes) {
    if (node.kind === "element") {
      const children = stripAnnotations(node.children, unclosed);
      if (node.tag === "annotation") {
        if (unclosed.has(node)) {
          out.push(...children);
        }
        continue;
      }
      out.push({ ...node, children });
    } else {
      out.push(node);
    }
  }
  return out;
}

/** 节点树的可见纯文本长度（与 highlightInlineNodes 的遍历顺序一致）。 */
function textLength(nodes: InlineNode[]): number {
  return nodes.reduce(
    (sum, node) => sum + (node.kind === "text" ? node.value.length : textLength(node.children)),
    0,
  );
}

function collectText(nodes: InlineNode[]): string {
  return nodes
    .map((node) => (node.kind === "text" ? node.value : collectText(node.children)))
    .join("");
}

/** 把一个文本节点按命中区间切成 文本 / mark 交替片段。start 为该节点在全文中的起始偏移。 */
function splitByRanges(
  value: string,
  start: number,
  ranges: readonly [number, number][],
): InlineNode[] {
  const out: InlineNode[] = [];
  let cursor = 0;
  for (const [rangeStart, rangeEnd] of ranges) {
    const s = Math.max(rangeStart - start, cursor);
    const e = Math.min(rangeEnd - start, value.length);
    if (s >= e) continue;
    if (s > cursor) out.push({ kind: "text", value: value.slice(cursor, s) });
    out.push({
      kind: "element",
      tag: "mark",
      children: [{ kind: "text", value: value.slice(s, e) }],
    });
    cursor = e;
  }
  if (cursor < value.length) out.push({ kind: "text", value: value.slice(cursor) });
  return out;
}

/**
 * 在节点树上高亮检索词：大小写不敏感、按字面匹配，命中片段包成 `mark` 元素节点。
 * 命中跨越标签时拆成多段 mark，不改动原有标签结构；`math` 子树不插 mark（避免破坏 MathML），
 * 但其文本仍计入偏移。q 为空白时原样返回。
 */
export function highlightInlineNodes(nodes: InlineNode[], query: string): InlineNode[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return nodes;
  const full = collectText(nodes);
  const haystack = full.toLowerCase();
  // 个别字符小写后长度变化（如 İ）时偏移不可靠，放弃高亮
  if (haystack.length !== full.length) return nodes;

  const ranges: [number, number][] = [];
  for (
    let from = haystack.indexOf(needle);
    from !== -1;
    from = haystack.indexOf(needle, from + needle.length)
  ) {
    ranges.push([from, from + needle.length]);
  }
  if (ranges.length === 0) return nodes;

  let offset = 0;
  const walk = (list: InlineNode[]): InlineNode[] =>
    list.flatMap((node): InlineNode[] => {
      if (node.kind === "element") {
        if (node.tag === "math") {
          offset += textLength(node.children);
          return [node];
        }
        return [{ ...node, children: walk(node.children) }];
      }
      const start = offset;
      offset += node.value.length;
      return splitByRanges(node.value, start, ranges);
    });
  return walk(nodes);
}
