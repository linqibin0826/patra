import type { ReactNode } from "react";
import { FigureBlock } from "@/components/portal/fulltext/FigureBlock";
import { MathMl } from "@/components/portal/fulltext/MathMl";
import { TableBlock, type TableBlockRow } from "@/components/portal/fulltext/TableBlock";
import { RailRow } from "@/components/portal/RailRow";
import {
  bodyAnchor,
  figureLabel,
  figureLayout,
  figureSrc,
  inlineText,
  isCitationSup,
  isSupplementarySection,
  numberSections,
  numericColumns,
  referenceAnchor,
  type SectionHeading,
  splitBack,
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
  /** 参考文献 id → 正文里被引用的次数（参考文献条目的「↩ 正文 N 处」） */
  citeCountByRef: Record<string, number>;
  /** 正文一、二级章节的序号与去掉原文序号后的标题 */
  numbering: Record<string, SectionHeading>;
  keyCount: number;
}

export interface RenderedFullTextBody {
  /** 正文（外层 `[data-ft-body]`，阅读进度按它量） */
  content: ReactNode;
  /** 文末「声明与附注」区块 `#declarations`；没有文末附加时为 null */
  declarations: ReactNode;
  /** 含图表的附录，排在声明区之后；没有时为 null */
  appendices: ReactNode;
  /** 每条参考文献的著录文本（与 body.references 一一对应） */
  references: ReactNode[];
  /** 参考文献 id → 正文里第一次引用它的锚点 id（「↩ 回到正文」的默认去处） */
  firstCiteByRef: Record<string, string>;
  /** 参考文献 id → 被引用的次数 */
  citeCountByRef: Record<string, number>;
}

/**
 * 段落的三种字级：正文（serif 17px、行高 1.65、行宽 36em）、文末附加（降一档到 15px）、
 * 图注表注（继承外层的 sans 小字，只管段距）。列表与引文框跟随所在位置的字级。
 */
type Tone = "body" | "back" | "note";

