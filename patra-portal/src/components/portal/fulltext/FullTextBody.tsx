import type { ReactNode } from "react";
import { FigureBlock } from "@/components/portal/fulltext/FigureBlock";
import { MathMl } from "@/components/portal/fulltext/MathMl";
import { TableBlock, type TableBlockRow } from "@/components/portal/fulltext/TableBlock";
import {
  bodyAnchor,
  figureSrc,
  inlineText,
  isSupplementarySection,
  referenceAnchor,
} from "@/lib/portal-api/fulltext-derive";
import type {
  FullTextBlock,
  FullTextBody,
  FullTextInline,
  FullTextSectionNode,
  FullTextTableCell,
} from "@/types/fulltext";

/** 一次渲染的上下文：交叉引用能跳到哪些目标、参考文献引用的编号与第一次出现的位置。 */
interface RenderContext {
  publicationId: string;
  /** 正文里带 id 的章节、图、表、公式、文本框 */
  bodyIds: ReadonlySet<string>;
  referenceIds: ReadonlySet<string>;
  citeCount: number;
  firstCiteByRef: Record<string, string>;
  keyCount: number;
}

export interface RenderedFullTextBody {
  content: ReactNode;
  /** 每条参考文献的著录文本（与 body.references 一一对应） */
  references: ReactNode[];
  /** 参考文献 id → 正文里第一次引用它的锚点 id（「↩ 回到正文」的默认去处） */
  firstCiteByRef: Record<string, string>;
}

/**
 * 段落的三种字级：正文（serif 17px、行高 1.65、行宽 36em）、文末附加（降一档到 15px）、
 * 图注表注（继承外层的 sans 小字，只管段距）。列表与引文框跟随所在位置的字级。
 */
type Tone = "body" | "back" | "note";

const TONE_CLASS: Record<Tone, string> = {
  body: "mb-[1em] max-w-[36em] text-pretty font-serif text-lg leading-reading text-ink-800",
  back: "mb-[1em] max-w-[36em] text-pretty font-serif text-md leading-reading text-ink-800",
  note: "[&+p]:mt-1",
};

function nextKey(ctx: RenderContext): string {
  ctx.keyCount += 1;
  return `n${ctx.keyCount}`;
}

function collectIds(sections: FullTextSectionNode[], blocks: FullTextBlock[], out: Set<string>) {
  for (const block of blocks) {
    switch (block.kind) {
      case "figure":
      case "table":
      case "formula":
        if (block.id) out.add(block.id);
        collectIds([], block.kind === "formula" ? [] : block.caption, out);
        break;
      case "box":
        if (block.id) out.add(block.id);
        collectIds([], block.blocks, out);
        break;
      case "quote":
        collectIds([], block.blocks, out);
        break;
      case "list":
        for (const item of block.items) collectIds([], item, out);
        break;
      default:
        break;
    }
  }
  for (const section of sections) {
    out.add(section.id);
    collectIds(section.sections, section.blocks, out);
  }
}

