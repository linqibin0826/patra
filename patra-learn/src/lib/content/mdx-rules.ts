// patra-learn/src/lib/content/mdx-rules.ts
// 正文规则：只许用登记表里的组件、属性只能是字面量、不许 import / export 与表达式、
// 开头诊断题与结尾复述 / 迁移题；顺带收集 CodeRef 引用
import remarkGfm from "remark-gfm";
import remarkMdx from "remark-mdx";
import remarkParse from "remark-parse";
import { unified } from "unified";
import type { ComponentRole, ComponentSpec } from "@/teaching/registry";
import type { ContentIssue } from "./types";

export interface CodeRefUse {
  /// 引用所在条目的 slug
  entry: string;
  file: string;
  at: string;
  lines?: string;
}

/// estree 节点的最小结构（只用到字面量求值需要的字段）
interface EsNode {
  type: string;
  [key: string]: unknown;
}

interface EsProgram {
  body: EsNode[];
}

interface MdxAttribute {
  type: "mdxJsxAttribute" | "mdxJsxExpressionAttribute";
  name?: string;
  value?: string | null | { type: string; data?: { estree?: EsProgram } };
}

/// mdast 节点的最小结构
interface MdNode {
  type: string;
  name?: string | null;
  attributes?: MdxAttribute[];
  children?: MdNode[];
  data?: { estree?: EsProgram };
}

const processor = unified().use(remarkParse).use(remarkMdx).use(remarkGfm);

const JSX_TYPES = new Set(["mdxJsxFlowElement", "mdxJsxTextElement"]);
const EXPRESSION_TYPES = new Set(["mdxFlowExpression", "mdxTextExpression"]);

class DynamicValue extends Error {}

/// 只认字面量：字符串 / 数字 / 布尔 / null、数组、对象、负数、无插值模板字符串
function evaluateLiteral(node: EsNode): unknown {
  switch (node.type) {
    case "Literal":
      return node.value;
    case "TemplateLiteral": {
      const expressions = node.expressions as EsNode[];
      const quasis = node.quasis as { value: { cooked: string } }[];
      if (expressions.length > 0) throw new DynamicValue();
      return quasis.map((q) => q.value.cooked).join("");
    }
    case "UnaryExpression": {
      const value = evaluateLiteral(node.argument as EsNode);
      if (node.operator === "-" && typeof value === "number") return -value;
      throw new DynamicValue();
    }
    case "ArrayExpression":
      return (node.elements as (EsNode | null)[]).map((e) => {
        if (!e || e.type === "SpreadElement") throw new DynamicValue();
        return evaluateLiteral(e);
      });
    case "ObjectExpression": {
      const out: Record<string, unknown> = {};
      for (const prop of node.properties as EsNode[]) {
        if (prop.type !== "Property" || prop.computed) throw new DynamicValue();
        const key = prop.key as EsNode;
        const name = key.type === "Identifier" ? key.name : key.value;
        out[String(name)] = evaluateLiteral(prop.value as EsNode);
      }
      return out;
    }
    default:
      throw new DynamicValue();
  }
}

/// 属性表达式 `{…}` 求值；不是单个字面量表达式时抛 DynamicValue
function evaluateExpressionValue(program: EsProgram | undefined): unknown {
  const statement = program?.body[0];
  if (program?.body.length !== 1 || statement?.type !== "ExpressionStatement") {
    throw new DynamicValue();
  }
  return evaluateLiteral(statement.expression as EsNode);
}

/// 只有注释的表达式（`{/* … */}`）
function isComment(node: MdNode): boolean {
  return EXPRESSION_TYPES.has(node.type) && (node.data?.estree?.body.length ?? 0) === 0;
}

function hasContent(node: MdNode): boolean {
  return (node.children ?? []).some((c) => !isComment(c));
}

/// 标签与内容写在同一行时，MDX 把组件解析成段落里唯一的行内元素；这种段落按组件本身看待
function unwrapParagraph(node: MdNode | undefined): MdNode | undefined {
  if (node?.type !== "paragraph") return node;
  const meaningful = (node.children ?? []).filter(
    (c) => !(c.type === "text" && !(c as { value?: string }).value?.trim()),
  );
  const [only] = meaningful;
  return meaningful.length === 1 && only?.type === "mdxJsxTextElement" ? only : node;
}

/// 块是否为登记表里某个角色的组件
function roleOf(block: MdNode | undefined, registry: Record<string, ComponentSpec>) {
  const node = unwrapParagraph(block);
  if (!node || !JSX_TYPES.has(node.type) || !node.name) return undefined;
  return registry[node.name]?.role;
}

