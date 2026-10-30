"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AuthorAvatar } from "@/components/discussion/AuthorIdentity";
import { HelpfulButton } from "@/components/helpful/HelpfulButton";
import { type AuthorView, authorView } from "@/lib/discussion/author";
import {
  formatDetailLine,
  formatOutcomeLine,
  formatPublished,
  formatSubmittedMonth,
} from "@/lib/experience/format";
import { ARTICLE_TYPE_LABELS } from "@/lib/experience/labels";
import { refetchPostLists } from "@/lib/helpful/cache";
import type { Experience, UserExperience } from "@/lib/interaction/experience-schema";
import { excerpt } from "@/lib/markdown/plain";

type ExperienceRowProps =
  | { variant: "journal"; experience: Experience; now: number }
  | { variant: "profile"; item: UserExperience; now: number };

/// 两行摘录最多取这么多字（`line-clamp-2` 截到两行，这里只防止一次塞进整篇正文）
const EXCERPT_MAX = 160;

/// 作者行的名字：整行已经是链接，名字不再单独链接（不嵌套链接），只写文字
function authorText(view: AuthorView): string {
  switch (view.kind) {
    case "anonymous":
      return view.self ? "匿名作者（你）" : "匿名作者";
    case "deleted":
      return "已注销用户";
    default:
      return view.card.displayName ?? "";
  }
}

/// 匿名胶囊：只有本人看得到
function AnonymousChip({ label }: { label: string }) {
  return (
    <span className="inline-flex h-5 items-center rounded-full border border-ink-200 px-2 font-medium text-2xs text-fg-3">
      {label}
    </span>
  );
}

/// 标题行的链接：伪元素铺满整行（整行可点、悬停整行时标题下划线绘出），读屏名只是标题行
const ROW_LINK = "link-draw text-inherit after:absolute after:inset-0 after:content-['']";

/// 经验列表的一条（简报 v0.14 §3.14、§3.17，v0.15 §3.9）：`article` 里标题行是链到经验页的链接、铺满整行；
/// 底部行压在链接上面放有帮助按钮（按钮不能放进链接里）。`journal`（期刊页）第一行是投稿月份与类型、标题行是结果；
/// `profile`（主页标签）第一行是期刊名
export function ExperienceRow(props: ExperienceRowProps) {
  const experience = props.variant === "journal" ? props.experience : props.item.experience;
  const month = `${formatSubmittedMonth(experience.submittedOn)} · ${ARTICLE_TYPE_LABELS[experience.articleType]}`;
  const outcome = formatOutcomeLine(experience).replace("  →  ", " → ");
  const detail = formatDetailLine(experience);
  const body = experience.body ? excerpt(experience.body, EXCERPT_MAX) : "";
  const published = `发布于 ${formatPublished(experience.createdAt, props.now)}`;
  const client = useQueryClient();
  const href = `/posts/${experience.postId}`;
  return (
    <article className="relative flex flex-col gap-1.5 border-border-subtle border-t py-5 last:border-b">
      {props.variant === "journal" ? (
        <>
          <span className="font-mono text-2xs text-fg-3 tracking-mono">{month}</span>
          <span className="m-0 font-medium font-serif text-ink-900 text-lg leading-snug">
            <Link href={href} className={ROW_LINK}>
              {outcome}
            </Link>
          </span>
          {detail && (
            <span data-detail className="text-fg-2 text-sm tabular-nums leading-5">
              {detail}
            </span>
          )}
          {body && (
            <span
              data-excerpt
              className="mt-0.5 line-clamp-2 max-w-[72ch] text-fg-2 text-sm leading-relaxed"
            >
              {body}
            </span>
          )}
          <JournalByline experience={experience} published={published} />
        </>
      ) : (
        <>
          <span className="m-0 font-medium font-serif text-ink-900 text-lg leading-snug">
            <Link href={href} className={ROW_LINK}>
              {props.item.journal.title}
            </Link>
          </span>
          <span className="text-fg-2 text-sm tabular-nums leading-5">
            {month} · {outcome}
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-fg-3 text-xs">
            <span>{published}</span>
            {experience.edited && (
              <>
                <span aria-hidden="true">·</span>
                <span>已编辑</span>
              </>
            )}
            {experience.anonymous && <AnonymousChip label="匿名 · 仅你可见" />}
          </span>
        </>
      )}
      {(!experience.mine || experience.helpfulCount > 0) && (
        <div data-row-foot className="relative z-1 mt-1 flex items-center gap-4">
          <HelpfulButton
            target={{ type: "post", id: experience.postId }}
            helpful={experience.helpful}
            count={experience.helpfulCount}
            own={experience.mine}
            variant="text"
            onGone={() => refetchPostLists(client)}
          />
        </div>
      )}
    </article>
  );
}

/// 期刊页一行的作者行：头像、名字、发布日期、已编辑、本人的「你写的」与匿名胶囊
function JournalByline({ experience, published }: { experience: Experience; published: string }) {
  const view = authorView(experience);
  return (
    <span className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-fg-3 text-xs">
      <AuthorAvatar view={view} size={22} />
      <span className="font-medium text-fg-2">{authorText(view)}</span>
      <span aria-hidden="true">·</span>
      <span>{published}</span>
      {experience.edited && (
        <>
          <span aria-hidden="true">·</span>
          <span>已编辑</span>
        </>
      )}
      {experience.mine && (
        <>
          <span aria-hidden="true">·</span>
          <span>你写的</span>
        </>
      )}
      {experience.mine && experience.anonymous && <AnonymousChip label="匿名" />}
    </span>
  );
}
