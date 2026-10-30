import type { InfiniteData } from "@tanstack/react-query";
import type {
  Comment,
  DeleteResult,
  DiscussionSort,
  Replies,
  SingleThread,
  Thread,
  ThreadPage,
} from "@/lib/interaction/discussion-schema";

/// 讨论区列表在缓存里的形状：一页页的楼，页参数是游标（第一页为 null）
export type ThreadsData = InfiniteData<ThreadPage, string | null>;

/// 单楼视图在缓存里的形状：目标不存在（被删且没有回复、或不在这个对象下）时是 `{ missing: true }`
export type SingleData = SingleThread | { missing: true };

/// 写操作的结果并进缓存（spec 4.5）：都是纯函数，不改入参；不乐观，拿到服务端的返回才调用。
/// 有帮助例外（乐观，按目标 ID 写回所有副本），见 `lib/helpful/cache.ts`

/// 删成占位：只留位置与结构字段（后端占位条目的形状）
export function toPlaceholder(comment: Comment): Comment {
  return {
    ...comment,
    deleted: true,
    author: null,
    anonymous: false,
    isOwner: false,
    mine: false,
    replyTo: null,
    body: null,
    mentions: [],
    hasReplies: true,
    edited: false,
    helpfulCount: 0,
    helpful: null,
  };
}

function mapThreads(data: ThreadsData, fn: (thread: Thread) => Thread | null): ThreadsData {
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: page.items.flatMap((thread) => {
        const next = fn(thread);
        return next ? [next] : [];
      }),
    })),
  };
}

/// 发表根评论：`latest`、`helpful` 插到最前（刷新后「最有帮助」按数字排，新评论是 0）；`asc` 已加载到最后一页时
/// 接到末尾；否则不动（编辑器下方给「去看看」）
export function insertRoot(
  data: ThreadsData,
  comment: Comment,
  sort: DiscussionSort,
): { data: ThreadsData; placed: "top" | "bottom" | "offpage" } {
  const thread: Thread = { root: comment, replies: [], hasMoreReplies: false, repliesCursor: null };
  const pages = data.pages;
  if (sort !== "asc") {
    const [first, ...rest] = pages;
    const head = first ?? { items: [], nextCursor: null };
    return {
      data: { ...data, pages: [{ ...head, items: [thread, ...head.items] }, ...rest] },
      placed: "top",
    };
  }
  const last = pages.at(-1);
  if (last && last.nextCursor !== null) {
    return { data, placed: "offpage" };
  }
  const tail = last ?? { items: [], nextCursor: null };
  return {
    data: {
      ...data,
      pages: [...pages.slice(0, -1), { ...tail, items: [...tail.items, thread] }],
      pageParams: data.pageParams.length > 0 ? data.pageParams : [null],
    },
    placed: "bottom",
  };
}

function withReply(thread: Thread, comment: Comment): Thread {
  return { ...thread, replies: [...thread.replies, comment] };
}

/// 新回复接到这一楼最后
export function appendReply(data: ThreadsData, rootId: string, comment: Comment): ThreadsData {
  return mapThreads(data, (thread) =>
    thread.root.commentId === rootId ? withReply(thread, comment) : thread,
  );
}

function replaceIn(thread: Thread, comment: Comment): Thread {
  if (thread.root.commentId === comment.commentId) {
    return { ...thread, root: comment };
  }
  return thread.replies.some((reply) => reply.commentId === comment.commentId)
    ? {
        ...thread,
        replies: thread.replies.map((reply) =>
          reply.commentId === comment.commentId ? comment : reply,
        ),
      }
    : thread;
}

/// 编辑后按 ID 替换（根评论或回复）
export function replaceComment(data: ThreadsData, comment: Comment): ThreadsData {
  return mapThreads(data, (thread) => replaceIn(thread, comment));
}

/// 删除：留占位就换成占位；整条去掉的按 `removedIds` 去掉，根评论在其中整楼消失。返回 null 表示整楼没了
function deleteIn(thread: Thread, commentId: string, result: DeleteResult): Thread | null {
  if (result.placeholder) {
    if (thread.root.commentId === commentId) {
      return { ...thread, root: toPlaceholder(thread.root) };
    }
    return {
      ...thread,
      replies: thread.replies.map((reply) =>
        reply.commentId === commentId ? toPlaceholder(reply) : reply,
      ),
    };
  }
  const removed = new Set(result.removedIds);
  if (removed.has(thread.root.commentId)) {
    return null;
  }
  return { ...thread, replies: thread.replies.filter((reply) => !removed.has(reply.commentId)) };
}

export function applyDeletion(
  data: ThreadsData,
  commentId: string,
  result: DeleteResult,
): ThreadsData {
  return mapThreads(data, (thread) => deleteIn(thread, commentId, result));
}

/// 「展开更多回复」：接上取到的一批，更新游标
export function appendReplies(data: ThreadsData, rootId: string, page: Replies): ThreadsData {
  return mapThreads(data, (thread) =>
    thread.root.commentId === rootId
      ? {
          ...thread,
          replies: [...thread.replies, ...page.items],
          hasMoreReplies: page.nextCursor !== null,
          repliesCursor: page.nextCursor,
        }
      : thread,
  );
}

/// 删除后对账：用单楼接口重取的整楼替换（这一楼随之完整展开）
export function replaceThread(data: ThreadsData, thread: Thread): ThreadsData {
  return mapThreads(data, (current) =>
    current.root.commentId === thread.root.commentId ? thread : current,
  );
}

/// 单楼视图里的回复
export function appendReplySingle(data: SingleData, comment: Comment): SingleData {
  return "missing" in data ? data : { ...data, thread: withReply(data.thread, comment) };
}

/// 单楼视图里的编辑
export function replaceCommentSingle(data: SingleData, comment: Comment): SingleData {
  return "missing" in data ? data : { ...data, thread: replaceIn(data.thread, comment) };
}

/// 单楼视图里的删除：整楼没了就是 missing
export function applyDeletionSingle(
  data: SingleData,
  commentId: string,
  result: DeleteResult,
): SingleData {
  if ("missing" in data) {
    return data;
  }
  const thread = deleteIn(data.thread, commentId, result);
  return thread ? { ...data, thread } : { missing: true };
}