/// 读组件属性的字面量值；展开属性与非字面量的属性另行列出
function readAttributes(node: MdNode) {
  const props: Record<string, unknown> = {};
  const dynamicNames: string[] = [];
  let spread = false;
  for (const attr of node.attributes ?? []) {
    if (attr.type === "mdxJsxExpressionAttribute" || !attr.name) {
      spread = true;
      continue;
    }
    const value = attr.value;
    if (value === null || value === undefined) {
      props[attr.name] = true;
    } else if (typeof value === "string") {
      props[attr.name] = value;
    } else {
      try {
        props[attr.name] = evaluateExpressionValue(value.data?.estree);
      } catch {
        dynamicNames.push(attr.name);
      }
    }
  }
  return { props, spread, dynamicNames };
}

/// 正文里所有名为 name 的组件的字面量参数，按出现顺序；解析失败时为空。页面在构建时用它读诊断题、代码引用
export function readComponents(body: string, name: string): Record<string, unknown>[] {
  let root: MdNode;
  try {
    root = processor.parse(body) as unknown as MdNode;
  } catch {
    return [];
  }
  const found: Record<string, unknown>[] = [];
  const walk = (node: MdNode) => {
    if (JSX_TYPES.has(node.type) && node.name === name) found.push(readAttributes(node).props);
    for (const child of node.children ?? []) walk(child);
  };
  walk(root);
  return found;
}

/// 检查一篇条目的正文，返回问题与正文里的代码引用
export function checkEntryBody(
  input: { slug: string; file: string; body: string },
  registry: Record<string, ComponentSpec>,
): { issues: ContentIssue[]; codeRefs: CodeRefUse[] } {
  const { slug, file } = input;
  const issues: ContentIssue[] = [];
  const codeRefs: CodeRefUse[] = [];
  const report = (rule: string, message: string) => issues.push({ rule, file, message });

  let root: MdNode;
  try {
    root = processor.parse(input.body) as unknown as MdNode;
  } catch (error) {
    report("mdx-syntax", `MDX 解析失败：${(error as Error).message}`);
    return { issues, codeRefs };
  }

  const visitElement = (node: MdNode) => {
    const name = node.name ?? "";
    const spec = registry[name];
    if (!spec) {
      report(
        "component-unknown",
        name
          ? `<${name}> 不在组件登记表里（src/teaching/registry.ts）；排版用 Markdown，交互用组件库`
          : "不支持 JSX 片段 <>…</>",
      );
      return;
    }
    const { props, spread, dynamicNames } = readAttributes(node);
    if (spread) report("component-prop-dynamic", `<${name}> 不能用展开属性 {...}`);
    for (const attrName of dynamicNames) {
      report("component-prop-dynamic", `<${name}> 的 ${attrName} 只能写字面量，不能写变量或表达式`);
    }
    const dynamic = spread || dynamicNames.length > 0;
    if (!dynamic) {
      const result = spec.props.safeParse(props);
      if (!result.success) {
        const detail = result.error.issues
          .map((i) => `${i.path.join(".") || "（参数）"}：${i.message}`)
          .join("；");
        report("component-props-invalid", `<${name}> 参数不合法：${detail}`);
      }
    }
    const content = hasContent(node);
    if (spec.children === "none" && content) {
      report("component-children", `<${name}> 不能写子内容`);
    } else if (spec.children === "required" && !content) {
      report("component-children", `<${name}> 必须写子内容`);
    }
    if (name === "CodeRef" && typeof props.at === "string") {
      codeRefs.push({
        entry: slug,
        file,
        at: props.at,
        ...(typeof props.lines === "string" ? { lines: props.lines } : {}),
      });
    }
  };

  const walk = (node: MdNode) => {
    if (node.type === "mdxjsEsm") {
      report("mdx-esm", "正文不能写 import / export，组件由 mdx-components.tsx 注入");
      return;
    }
    if (EXPRESSION_TYPES.has(node.type)) {
      if (!isComment(node)) report("mdx-expression", "正文只能写注释 {/* … */}，不能写 {表达式}");
      return;
    }
    if (JSX_TYPES.has(node.type)) visitElement(node);
    for (const child of node.children ?? []) walk(child);
  };
  walk(root);

  // 注释与已单独报过的 import / export 不算正文块
  const blocks = (root.children ?? []).filter((n) => !isComment(n) && n.type !== "mdxjsEsm");
  if (roleOf(blocks[0], registry) !== "diagnostic") {
    report("diagnostic-missing", '正文第一块必须是诊断题 <Diagnose question="…" />');
  }
  const endRoles: (ComponentRole | undefined)[] = ["recap", "transfer"];
  if (blocks.length === 0 || !endRoles.includes(roleOf(blocks.at(-1), registry))) {
    report("recap-missing", "正文最后一块必须是复述题 <Recap /> 或迁移题 <Transfer />");
  }
  return { issues, codeRefs };
}