const TONE_CLASS: Record<Tone, string> = {
  body: "mb-[1.1em] max-w-[36em] text-pretty font-serif text-lg leading-reading text-ink-800",
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

/** 渲染行内节点；紧挨在引用上标前的文本去掉末尾空白，让上标贴住前文（spec §5.7）。 */
function renderInlines(nodes: FullTextInline[], ctx: RenderContext): ReactNode[] {
  return nodes.map((node, i) => {
    const next = nodes[i + 1];
    if (node.kind === "text" && next && isCitationSup(next)) {
      return node.text.trimEnd();
    }
    return renderInline(node, ctx);
  });
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
      const className =
        node.style === "bold"
          ? "font-semibold"
          : isCitationSup(node)
            ? "font-mono text-2xs font-medium"
            : undefined;
      return (
        <Tag key={key} className={className}>
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
    const targets = node.targets.filter((t) => ctx.referenceIds.has(t));
    for (const t of targets) {
      ctx.firstCiteByRef[t] ??= citeId;
      ctx.citeCountByRef[t] = (ctx.citeCountByRef[t] ?? 0) + 1;
    }
    return (
      <a
        key={key}
        id={citeId}
        href={`#${referenceAnchor(target)}`}
        aria-label={`参考文献 ${inlineText(node.children).trim()}`}
        data-cite-ref={target}
        data-cite-refs={targets.join(" ")}
        className="link-draw scroll-mt-[68px] rounded-[2px] text-clay-700 hover:text-link-hover"
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

/** 自己挂标签栏的块（图、表）；其余块连续地放进同一个标签栏行。 */
function ownsRail(block: FullTextBlock): boolean {
  return block.kind === "figure" || block.kind === "table";
}

/** 章节里的块按标签栏排：连续的段落、列表、引文框等合成一行（标签格留空），图表各自成行。 */
function renderRailBlocks(blocks: FullTextBlock[], ctx: RenderContext, tone: Tone): ReactNode[] {
  const out: ReactNode[] = [];
  let flow: ReactNode[] = [];
  const flush = () => {
    if (flow.length > 0) {
      out.push(<RailRow key={nextKey(ctx)}>{flow}</RailRow>);
      flow = [];
    }
  };
  for (const block of blocks) {
    if (ownsRail(block)) {
      flush();
      out.push(renderBlock(block, ctx, tone));
    } else {
      flow.push(renderBlock(block, ctx, tone));
    }
  }
  flush();
  return out;
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
          label={figureLabel(block.label)}
          title={renderInlines(title, ctx)}
          head={tableRows(block.head, ctx)}
          body={tableRows(block.body, ctx)}
          numeric={numericColumns(block.head, block.body)}
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
          label={figureLabel(block.label)}
          altText={inlineText(title).trim()}
          title={renderInlines(title, ctx)}
          caption={renderBlocks(rest, ctx, "note")}
          src={block.image ? figureSrc(ctx.publicationId, block.image) : null}
          width={block.width}
          height={block.height}
          layout={figureLayout(block.width, block.height)}
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

/** 标题字级（spec §5.6）：一级 serif 30px、二级 serif 24px、三级及以下 sans 15px 600。 */
const HEADING_CLASS = [
  "m-0 font-serif text-3xl leading-[1.12] font-medium tracking-tight text-fg-1",
  "m-0 font-serif text-2xl leading-tight font-medium tracking-tight text-fg-1",
  "m-0 font-sans text-md font-semibold text-fg-1",
];
/** 有标题章节的上间距：一级 96px（正文第一个 48px）、二级 48px、三级 24px。 */
const SECTION_CLASS = ["mt-24 first:mt-12", "mt-12", "mt-6"];
/** 标题与内容之间：20 / 16 / 8px。 */
const HEADING_ROW_CLASS = ["mb-5 items-baseline", "mb-4 items-baseline", "mb-2 items-baseline"];
const NUMBER_CLASS = [
  "font-mono text-sm font-medium tabular-nums text-clay-700",
  "font-mono text-xs tabular-nums text-fg-3",
];

/**
 * 按标签栏渲染章节：一、二级章节号放标签格（无编号表时标签格留空），标题在内容格。
 * `numbered` 为 false 时（附录）不编号。
 */
function renderSections(
  sections: FullTextSectionNode[],
  depth: number,
  ctx: RenderContext,
  tone: Tone,
  numbered = true,
): ReactNode[] {
  return sections
    .filter((s) => !isSupplementarySection(s))
    .map((s) => {
      const key = nextKey(ctx);
      const level = Math.min(depth + 1, 6);
      const Heading = `h${level}` as "h2" | "h3" | "h4" | "h5" | "h6";
      const tier = Math.min(depth - 1, 2);
      const heading = numbered ? ctx.numbering[s.id] : undefined;
      return (
        <section key={key} className={s.title.length > 0 ? SECTION_CLASS[tier] : undefined}>
          {s.title.length > 0 && (
            <RailRow
              className={HEADING_ROW_CLASS[tier]}
              label={
                heading && <span className={NUMBER_CLASS[Math.min(tier, 1)]}>{heading.label}</span>
              }
            >
              <Heading
                id={bodyAnchor(s.id)}
                className={`scroll-mt-[45px] text-balance ${HEADING_CLASS[tier]}`}
              >
                {renderInlines(heading?.title ?? s.title, ctx)}
              </Heading>
            </RailRow>
          )}
          {renderRailBlocks(s.blocks, ctx, tone)}
          {renderSections(s.sections, depth + 1, ctx, tone, numbered)}
        </section>
      );
    });
}

/** 声明区里一节的内容（不挂标签栏）：块照常渲染，子节标题用 sans 小标题。 */
function renderPlainSection(section: FullTextSectionNode, ctx: RenderContext): ReactNode[] {
  return [
    ...renderBlocks(section.blocks, ctx, "back"),
    ...section.sections
      .filter((c) => !isSupplementarySection(c))
      .map((c) => (
        <div key={nextKey(ctx)} className="mt-3">
          {c.title.length > 0 && (
            <p
              id={bodyAnchor(c.id)}
              className="m-0 mb-1 scroll-mt-[45px] font-sans text-sm font-semibold text-fg-1"
            >
              {renderInlines(c.title, ctx)}
            </p>
          )}
          {renderPlainSection(c, ctx)}
        </div>
      )),
  ];
}

/** 文末「声明与附注」（spec §5.11）：有标题的节合成一个 dl，无标题的节接在后面。 */
function renderDeclarations(
  declarations: FullTextSectionNode[],
  untitled: FullTextSectionNode[],
  count: number,
  ctx: RenderContext,
): ReactNode {
  if (declarations.length === 0 && untitled.length === 0) {
    return null;
  }
  return (
    <section
      id="declarations"
      aria-labelledby="declarations-title"
      className="mt-16 scroll-mt-[45px]"
    >
      <RailRow
        className="items-baseline"
        label={<span className="font-mono text-2xs tracking-caps text-fg-3">文末</span>}
      >
        <h2
          id="declarations-title"
          className="m-0 flex items-baseline gap-3 font-serif text-2xl font-medium tracking-tight text-fg-1"
        >
          声明与附注
          <span className="font-mono text-xs tracking-mono text-fg-3 tabular-nums">{count} 项</span>
        </h2>
      </RailRow>
      {declarations.length > 0 && (
        <dl className="m-0 mt-6 border-t border-border-subtle">
          {declarations.map((s) => (
            <div
              key={nextKey(ctx)}
              className="grid grid-cols-[128px_minmax(0,1fr)] gap-x-8 gap-y-1 border-b border-border-subtle py-4 @max-[580px]/main:grid-cols-1"
            >
              <dt
                id={bodyAnchor(s.id)}
                className="scroll-mt-[45px] font-sans text-sm leading-snug font-semibold text-fg-1"
              >
                {renderInlines(s.title, ctx)}
              </dt>
              <dd className="m-0 min-w-0 [&>p:last-child]:mb-0">{renderPlainSection(s, ctx)}</dd>
            </div>
          ))}
        </dl>
      )}
      {untitled.map((s) => (
        <RailRow key={nextKey(ctx)} className="mt-4">
          {renderPlainSection(s, ctx)}
        </RailRow>
      ))}
    </section>
  );
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
    citeCountByRef: {},
    numbering: numberSections(body.sections),
    keyCount: 0,
  };
  const content = (
    <div data-ft-body>
      {renderRailBlocks(body.blocks, ctx, "body")}
      {renderSections(body.sections, 1, ctx, "body")}
    </div>
  );
  const back = splitBack(body.back);
  const declarations = renderDeclarations(
    back.declarations,
    back.untitled,
    back.declarations.length + back.appendices.length,
    ctx,
  );
  const appendices =
    back.appendices.length > 0 ? renderSections(back.appendices, 1, ctx, "back", false) : null;
  const references = body.references.map((r) => renderInlines(r.content, ctx));
  return {
    content,
    declarations,
    appendices,
    references,
    firstCiteByRef: ctx.firstCiteByRef,
    citeCountByRef: ctx.citeCountByRef,
  };
}
