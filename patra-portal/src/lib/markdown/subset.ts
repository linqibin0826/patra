import type { Data, Node, Nodes, Paragraph, Root } from "mdast";
import type {
  CompileContext,
  Extension as FromMarkdownExtension,
  Token as FromMarkdownToken,
} from "mdast-util-from-markdown";
import type {
  Code,
  Construct,
  Effects,
  State,
  Extension as SyntaxExtension,
} from "micromark-util-types";
import type { Processor } from "unified";

/// 正文里的提及 `<@用户ID>`：只有服务端解析出来的才是（客户端发来的会被转义成 `\<@…>`，后端 spec 5.4.3）
export interface MentionNode extends Node {
  type: "mention";
  userId: string;
  data?: Data;
}

/// 按原文显示的一段（图片、嵌套列表、围栏代码）：不是 `text`，自动链接、换行插件都不碰它，渲染时原样输出
export interface SourceTextNode extends Node {
  type: "sourceText";
  value: string;
  data?: Data;
}

declare module "mdast" {
  interface PhrasingContentMap {
    mention: MentionNode;
    sourceText: SourceTextNode;
  }
  interface RootContentMap {
    mention: MentionNode;
    sourceText: SourceTextNode;
  }
}

declare module "micromark-util-types" {
  interface TokenTypeMap {
    mention: "mention";
    mentionId: "mentionId";
    mentionMarker: "mentionMarker";
  }
}

const LESS_THAN = 60;
const AT = 64;
const GREATER_THAN = 62;
const DIGIT_ZERO = 48;
const DIGIT_ONE = 49;
const DIGIT_NINE = 57;

/// 不放行的构造：关掉后它们的原文就是普通文字（简报 §3.7「按原文显示」）。图片、嵌套列表、跨行的三反引号由
/// `remarkLiteralFallback` 按源位置切回原文。标题照常解析，再由 `remarkLiteralFallback` 降成粗体段落
const DISABLED_CONSTRUCTS = [
  "codeIndented",
  "codeFenced",
  "htmlFlow",
  "htmlText",
  "thematicBreak",
  "definition",
  "autolink",
];

/// `<@` + 规范十进制正整数 + `>`（`<@`、`>` 是 marker，数字是 `mentionId`；micromark 要求消费字符时总有打开的 token）。反斜杠转义、行内代码先于它处理，所以 `\<@42>` 与代码里的不会进来
function tokenizeMention(effects: Effects, ok: State, nok: State): State {
  const digits = (code: Code): State | undefined => {
    if (code !== null && code >= DIGIT_ZERO && code <= DIGIT_NINE) {
      effects.consume(code);
      return digits;
    }
    if (code !== GREATER_THAN) {
      return nok(code);
    }
    effects.exit("mentionId");
    effects.enter("mentionMarker");
    effects.consume(code);
    effects.exit("mentionMarker");
    effects.exit("mention");
    return ok;
  };
  const firstDigit = (code: Code): State | undefined => {
    if (code === null || code < DIGIT_ONE || code > DIGIT_NINE) {
      return nok(code);
    }
    effects.consume(code);
    return digits;
  };
  const at = (code: Code): State | undefined => {
    if (code !== AT) {
      return nok(code);
    }
    effects.consume(code);
    effects.exit("mentionMarker");
    effects.enter("mentionId");
    return firstDigit;
  };
  return (code: Code) => {
    effects.enter("mention");
    effects.enter("mentionMarker");
    effects.consume(code);
    return at;
  };
}

const mentionConstruct: Construct = { name: "mention", tokenize: tokenizeMention };

/// micromark 语法扩展：关掉不放行的构造 + 认提及
export const SUBSET_SYNTAX: SyntaxExtension[] = [
  { disable: { null: DISABLED_CONSTRUCTS } },
  { text: { [LESS_THAN]: mentionConstruct } },
];

/// mdast 扩展：提及 → `{ type: "mention", userId }`
export const MENTION_FROM_MARKDOWN: FromMarkdownExtension = {
  enter: {
    mention(this: CompileContext, token: FromMarkdownToken) {
      this.enter({ type: "mention", userId: "" }, token);
    },
  },
  exit: {
    mentionId(this: CompileContext, token: FromMarkdownToken) {
      const node = this.stack.at(-1);
      if (node?.type === "mention") {
        node.userId = this.sliceSerialize(token);
      }
    },
    mention(this: CompileContext, token: FromMarkdownToken) {
      this.exit(token);
    },
  },
};

/// remark-parse 从处理器的 data 里读这两个列表
interface ParserData {
  micromarkExtensions?: SyntaxExtension[];
  fromMarkdownExtensions?: FromMarkdownExtension[];
}

/// unified 插件：把白名单语法装进 remark-parse
export function remarkSubset(this: Processor): void {
  const data = this.data() as ParserData;
  data.micromarkExtensions = [...(data.micromarkExtensions ?? []), ...SUBSET_SYNTAX];
  data.fromMarkdownExtensions = [...(data.fromMarkdownExtensions ?? []), MENTION_FROM_MARKDOWN];
}

/// 节点在原文里的那一段
function sourceOf(node: Node, source: string): string | null {
  const start = node.position?.start.offset;
  const end = node.position?.end.offset;
  return start === undefined || end === undefined ? null : source.slice(start, end);
}

function literal(value: string, from: Node): SourceTextNode {
  return { type: "sourceText", value, ...(from.position ? { position: from.position } : {}) };
}

/// 解析后要改写的节点：标题降成同字号的粗体段落（2026-10-28 用户定：认 `#` 语法，但评论里不出现大标题）；
/// 图片（不发请求）、列表项里的子列表（不支持嵌套）、跨行的三反引号（围栏代码被当成了行内代码）显示成原文
function fallback(node: Nodes, source: string): Nodes {
  if (node.type === "heading") {
    const paragraph: Paragraph = {
      type: "paragraph",
      children: [{ type: "strong", children: node.children }],
      ...(node.position ? { position: node.position } : {}),
    };
    return fallback(paragraph, source);
  }
  if (node.type === "image") {
    return literal(sourceOf(node, source) ?? "", node);
  }
  if (node.type === "inlineCode") {
    const raw = sourceOf(node, source);
    if (raw?.startsWith("```") && raw.includes("\n")) {
      return literal(raw, node);
    }
  }
  if (node.type === "listItem") {
    node.children = node.children.map((child) => {
      if (child.type !== "list") {
        return child;
      }
      const paragraph: Paragraph = {
        type: "paragraph",
        children: [literal(sourceOf(child, source) ?? "", child)],
      };
      return paragraph;
    });
  }
  if ("children" in node) {
    const parent = node as { children: Nodes[] };
    parent.children = parent.children.map((child) => fallback(child, source));
  }
  return node;
}

/// unified 插件：按源位置把图片、嵌套列表、围栏代码换回原文
export function remarkLiteralFallback() {
  return (tree: Root, file: { value: unknown }) => {
    fallback(tree, String(file.value));
  };
}