/** 外链只放行 http(s)；`doi:` 改成 doi.org 地址；其余（javascript: 等）返回 null，按文字显示。 */
function safeHref(href: string): string | null {
  if (/^doi:/i.test(href)) {
    return `https://doi.org/${href.slice(4).trim()}`;
  }
  try {
    const url = new URL(href);
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

const STYLE_TAGS = {
  italic: "em",
  bold: "strong",
  sup: "sup",
  sub: "sub",
  underline: "u",
} as const;

function renderInlines(nodes: FullTextInline[], ctx: RenderContext): ReactNode[] {
  return nodes.map((node) => renderInline(node, ctx));
}

function renderInline(node: FullTextInline, ctx: RenderContext): ReactNode {
  const key = nextKey(ctx);
  switch (node.kind) {
    case "text":
      return node.text;
    case "break":
      return <br key={key} />;
    case "math":
      return <MathMl key={key} mathml={node.mathml} />;
    case "style": {
      const children = renderInlines(node.children, ctx);
      if (node.style === "smallcaps") {
        return (
          <span key={key} className="[font-variant:small-caps]">
            {children}
          </span>
        );
      }
      if (node.style === "monospace") {
        return (
          <code key={key} className="font-mono text-[0.9em]">
            {children}
          </code>
        );
      }
      const Tag = STYLE_TAGS[node.style];
      return (
        <Tag key={key} className={node.style === "bold" ? "font-semibold" : undefined}>
          {children}
        </Tag>
      );
    }
    case "link": {
      const href = safeHref(node.href);
      const children = renderInlines(node.children, ctx);
      if (!href) {
        return <span key={key}>{children}</span>;
      }
      return (
        <a
          key={key}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="link-draw break-words text-link hover:text-link-hover"
        >
          {children}
        </a>
      );
    }
    case "xref":
      return renderXref(node, key, ctx);
  }
}

function renderXref(
  node: Extract<FullTextInline, { kind: "xref" }>,
  key: string,
  ctx: RenderContext,
): ReactNode {
  const children = renderInlines(node.children, ctx);
  const target = node.targets[0];
  if (node.refType === "bibr" && target && ctx.referenceIds.has(target)) {
    ctx.citeCount += 1;
    const citeId = `cite-${ctx.citeCount}`;
    ctx.firstCiteByRef[target] ??= citeId;
    return (
      <a
        key={key}
        id={citeId}
        href={`#${referenceAnchor(target)}`}
        aria-label={`参考文献 ${inlineText(node.children).trim()}`}
        data-cite-ref={target}
        className="link-draw scroll-mt-[68px] rounded-[2px] text-link hover:text-link-hover"
      >
        {children}
      </a>
    );
  }
  if (node.refType !== "bibr" && target && ctx.bodyIds.has(target)) {
    return (
      <a
        key={key}
        href={`#${bodyAnchor(target)}`}
        className="link-draw text-link hover:text-link-hover"
      >
        {children}
      </a>
    );
  }
  return <span key={key}>{children}</span>;
}

function renderBlocks(
  blocks: FullTextBlock[],
  ctx: RenderContext,
  tone: Tone = "body",
): ReactNode[] {
  return blocks.map((block) => renderBlock(block, ctx, tone));
}

function tableRows(rows: FullTextTableCell[][], ctx: RenderContext): TableBlockRow[] {
  return rows.map((row) =>
    row.map((c) => ({
      content: renderInlines(c.content, ctx),
      colspan: c.colspan,
      rowspan: c.rowspan,
      header: c.header,
    })),
  );
}

/** 图注、表题：第一段是标题，其余是正文。第一块不是段落时没有标题。 */
function splitCaption(caption: FullTextBlock[]): {
  title: FullTextInline[];
  rest: FullTextBlock[];
} {
  const [first, ...rest] = caption;
  return first?.kind === "paragraph"
    ? { title: first.content, rest }
    : { title: [], rest: caption };
}

function renderBlock(block: FullTextBlock, ctx: RenderContext, tone: Tone): ReactNode {
  const key = nextKey(ctx);
  switch (block.kind) {
    case "paragraph":
      return (
        <p key={key} className={`m-0 ${TONE_CLASS[tone]}`}>
          {renderInlines(block.content, ctx)}
        </p>
      );
    case "table": {
      const { title, rest } = splitCaption(block.caption);
      const notes = [...rest, ...block.footnotes];
      return (
        <TableBlock
          key={key}
          anchorId={block.id ? bodyAnchor(block.id) : undefined}
          label={block.label}
          title={renderInlines(title, ctx)}
          titleText={inlineText(title).trim()}
          head={tableRows(block.head, ctx)}
          body={tableRows(block.body, ctx)}
          footnotes={notes.length > 0 ? renderBlocks(notes, ctx, "note") : null}
        />
      );
    }
    case "figure": {
      const { title, rest } = splitCaption(block.caption);
      return (
        <FigureBlock
          key={key}
          anchorId={block.id ? bodyAnchor(block.id) : undefined}
          label={block.label}
          altText={inlineText(title).trim()}
          title={renderInlines(title, ctx)}
          caption={renderBlocks(rest, ctx, "note")}
          src={block.image ? figureSrc(ctx.publicationId, block.image) : null}
          width={block.width}
          height={block.height}
        />
      );
    }
    case "list": {
      const List = block.ordered ? "ol" : "ul";
      return (
        <List
          key={key}
          className={`m-0 pl-[1.25em] ${TONE_CLASS[tone]} ${block.ordered ? "list-decimal" : "list-disc"} marker:text-fg-3`}
        >
          {block.items.map((item) => (
            <li key={nextKey(ctx)} className="pl-1 [&>p]:mb-2">
              {renderBlocks(item, ctx, tone)}
            </li>
          ))}
        </List>
      );
    }
    case "quote":
      return (
        <blockquote
          key={key}
          className="m-0 mb-[1em] ml-[1.5em] border-l border-border-strong pl-4 [&>p]:text-fg-2"
        >
          {renderBlocks(block.blocks, ctx, tone)}
        </blockquote>
      );
    case "box": {
      const titleId = block.title.length > 0 ? `${nextKey(ctx)}-title` : undefined;
      return (
        <aside
          key={key}
          id={block.id ? bodyAnchor(block.id) : undefined}
          aria-labelledby={titleId}
          className="my-8 scroll-mt-[45px] rounded-lg border border-border-default bg-bg-paper p-5 [&>p:last-child]:mb-0"
        >
          {titleId && (
            <p
              id={titleId}
              className="m-0 mb-3 font-sans text-sm font-semibold uppercase tracking-caps text-fg-2"
            >
              {renderInlines(block.title, ctx)}
            </p>
          )}
          {renderBlocks(block.blocks, ctx)}
        </aside>
      );
    }
    case "code":
      return (
        <div key={key} className="my-6">
          {TEX_SOURCE.test(block.text) && (
            <p className="m-0 mb-2 font-mono text-2xs tracking-mono text-fg-3">公式 · LaTeX 源码</p>
          )}
          <pre className="m-0 overflow-x-auto rounded-md bg-bg-sunken p-3 font-mono text-sm leading-normal whitespace-pre text-fg-1">
            <code>{block.text}</code>
          </pre>
        </div>
      );
    case "formula":
      return (
        <div
          key={key}
          id={block.id ? bodyAnchor(block.id) : undefined}
          className="my-5 flex scroll-mt-[45px] items-center gap-4"
        >
          <div className="math-scroll min-w-0 flex-1 overflow-x-auto py-1.5 text-center">
            {block.mathml ? (
              <MathMl mathml={block.mathml} />
            ) : (
              block.image && (
                // biome-ignore lint/performance/noImgElement: 同源 Route Handler 转发的原图，不走 next/image 的二次压缩
                <img
                  src={figureSrc(ctx.publicationId, block.image)}
                  alt={block.label ? `公式 ${block.label}` : "公式"}
                  width={block.width ?? undefined}
                  height={block.height ?? undefined}
                  loading="lazy"
                  className="inline-block h-auto max-w-full"
                />
              )
            )}
          </div>
          {block.label && (
            <span className="shrink-0 font-mono text-sm text-fg-3">{block.label}</span>
          )}
        </div>
      );
    default:
      return null;
  }
}

/** 只有 TeX 的公式会被后端输出成代码块（BMC、Sci Rep）：认出常见 TeX 命令就加小标。 */
const TEX_SOURCE =
  /\\(frac|begin|end|left|right|sum|int|prod|mathrm|mathbf|mathit|mathcal|text|sqrt|hat|bar|tilde|cdot|times|le|ge|leq|geq|pm|infty|alpha|beta|gamma|delta|sigma|mu|lambda|theta|operatorname)\b/;

/*
 * 页内锚点的落点：根元素已有 `scroll-padding-top: 72px`（让出顶部导航），元素自己的 scroll-margin 叠加在它上面。
 * 正文标题、图表要落在 TopNav 57 + 面包屑 45 + 16 留白 = 117px 处，所以写 117 − 72 = 45px；
 * 参考文献条目（134px）与正文引用（140px）同理写 62px、68px。
 */

/** 标题字级：一级 serif 24px、二级 serif 19px、三级及以下 sans 15px 600（简报 §3.4）。 */
const HEADING_CLASS = [
  "mt-12 mb-4 font-serif text-2xl leading-tight font-medium tracking-tight text-fg-1",
  "mt-8 mb-3 font-serif text-xl leading-snug font-medium text-fg-1",
  "mt-6 mb-2 font-sans text-md font-semibold text-fg-1",
];

function renderSections(
  sections: FullTextSectionNode[],
  depth: number,
  ctx: RenderContext,
  tone: Tone,
): ReactNode[] {
  return sections
    .filter((s) => !isSupplementarySection(s))
    .map((s) => {
      const key = nextKey(ctx);
      const level = Math.min(depth + 1, 6);
      const Heading = `h${level}` as "h2" | "h3" | "h4" | "h5" | "h6";
      return (
        <section key={key}>
          {s.title.length > 0 && (
            <Heading
              id={bodyAnchor(s.id)}
              className={`scroll-mt-[45px] text-balance ${HEADING_CLASS[Math.min(depth - 1, 2)]}`}
            >
              {renderInlines(s.title, ctx)}
            </Heading>
          )}
          {renderBlocks(s.blocks, ctx, tone)}
          {renderSections(s.sections, depth + 1, ctx, tone)}
        </section>
      );
    });
}

/**
 * 正文渲染（纯函数，服务端与客户端都能用）：按白名单把正文结构转成 React 元素。
 * 用函数而不是组件递归，是为了在同一遍里按阅读顺序给参考文献引用编号，并把每条参考文献
 * 第一次被引用的位置交给参考文献列表——组件是惰性渲染的，拿不到这个结果。
 */
export function renderFullTextBody(
  body: FullTextBody,
  publicationId: string,
): RenderedFullTextBody {
  const bodyIds = new Set<string>();
  collectIds([...body.sections, ...body.back], body.blocks, bodyIds);
  const ctx: RenderContext = {
    publicationId,
    bodyIds,
    referenceIds: new Set(body.references.flatMap((r) => (r.id ? [r.id] : []))),
    citeCount: 0,
    firstCiteByRef: {},
    keyCount: 0,
  };
  const content = (
    <>
      {renderBlocks(body.blocks, ctx)}
      {renderSections(body.sections, 1, ctx, "body")}
      {renderSections(body.back, 1, ctx, "back")}
    </>
  );
  const references = body.references.map((r) => renderInlines(r.content, ctx));
  return { content, references, firstCiteByRef: ctx.firstCiteByRef };
}
