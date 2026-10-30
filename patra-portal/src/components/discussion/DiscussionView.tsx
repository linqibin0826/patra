"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CircleCheck } from "lucide-react";
import { type ReactNode, useRef, useState } from "react";
import { CommentComposer } from "@/components/discussion/CommentComposer";
import type { CommentActions } from "@/components/discussion/CommentItem";
import { CommentThread } from "@/components/discussion/CommentThread";
import { DiscussionEmpty } from "@/components/discussion/DiscussionEmpty";
import { DiscussionSkeleton } from "@/components/discussion/DiscussionSkeleton";
import {
  Announcer,
  DiscardReplyConfirm,
  DiscussionHeader,
  type DiscussionLocation,
  type DiscussionViewProps,
  useFresh,
} from "@/components/discussion/discussion-parts";
import { SingleThreadView } from "@/components/discussion/SingleThreadView";
import { useCommentActions } from "@/components/discussion/use-comment-actions";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import {
  appendReplies,
  appendReply,
  applyDeletion,
  insertRoot,
  removeOwnComment,
  replaceComment,
  replaceThread,
  type ThreadsData,
} from "@/lib/discussion/cache";
import {
  fetchRepliesPage,
  fetchThreadOf,
  markDiscussionStale,
  useThreads,
} from "@/lib/discussion/queries";
import { useDiscussionLocation } from "@/lib/discussion/use-discussion-location";
import { useNow } from "@/lib/discussion/use-now";
import type { Comment, DiscussionSort, Thread } from "@/lib/interaction/discussion-schema";
import { btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { uniqueBy } from "@/lib/unique-by";
import { cn } from "@/lib/utils";

/// 讨论区（简报 §3.1）：文献页与经验页共用。视图以地址为准：`?comment=` 是单楼视图，否则是列表
export function DiscussionView(props: DiscussionViewProps) {
  const location = useDiscussionLocation();
  const now = useNow(props.now);
  return location.view.mode === "list" ? (
    <ThreadList {...props} now={now} sort={location.view.sort} location={location} />
  ) : (
    <SingleThreadView
      {...props}
      now={now}
      commentId={location.view.commentId}
      location={location}
    />
  );
}

export type { DiscussionViewProps };

interface ThreadListProps extends DiscussionViewProps {
  sort: DiscussionSort;
  location: DiscussionLocation;
}

/// 回复框开在哪一楼、回复谁
type ReplyTarget = { root: Comment; target: Comment };

/// 列表视图（简报 §3.1–§3.4、§3.11）：顶部编辑器、一页页的楼、「加载更多讨论」；没有评论时是空状态
function ThreadList({
  subject,
  copy,
  viewer,
  now,
  basePath,
  shareBase,
  titleId = "discussion-title",
  sort,
  location,
}: ThreadListProps) {
  const client = useQueryClient();
  const key = queryKeys.discussionThreads(subject.type, subject.key, sort);
  const query = useThreads(subject, sort);
  const [fresh, markFresh] = useFresh();
  const [announcement, setAnnouncement] = useState("");
  const [postedOffpage, setPostedOffpage] = useState<string | null>(null);
  const [reply, setReply] = useState<ReplyTarget | null>(null);
  const [replyDirty, setReplyDirty] = useState(false);
  const [pendingReply, setPendingReply] = useState<ReplyTarget | null>(null);
  const [expanding, setExpanding] = useState<string | null>(null);
  /// 点开回复框的那个「回复」按钮：回复框关掉后焦点回到它（简报 §6）
  const replyTrigger = useRef<HTMLElement | null>(null);
  const stale = () => markDiscussionStale(client, subject);

  const permalinkOf = (comment: Comment) => `${basePath}?comment=${comment.commentId}`;
  /// 把写操作的结果并进缓存。先取消这份查询还在路上的请求：它在写操作之前发出、之后才回来，带回的是写之前的
  /// 列表，会把刚并进去的结果冲掉（2026-10-28 排查「刚发表的根评论从列表消失」）
  const update = async (change: (data: ThreadsData) => ThreadsData) => {
    await client.cancelQueries({ queryKey: key });
    client.setQueryData<ThreadsData>(key, (data) => (data ? change(data) : data));
  };

  /// 把这一楼剩下的回复全部接上（回复框打开前、点「展开更多回复」）
  const expandThread = async (rootId: string, all: boolean) => {
    setExpanding(rootId);
    try {
      do {
        const thread = client
          .getQueryData<ThreadsData>(key)
          ?.pages.flatMap((page) => page.items)
          .find((item) => item.root.commentId === rootId);
        if (!thread?.hasMoreReplies) {
          break;
        }
        const page = await fetchRepliesPage(rootId, thread.repliesCursor);
        await update((data) => appendReplies(data, rootId, page));
      } while (all);
    } catch {
      // 取不到就停在已加载的部分，按钮还在，可以再点
    } finally {
      setExpanding(null);
    }
  };

  const openReply = async (thread: Thread, target: Comment) => {
    const next = { root: thread.root, target };
    if (reply && replyDirty) {
      setPendingReply(next);
      return;
    }
    replyTrigger.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    await expandThread(thread.root.commentId, true);
    setReply(next);
  };

  const { handlers, editingOf, dialogs } = useCommentActions({
    subject,
    viewer,
    now,
    shareBase,
    permalinkOf,
    openSingle: location.openSingle,
    onEdited: (comment) => {
      update((data) => replaceComment(data, comment));
      stale();
    },
    onDeleted: (comment, result) => {
      update((data) => removeOwnComment(data, comment, result));
      stale();
      if (result.removedIds.includes(comment.rootId)) {
        return;
      }
      // 楼里由它派生的字段（子回复的「回复已删除的评论」、父评论有没有回复）以服务端为准：重取这一楼
      reconcileThread(comment.rootId);
    },
  });

  /// 按服务端重取一楼替换进列表；整楼没了就去掉
  const reconcileThread = (rootId: string) => {
    fetchThreadOf(subject, rootId)
      .then((single) =>
        update((data) =>
          single
            ? replaceThread(data, single.thread)
            : applyDeletion(data, rootId, { placeholder: false, removedIds: [rootId] }),
        ),
      )
      .catch(() => {});
  };

  const actions: CommentActions = {
    ...handlers,
    onReply: (comment) => {
      const thread = query.data?.pages
        .flatMap((page) => page.items)
        .find((item) => item.root.commentId === comment.rootId);
      if (thread) {
        void openReply(thread, comment);
      }
    },
    onOpenPermalink: (comment) => location.openSingle(comment.commentId),
    onHelpfulGone: (comment) => {
      reconcileThread(comment.rootId);
      stale();
    },
  };

  const header = (withSort: boolean) => (
    <DiscussionHeader
      title={copy.title}
      titleId={titleId}
      total={query.data?.pages[0]?.total}
      sort={withSort ? sort : undefined}
      onSort={location.setSort}
    />
  );

  let body: ReactNode;
  if (!query.data && !query.isError) {
    body = <DiscussionSkeleton />;
  } else if (!query.data) {
    // 只在一份数据都没有时整块降级：翻页、重取失败时已显示的楼与正在写的内容都留着
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
  } else {
    // 「最有帮助」翻页期间数字会变，同一楼可能出现在两页里：只渲染先加载的那份（决策 D）
    const threads = uniqueBy(
      query.data.pages.flatMap((page) => page.items),
      (thread) => thread.root.commentId,
    );
    const onRootDone = async ({ comment }: { comment: Comment }) => {
      await client.cancelQueries({ queryKey: key });
      const current = client.getQueryData<ThreadsData>(key);
      if (current) {
        const result = insertRoot(current, comment, sort);
        client.setQueryData(key, result.data);
        if (result.placed === "offpage") {
          setPostedOffpage(comment.commentId);
        } else {
          setPostedOffpage(null);
          markFresh(comment.commentId);
        }
      }
      setAnnouncement("已发表");
      stale();
    };
    if (threads.length === 0) {
      return (
        <div className="flex max-w-190 flex-col gap-5">
          {header(false)}
          <Announcer text={announcement} />
          <DiscussionEmpty
            copy={copy}
            composer={
              <CommentComposer
                subject={subject}
                mode={{ kind: "root" }}
                placeholder={copy.emptyPlaceholder}
                viewer={viewer}
                onDone={onRootDone}
                onCancel={() => {}}
              />
            }
          />
        </div>
      );
    }
    body = (
      <>
        <CommentComposer
          subject={subject}
          mode={{ kind: "root" }}
          placeholder={copy.composerPlaceholder}
          viewer={viewer}
          collapsible
          onDone={onRootDone}
          onCancel={() => {}}
        />
        {postedOffpage && (
          <p className="flex items-center gap-2 text-sm text-status-success-ink">
            <CircleCheck className="size-4" aria-hidden="true" />
            已发表。按正序排在最后，
            <a
              href={`${basePath}?comment=${postedOffpage}`}
              onClick={(event) => {
                event.preventDefault();
                location.openSingle(postedOffpage);
              }}
              className="font-medium text-link hover:text-link-hover"
            >
              去看看
            </a>
          </p>
        )}
        <div className="flex flex-col">
          {threads.map((thread) => {
            const open = reply?.root.commentId === thread.root.commentId;
            return (
              <CommentThread
                key={thread.root.commentId}
                thread={thread}
                now={now}
                permalinkOf={permalinkOf}
                actions={actions}
                freshId={fresh}
                editingOf={editingOf}
                expanding={expanding === thread.root.commentId}
                onExpand={() => void expandThread(thread.root.commentId, false)}
                replyBox={
                  open && reply ? (
                    <div data-composer className="flex flex-col gap-2">
                      {pendingReply && (
                        <DiscardReplyConfirm
                          onDiscard={() => {
                            const next = pendingReply;
                            setPendingReply(null);
                            setReplyDirty(false);
                            setReply(null);
                            void expandThread(next.root.commentId, true).then(() => setReply(next));
                          }}
                          onKeep={() => setPendingReply(null)}
                        />
                      )}
                      <CommentComposer
                        key={`${reply.root.commentId}-${reply.target.commentId}`}
                        subject={subject}
                        mode={{ kind: "reply", root: reply.root, target: reply.target }}
                        placeholder=""
                        viewer={viewer}
                        autoFocus
                        onDirtyChange={setReplyDirty}
                        onDone={({ comment }) => {
                          update((data) => appendReply(data, reply.root.commentId, comment));
                          setReply(null);
                          setReplyDirty(false);
                          markFresh(comment.commentId);
                          setAnnouncement("已回复");
                          stale();
                        }}
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
            );
          })}
        </div>
        {query.hasNextPage && (
          <button
            type="button"
            disabled={query.isFetchingNextPage}
            onClick={() => void query.fetchNextPage()}
            className={cn(btnSecondary, "w-full")}
          >
            {query.isFetchingNextPage ? "加载中…" : "加载更多讨论"}
          </button>
        )}
        {query.isFetchNextPageError && (
          <p role="alert" className="text-center text-sm text-status-danger">
            没有加载出来，请重试。
          </p>
        )}
      </>
    );
  }

  return (
    <div className="flex max-w-190 flex-col gap-5">
      {header(query.data?.pages.some((page) => page.items.length > 0) ?? false)}
      <Announcer text={announcement} />
      {body}
      {dialogs}
    </div>
  );
}
