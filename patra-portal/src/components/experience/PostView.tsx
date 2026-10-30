"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Info, MoreHorizontal } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { AuthorAvatar, AuthorName } from "@/components/discussion/AuthorIdentity";
import { MarkdownBody } from "@/components/discussion/MarkdownBody";
import { DeletePostDialog } from "@/components/experience/DeletePostDialog";
import { ExperienceFacts } from "@/components/experience/ExperienceFacts";
import { ExperienceForm } from "@/components/experience/ExperienceForm";
import { ExperienceTimeline } from "@/components/experience/ExperienceTimeline";
import { PostRevisionDialog } from "@/components/experience/PostRevisionDialog";
import { HelpfulButton } from "@/components/helpful/HelpfulButton";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { authorView } from "@/lib/discussion/author";
import { journalHref } from "@/lib/experience/links";
import { afterEditOrDelete } from "@/lib/experience/queries";
import { useRefreshOnIdentityChange } from "@/lib/experience/use-identity-refresh";
import { formatDate } from "@/lib/format-time";
import type { PostDetail } from "@/lib/interaction/experience-schema";
import { btnSecondary } from "@/lib/portal-ui";
import { useCopy } from "@/lib/use-copy";
import { cn } from "@/lib/utils";

interface PostViewProps {
  /// 没被删除的经验（`experience` 不为 null）
  detail: PostDetail;
  /// 复制链接用的完整公开地址
  shareUrl: string;
}

/// 经验页主栏（简报 §3.16、spec 8.2）：眉题、期刊名、署名与操作；展示态是时间线、定义表、正文，编辑态原地换成
/// 表单。详情来自页面（RSC），保存后先用返回的新详情、再刷新页面取一份
export function PostView({ detail, shareUrl }: PostViewProps) {
  const router = useRouter();
  const client = useQueryClient();
  useRefreshOnIdentityChange();
  const [editing, setEditing] = useState(false);
  // 保存后先显示返回的新详情；页面刷新带来新的 props 时（`base` 不再是当前的），以页面的为准
  const [saved, setSaved] = useState<{ base: PostDetail; next: PostDetail } | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const { copy } = useCopy();
  const menuButton = useRef<HTMLButtonElement | null>(null);
  const shown = saved?.base === detail ? saved.next : detail;
  const experience = shown.experience;
  if (!experience) {
    return null;
  }
  const view = authorView(experience);
  const journalLink = journalHref(shown);
  const cacheTarget = {
    journalId: shown.journal.key,
    authorUserId: experience.author?.userId ?? null,
    postId: shown.postId,
  };

  const copyLink = async () => {
    if ((await copy(shareUrl, menuButton.current)) === "copied") {
      toast.success("已复制链接");
    }
  };

  return (
    <article aria-labelledby="post-journal" className="flex min-w-0 flex-col gap-8">
      <header className="flex flex-col gap-3.5">
        <SectionEyebrow className="mb-0">投稿经验 · Submission experience</SectionEyebrow>
        <h1
          id="post-journal"
          className="m-0 text-balance font-medium font-serif text-display-3 text-fg-1 leading-heading tracking-tight"
        >
          {journalLink ? (
            <Link href={journalLink} className="link-draw">
              {shown.journal.title}
            </Link>
          ) : (
            shown.journal.title
          )}
        </h1>
        <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1.5 text-fg-3 text-sm">
          <AuthorAvatar view={view} size={28} />
          <AuthorName view={view} className="text-md" />
          <span aria-hidden="true">·</span>
          <span>发布于 {formatDate(experience.createdAt)}</span>
          {experience.edited && (
            <>
              <span aria-hidden="true">·</span>
              <button
                type="button"
                onClick={() => setHistoryOpen(true)}
                className="text-fg-3 underline-offset-3 hover:text-fg-1 hover:underline"
              >
                已编辑
              </button>
            </>
          )}
          {!editing && (
            <div className="ml-auto flex items-center gap-2 max-[880px]:ml-0 max-[880px]:w-full">
              {experience.mine && (
                <button
                  type="button"
                  onClick={() => setEditing(true)}
                  className={cn(btnSecondary, "px-4 py-1.5 text-sm")}
                >
                  编辑
                </button>
              )}
              <DropdownMenu>
                <DropdownMenuTrigger
                  ref={menuButton}
                  aria-label="投稿经验操作"
                  onKeyDown={(event) => {
                    if (event.shiftKey && event.key === "F10") {
                      event.preventDefault();
                      event.currentTarget.click();
                    }
                  }}
                  className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900 data-popup-open:bg-paper-200"
                >
                  <MoreHorizontal className="size-4" aria-hidden="true" />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-36">
                  <DropdownMenuItem onClick={() => void copyLink()}>复制链接</DropdownMenuItem>
                  {experience.mine && (
                    <>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        onClick={() => setDeleteOpen(true)}
                        className="text-status-danger"
                      >
                        删除
                      </DropdownMenuItem>
                    </>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}
        </div>
        {experience.anonymous && experience.mine && (
          <p className="m-0 flex items-center gap-2 self-start rounded-md bg-slate-50 px-3 py-2 text-fg-1 text-sm">
            <Info className="size-3.5 text-slate-500" aria-hidden="true" />
            匿名发布 · 只有你知道是你写的
          </p>
        )}
      </header>

      {editing ? (
        <ExperienceForm
          mode="edit"
          postId={shown.postId}
          journalTitle={shown.journal.title}
          initial={experience}
          anonymous={experience.anonymous}
          onDone={(next) => {
            setSaved({ base: detail, next });
            setEditing(false);
            void afterEditOrDelete(client, cacheTarget);
            router.refresh();
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <>
          <ExperienceTimeline experience={experience} />
          <ExperienceFacts experience={experience} />
          {experience.body && (
            <div data-post-body>
              <MarkdownBody source={experience.body} mentions={[]} variant="reading" />
            </div>
          )}
          {/* 经验是页面主对象：胶囊形；props 来自页面、不在查询缓存里，按钮自己显示返回值，删了就刷新页面 */}
          {(!experience.mine || experience.helpfulCount > 0) && (
            <div className="flex">
              <HelpfulButton
                target={{ type: "post", id: shown.postId }}
                helpful={experience.helpful}
                count={experience.helpfulCount}
                own={experience.mine}
                variant="pill"
                onGone={() => router.refresh()}
              />
            </div>
          )}
        </>
      )}

      <PostRevisionDialog postId={shown.postId} open={historyOpen} onOpenChange={setHistoryOpen} />
      <DeletePostDialog
        postId={shown.postId}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onDeleted={() => {
          void afterEditOrDelete(client, cacheTarget);
          toast.success("投稿经验已删除");
          router.push(journalLink ?? "/journals");
        }}
      />
    </article>
  );
}
