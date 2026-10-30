import type { Card, Comment, Thread } from "@/lib/interaction/discussion-schema";

/// 作者名片：默认是主页可见的正常账号陈宇
export function makeCard(overrides: Partial<Card> = {}): Card {
  return {
    userId: "7",
    username: "chen-yu",
    displayName: "陈宇",
    avatarUrl: null,
    avatarPalette: 3,
    state: "ACTIVE",
    profileVisible: true,
    ...overrides,
  };
}

/// 一条根评论：默认 ID 1、陈宇发的、正文「每组只有 47 例」
export function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    commentId: "1",
    rootId: "1",
    parentId: null,
    deleted: false,
    author: makeCard(),
    anonymous: false,
    isOwner: false,
    mine: false,
    replyTo: null,
    body: "每组只有 47 例",
    mentions: [],
    hasReplies: false,
    createdAt: "2026-10-24T06:20:00Z",
    edited: false,
    helpfulCount: 0,
    helpful: false,
    ...overrides,
  };
}

/// 一楼的回复：`id` 是回复的 ID，挂在根评论 `rootId` 下
export function makeReply(id: string, rootId = "1", overrides: Partial<Comment> = {}): Comment {
  return makeComment({ commentId: id, rootId, parentId: rootId, ...overrides });
}

/// 一楼：根评论 + 回复
export function makeThread(overrides: Partial<Thread> = {}): Thread {
  return {
    root: makeComment(),
    replies: [],
    hasMoreReplies: false,
    repliesCursor: null,
    ...overrides,
  };
}
