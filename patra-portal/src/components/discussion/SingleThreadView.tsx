"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Eye } from "lucide-react";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { CommentComposer } from "@/components/discussion/CommentComposer";
import type { CommentActions } from "@/components/discussion/CommentItem";
import { CommentThread } from "@/components/discussion/CommentThread";
import { DiscussionSkeleton } from "@/components/discussion/DiscussionSkeleton";
import {
  Announcer,
  DiscardReplyConfirm,
  DiscussionHeader,
  type DiscussionLocation,
  type DiscussionViewProps,
  useFresh,
} from "@/components/discussion/discussion-parts";
import { useCommentActions } from "@/components/discussion/use-comment-actions";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import {
  appendReplySingle,
  applyDeletionSingle,
  replaceCommentSingle,
  type SingleData,
} from "@/lib/discussion/cache";
import { SINGLE_REPLY_PLACEHOLDER } from "@/lib/discussion/copy";
import { markDiscussionStale, useSingleThread } from "@/lib/discussion/queries";
import type { Comment } from "@/lib/interaction/discussion-schema";
import { queryKeys } from "@/lib/query-keys";

interface SingleThreadViewProps extends DiscussionViewProps {
  commentId: string;
  location: DiscussionLocation;
}

/// 单楼视图（简报 §3.10）：只显示目标所在的那一楼（全部展开），目标持续高亮，页面滚到它；顶部提示条与
/// 「查看全部讨论」，没有排序，顶部是这一楼的回复框。目标不存在时只有提示
export function SingleThreadView({
  subject,
  copy,
  viewer,
  now,
  basePath,
  shareBase,
  titleId = "discussion-title",
  commentId,
  location,
}: SingleThreadViewProps) {
  const client = useQueryClient();
  const key = queryKeys.discussionSingle(subject.type, subject.key, commentId);
  const query = useSingleThread(subject, commentId);
  const [fresh, markFresh] = useFresh();
  const [announcement, setAnnouncement] = useState("");
  const [reply, setReply] = useState<Comment | null>(null);
  const [replyDirty, setReplyDirty] = useState(false);
  const [pendingReply, setPendingReply] = useState<Comment | null>(null);
  const scrolledTo = useRef<string | null>(null);
  /// 点开回复框的那个「回复」按钮：回复框关掉后焦点回到它（简报 §6）
  const replyTrigger = useRef<HTMLElement | null>(null);
  const stale = () => markDiscussionStale(client, subject);

  const permalinkOf = (comment: Comment) => `${basePath}?comment=${comment.commentId}`;
  /// 并进缓存前先取消还在路上的请求（同列表视图：写之前发出的请求晚回来会冲掉结果）
  const update = async (change: (data: SingleData) => SingleData) => {
    await client.cancelQueries({ queryKey: key });
    client.setQueryData<SingleData>(key, (data) => (data ? change(data) : data));
  };
  const target = query.data && "targetId" in query.data ? query.data.targetId : null;

  // 打开后滚到目标（导航栏与面包屑之下 24px，靠 scroll-margin-top），每个目标只滚一次
  useEffect(() => {
    if (target && scrolledTo.current !== target) {
      scrolledTo.current = target;
      document.getElementById(`comment-${target}`)?.scrollIntoView({ block: "start" });
    }
  }, [target]);

  const { handlers, editingOf, dialogs } = useCommentActions({
    subject,
    viewer,
    now,
    shareBase,
    permalinkOf,
    openSingle: location.openSingle,
    onEdited: (comment) => {
      update((data) => replaceCommentSingle(data, comment));
      stale();
    },
    onDeleted: (comment, result) => {
      update((data) => applyDeletionSingle(data, comment.commentId, result));
      stale();
      if (!result.removedIds.includes(comment.rootId)) {
        void client.invalidateQueries({ queryKey: key });
      }
    },
  });

  const showAll = () => {
    location.showAll();
    document.getElementById("discussion")?.scrollIntoView({ block: "start" });
  };

  const actions: CommentActions = {
    ...handlers,
    onReply: (comment) => {
      if (reply && replyDirty) {
        setPendingReply(comment);
        return;
      }
      replyTrigger.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setReply(comment);
    },
    onOpenPermalink: (comment) => location.openSingle(comment.commentId),
    // 点有帮助时这条已被删除：重取这一楼（正在看，立刻取）
    onHelpfulGone: () => void client.invalidateQueries({ queryKey: key }),
  };

  const notice = (text: string) => (
    <div className="flex flex-wrap items-center gap-2.5 rounded-md bg-slate-50 px-3.5 py-2.5 text-sm leading-5 text-fg-1">
      <Eye className="size-4 shrink-0 text-slate-500" aria-hidden="true" />
      {text}
      <button
        type="button"
        onClick={showAll}
        className="font-medium text-link hover:text-link-hover"
      >
        查看全部讨论
      </button>
    </div>
  );

  let body: ReactNode;
  if (!query.data && !query.isError) {
    body = <DiscussionSkeleton />;
  } else if (!query.data) {
    // 只在一份数据都没有时整块降级：删除后对账重取失败时保留本地结果（spec 4.5）
    body = (
      <div className="rounded-lg border border-border-subtle">
        <InteractionUnavailable
          compact
          title={copy.unavailableTitle}
          body={copy.unavailableBody}
          onRetry={() => void query.refetch()}
        />
      </div>
    );
  } else if ("missing" in query.data) {
    body = notice("这条评论已删除，或者不在这里的讨论中。");
  } else {
    const { thread } = query.data;
    const onReplied = ({ comment }: { comment: Comment }) => {
      update((data) => appendReplySingle(data, comment));
      setReply(null);
      setReplyDirty(false);
      markFresh(comment.commentId);
      setAnnouncement("已回复");
      stale();
    };
    body = (
      <>
        {notice("你正在看讨论中的一楼。")}
        <CommentComposer
          subject={subject}
          mode={{ kind: "reply", root: thread.root, target: thread.root }}
          placeholder={SINGLE_REPLY_PLACEHOLDER}
          viewer={viewer}
          collapsible
          onDone={onReplied}
          onCancel={() => {}}
        />
        <div className="flex flex-col">
          <CommentThread
            thread={thread}
            now={now}
            permalinkOf={permalinkOf}
            actions={actions}
            freshId={fresh}
            currentId={query.data.targetId}
            editingOf={editingOf}
            replyBox={
              reply ? (
                <div data-composer className="flex flex-col gap-2">
                  {pendingReply && (
                    <DiscardReplyConfirm
                      onDiscard={() => {
                        setReply(pendingReply);
                        setPendingReply(null);
                        setReplyDirty(false);
                      }}
                      onKeep={() => setPendingReply(null)}
                    />
                  )}
                  <CommentComposer
                    key={reply.commentId}
                    subject={subject}
                    mode={{ kind: "reply", root: thread.root, target: reply }}
                    placeholder=""
                    viewer={viewer}
                    autoFocus
                    onDirtyChange={setReplyDirty}
                    onDone={onReplied}
                    onCancel={() => {
                      setReply(null);
                      setReplyDirty(false);
                      requestAnimationFrame(() => replyTrigger.current?.focus());
                    }}
                  />
                </div>
              ) : undefined
            }
          />
        </div>
      </>
    );
  }

  return (
    <div className="flex max-w-190 flex-col gap-5">
      <DiscussionHeader title={copy.title} titleId={titleId} />
      <Announcer text={announcement} />
      {body}
      {dialogs}
    </div>
  );
}
