import { MATH_ELEMENT_TAGS } from "@/lib/mathml-tags";
import { decodeEntities } from "@/lib/rich-inline-text";

export { MATH_ELEMENT_TAGS };

/** MathML 节点：文本（实体已解码）或白名单元素（只留排版属性）。 */
export type MathNode =
  | { kind: "text"; value: string }
  | { kind: "element"; tag: string; attrs: Record<string, string>; children: MathNode[] };

type MathElement = Extract<MathNode, { kind: "element" }>;

/**
 * 属性白名单：只留影响排版的属性。事件、样式、类名、id、链接一律去掉；
 * 这些属性的值不是地址也不是脚本，React 会转义，不另做值过滤。
 */
const ALLOWED_ATTRS = new Set([
  "accent",
  "accentunder",
  "align",
  "bevelled",
  "close",
  "columnalign",
  "columnlines",
  "columnspacing",
  "columnspan",
  "denomalign",
  "depth",
  "dir",
  "display",
  "displaystyle",
  "fence",
  "form",
  "frame",
  "framespacing",
  "height",
  "largeop",
  "linebreak",
  "linethickness",
  "lspace",
  "mathsize",
  "mathvariant",
  "maxsize",
  "minsize",
  "movablelimits",
  "notation",
  "numalign",
  "open",
  "rowalign",
  "rowlines",
  "rowspacing",
  "rowspan",
  "rspace",
  "scriptlevel",
  "separator",
  "separators",
  "stretchy",
  "subscriptshift",
  "superscriptshift",
  "symmetric",
  "voffset",
  "width",
]);

/** 整个子树丢弃的元素：同一公式的 TeX / 其他编码重复表述，渲染出来会显示两遍。 */
const DROPPED_SUBTREES = new Set(["annotation", "annotation-xml"]);

/** 开 / 闭 / 自闭合标签（含连字符名与带命名空间的属性名）。 */
const TAG_RE =
  /^<(\/?)([a-zA-Z][\w.-]*)((?:\s+[^\s=/>]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(\/?)>/;
const ATTR_RE = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
const COMMENT_RE = /^<!--[\s\S]*?-->/;

/** 解析深度上限：更深的标签丢弃、只留文本，防止畸形嵌套压垮递归渲染。 */
const MAX_DEPTH = 48;

function parseAttrs(source: string): Record<string, string> {
  const attrs: Record<string, string> = {};
  for (const m of source.matchAll(ATTR_RE)) {
    const name = (m[1] ?? "").toLowerCase();
    if (ALLOWED_ATTRS.has(name)) {
      attrs[name] = decodeEntities(m[2] ?? m[3] ?? m[4] ?? "");
    }
  }
  return attrs;
}

/**
 * 把后端给的 MathML 字符串解析成白名单节点树（单遍栈式扫描）。
 * 白名单外的元素去掉标签、保留内容；annotation 整体丢弃；`mfenced` 改写成 `mrow` + `mo`；
 * 没有 `math` 根时补一个。
 */
export function parseMathml(mathml: string): MathNode[] {
  const root: MathElement = { kind: "element", tag: "#root", attrs: {}, children: [] };
  /** 打开中的元素；`null` 表示白名单外、只透传内容的元素 */
  const stack: { tag: string; element: MathElement | null; dropped: boolean }[] = [];
  let buf = "";
  let i = 0;

  const current = (): MathElement => {
    for (let s = stack.length - 1; s >= 0; s -= 1) {
      const element = stack[s]?.element;
      if (element) return element;
    }
    return root;
  };
  const dropping = (): boolean => stack.some((entry) => entry.dropped);
  const flush = (): void => {
    if (buf.length > 0 && !dropping()) {
      current().children.push({ kind: "text", value: decodeEntities(buf) });
    }
    buf = "";
  };

  while (i < mathml.length) {
    const ch = mathml.charAt(i);
    if (ch !== "<") {
      buf += ch;
      i += 1;
      continue;
    }
    const rest = mathml.slice(i);
    const comment = COMMENT_RE.exec(rest);
    if (comment) {
      i += comment[0].length;
      continue;
    }
    const m = TAG_RE.exec(rest);
    if (m === null) {
      buf += ch;
      i += 1;
      continue;
    }
    flush();
    i += m[0].length;
    const tag = (m[2] ?? "").toLowerCase();
    if (m[1] === "/") {
      for (let s = stack.length - 1; s >= 0; s -= 1) {
        if (stack[s]?.tag === tag) {
          stack.length = s;
          break;
        }
      }
      continue;
    }
    const selfClosing = m[4] === "/";
    const allowed = MATH_ELEMENT_TAGS.has(tag) && stack.length < MAX_DEPTH;
    const element: MathElement | null = allowed
      ? { kind: "element", tag, attrs: parseAttrs(m[3] ?? ""), children: [] }
      : null;
    const dropped = DROPPED_SUBTREES.has(tag);
    if (element && !dropping()) {
      current().children.push(element);
    }
    if (!selfClosing) {
      stack.push({ tag, element, dropped });
    }
  }
  flush();

  const nodes = root.children.map(rewriteFences);
  const hasMathRoot = nodes.some((n) => n.kind === "element" && n.tag === "math");
  return hasMathRoot ? nodes : [{ kind: "element", tag: "math", attrs: {}, children: nodes }];
}

function mo(text: string, attrs: Record<string, string>): MathNode {
  return { kind: "element", tag: "mo", attrs, children: [{ kind: "text", value: text }] };
}

/**
 * `mfenced` → `mrow`：开括号、子项（之间插分隔符）、闭括号。默认 `(` `)` 与逗号分隔；
 * 分隔符不够时重复最后一个，`separators=""` 不加分隔符（MathML 3 的语义）。
 */
function rewriteFences(node: MathNode): MathNode {
  if (node.kind === "text") {
    return node;
  }
  const children = node.children.map(rewriteFences);
  if (node.tag !== "mfenced") {
    return { ...node, children };
  }
  const open = node.attrs.open ?? "(";
  const close = node.attrs.close ?? ")";
  const separators = [...(node.attrs.separators ?? ",").replace(/\s/g, "")];
  const items = children.filter((c) => c.kind === "element");
  const row: MathNode[] = [];
  if (open) row.push(mo(open, { fence: "true", form: "prefix" }));
  items.forEach((item, index) => {
    if (index > 0 && separators.length > 0) {
      const sep = separators[Math.min(index - 1, separators.length - 1)] ?? "";
      row.push(mo(sep, { separator: "true" }));
    }
    row.push(item);
  });
  if (close) row.push(mo(close, { fence: "true", form: "postfix" }));
  return { kind: "element", tag: "mrow", attrs: {}, children: row };
}
