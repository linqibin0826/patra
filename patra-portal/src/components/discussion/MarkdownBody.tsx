import type { Element, ElementContent } from "hast";
import { ArrowUpRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import Markdown, { type Components, type Options } from "react-markdown";
import remarkBreaks from "remark-breaks";
import type { Mention } from "@/lib/interaction/discussion-schema";
import { remarkAutolink } from "@/lib/markdown/autolink";
import {
  type MentionNode,
  remarkLiteralFallback,
  remarkSubset,
  type SourceTextNode,
} from "@/lib/markdown/subset";
import { cn } from "@/lib/utils";

/// 渲染后只可能出现这些元素（解析层已经关掉了其余构造，这里再兜一层）
const ALLOWED_ELEMENTS = [
  "p",
  "strong",
  "em",
  "blockquote",
  "ul",
  "ol",
  "li",
  "code",
  "a",
  "br",
  "span",
];

const VARIANT = {
  comment: "text-md leading-relaxed text-fg-1",
  small: "text-sm leading-relaxed text-fg-2",
  reading: "font-serif text-lg leading-reading text-fg-1",
} as const;

const REMARK_PLUGINS: Options["remarkPlugins"] = [
  remarkSubset,
  remarkLiteralFallback,
  remarkAutolink,
  remarkBreaks,
];

/// 提及节点 → 带用户 ID 的 span，组件按 `mentions` 画
const REHYPE_OPTIONS: Options["remarkRehypeOptions"] = {
  handlers: {
    mention: (_state: unknown, node: MentionNode): Element => ({
      type: "element",
      tagName: "span",
      properties: { dataMentionId: node.userId },
      children: [],
    }),
    sourceText: (_state: unknown, node: SourceTextNode): ElementContent[] =>
      sourceLines(node.value),
  },
};

/// 原文按行输出，行间是 `br`
function sourceLines(value: string): ElementContent[] {
  const out: ElementContent[] = [];
  value.split("\n").forEach((line, index) => {
    if (index > 0) {
      out.push({ type: "element", tagName: "br", properties: {}, children: [] });
    }
    out.push({ type: "text", value: line });
  });
  return out;
}

/// 只放行 http(s)；其余协议（javascript:、mailto: 等）按文字显示
const HTTP_URL = /^https?:\/\//i;

/// 裸网址显示的最大字符数，超过时中间省略（简报 §3.7）
const URL_DISPLAY_MAX = 48;

/// 超长网址：保留开头 30 个字符与结尾 17 个字符，中间 `…`
export function shortenUrl(url: string): string {
  const chars = [...url];
  return chars.length <= URL_DISPLAY_MAX
    ? url
    : `${chars.slice(0, 30).join("")}…${chars.slice(-(URL_DISPLAY_MAX - 31)).join("")}`;
}

interface MarkdownBodyProps {
  /// Markdown 原文（评论的 `body`，@ 是 `<@用户ID>`）
  source: string;
  /// 正文里提及的人（评论条目的 `mentions`）；经验正文不解析 @，传空数组
  mentions: readonly Mention[];
  /// `comment`：评论（15px）；`small`：小字；`reading`：经验正文（衬线阅读字号）
  variant?: keyof typeof VARIANT;
  className?: string;
}

/// Markdown 子集的白名单渲染（简报 §3.7、spec 第 5 节）：评论、编辑器预览、经验正文共用。没有 hooks，服务端与
/// 客户端都能渲染
export function MarkdownBody({
  source,
  mentions,
  variant = "comment",
  className,
}: MarkdownBodyProps) {
  const byId = new Map(mentions.map((mention) => [mention.userId, mention]));
  const components: Components = {
    a: ({ href, children, ...rest }) => {
      const kind = (rest as Record<string, unknown>)["data-autolink"];
      return kind === "url" && href ? (
        <ExternalLink href={href} title={href}>
          {shortenUrl(href)}
        </ExternalLink>
      ) : (
        <ExternalLink href={href}>{children}</ExternalLink>
      );
    },
    span: ({ node, children, ...rest }) => {
      const userId = (rest as Record<string, unknown>)["data-mention-id"];
      return typeof userId === "string" ? (
        <MentionLabel mention={byId.get(userId)} />
      ) : (
        <span>{children}</span>
      );
    },
  };
  return (
    <div
      className={cn(
        "flex flex-col gap-2.5 break-words [overflow-wrap:anywhere]",
        "[&_blockquote]:border-l-2 [&_blockquote]:border-ink-300 [&_blockquote]:pl-3.5 [&_blockquote]:text-fg-2",
        "[&_ul]:list-disc [&_ol]:list-decimal [&_ul,&_ol]:flex [&_ul,&_ol]:flex-col [&_ul,&_ol]:gap-1 [&_ul,&_ol]:pl-5.5",
        "[&_code]:rounded-sm [&_code]:bg-paper-200 [&_code]:px-1 [&_code]:font-mono [&_code]:text-sm",
        "[&_strong]:font-semibold",
        VARIANT[variant],
        className,
      )}
    >
      <Markdown
        remarkPlugins={REMARK_PLUGINS}
        remarkRehypeOptions={REHYPE_OPTIONS}
        allowedElements={ALLOWED_ELEMENTS}
        unwrapDisallowed
        skipHtml
        components={components}
      >
        {source}
      </Markdown>
    </div>
  );
}

/// 外链：新窗口、`nofollow ugc noopener`、末尾外链小图标，读屏名称后加「（新窗口打开）」
function ExternalLink({
  href,
  title,
  children,
}: {
  href: string | undefined;
  title?: string;
  children: ReactNode;
}) {
  if (!href || !HTTP_URL.test(href)) {
    return <>{children}</>;
  }
  return (
    <a
      href={href}
      title={title}
      target="_blank"
      rel="nofollow ugc noopener"
      className="text-link underline-offset-2 hover:text-link-hover hover:underline"
    >
      {children}
      <ArrowUpRight className="ml-0.5 inline size-3 align-baseline" aria-hidden="true" />
      <span className="sr-only">（新窗口打开）</span>
    </a>
  );
}

/// 提及：有用户名链主页；主页不可见是 `@昵称`；已注销或实体里没有是 `@已注销用户`
function MentionLabel({ mention }: { mention: Mention | undefined }) {
  if (!mention || mention.deleted || (!mention.username && !mention.displayName)) {
    return <span className="text-fg-3">@已注销用户</span>;
  }
  if (!mention.username) {
    return <span className="font-medium">@{mention.displayName}</span>;
  }
  return (
    <Link href={`/u/${mention.username}`} className="font-medium text-link hover:text-link-hover">
      @{mention.username}
    </Link>
  );
}
