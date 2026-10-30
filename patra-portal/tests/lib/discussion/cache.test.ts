import { makeComment, makeReply, makeThread, makeThreadPage } from "@tests/fixtures/discussion";
import { describe, expect, it } from "vitest";
import {
  appendReplies,
  appendReply,
  appendReplySingle,
  applyDeletion,
  applyDeletionSingle,
  insertRoot,
  removeOwnComment,
  removeOwnCommentSingle,
  replaceComment,
  replaceCommentSingle,
  replaceThread,
  type ThreadsData,
  toPlaceholder,
} from "@/lib/discussion/cache";

function data(pages: { ids: string[]; next: string | null }[]): ThreadsData {
  return {
    pages: pages.map((page) => ({
      items: page.ids.map((id) => makeThread({ root: makeComment({ commentId: id, rootId: id }) })),
      nextCursor: page.next,
      total: 0,
    })),
    pageParams: pages.map((_, index) => (index === 0 ? null : `c${index}`)),
  };
}

const rootIds = (d: ThreadsData) =>
  d.pages.flatMap((page) => page.items.map((t) => t.root.commentId));

/// 冻结整棵对象：纯函数改了入参就会抛错
function frozen<T>(value: T): T {
  if (value && typeof value === "object") {
    for (const child of Object.values(value)) frozen(child);
    Object.freeze(value);
  }
  return value;
}

describe("insertRoot", () => {
  const fresh = makeComment({ commentId: "9", rootId: "9" });

  it("最新：插到第一页最前", () => {
    const result = insertRoot(frozen(data([{ ids: ["1", "2"], next: null }])), fresh, "latest");
    expect(result.placed).toBe("top");
    expect(rootIds(result.data)).toEqual(["9", "1", "2"]);
  });

  it("最有帮助：同最新，插到第一页最前", () => {
    const result = insertRoot(frozen(data([{ ids: ["1", "2"], next: "c1" }])), fresh, "helpful");
    expect(result.placed).toBe("top");
    expect(rootIds(result.data)).toEqual(["9", "1", "2"]);
  });

  it("正序且已到最后一页：接到末尾", () => {
    const result = insertRoot(
      frozen(
        data([
          { ids: ["1"], next: "c1" },
          { ids: ["2"], next: null },
        ]),
      ),
      fresh,
      "asc",
    );
    expect(result.placed).toBe("bottom");
    expect(rootIds(result.data)).toEqual(["1", "2", "9"]);
  });

  it("正序且还有下一页：不动", () => {
    const before = frozen(data([{ ids: ["1"], next: "c1" }]));
    const result = insertRoot(before, fresh, "asc");
    expect(result.placed).toBe("offpage");
    expect(rootIds(result.data)).toEqual(["1"]);
  });
});

describe("回复、替换、展开", () => {
  it("appendReply 接到这一楼末尾，其他楼不变", () => {
    const before = frozen(data([{ ids: ["1", "2"], next: null }]));
    const after = appendReply(before, "2", makeReply("5", "2"));
    expect(after.pages[0]?.items[1]?.replies.map((c) => c.commentId)).toEqual(["5"]);
    expect(after.pages[0]?.items[0]).toBe(before.pages[0]?.items[0]);
  });

  it("replaceComment 能替换根评论与回复", () => {
    const before = frozen({
      pages: [{ items: [makeThread({ replies: [makeReply("5")] })], nextCursor: null, total: 0 }],
      pageParams: [null],
    });
    const edited = replaceComment(
      replaceComment(before, makeComment({ body: "改过", edited: true })),
      makeReply("5", "1", { body: "回复改过" }),
    );
    expect(edited.pages[0]?.items[0]?.root.body).toBe("改过");
    expect(edited.pages[0]?.items[0]?.replies[0]?.body).toBe("回复改过");
  });

  it("appendReplies 接上并更新游标", () => {
    const before = frozen({
      pages: [
        {
          items: [
            makeThread({ replies: [makeReply("2")], hasMoreReplies: true, repliesCursor: "r1" }),
          ],
          nextCursor: null,
          total: 0,
        },
      ],
      pageParams: [null],
    });
    const after = appendReplies(before, "1", { items: [makeReply("3")], nextCursor: null });
    const thread = after.pages[0]?.items[0];
    expect(thread?.replies.map((c) => c.commentId)).toEqual(["2", "3"]);
    expect(thread?.hasMoreReplies).toBe(false);
    expect(thread?.repliesCursor).toBeNull();
  });

  it("replaceThread 按根评论 ID 整楼替换", () => {
    const before = frozen(data([{ ids: ["1", "2"], next: null }]));
    const after = replaceThread(
      before,
      makeThread({
        root: makeComment({ commentId: "2", rootId: "2" }),
        replies: [makeReply("7", "2")],
      }),
    );
    expect(after.pages[0]?.items[1]?.replies).toHaveLength(1);
  });
});

describe("applyDeletion", () => {
  const withReplies = () =>
    frozen({
      pages: [
        {
          items: [
            makeThread({ replies: [makeReply("2"), makeReply("3")] }),
            makeThread({ root: makeComment({ commentId: "4", rootId: "4" }) }),
          ],
          nextCursor: null,
          total: 0,
        },
      ],
      pageParams: [null],
    });

  it("占位：保留位置，清掉作者与正文", () => {
    const after = applyDeletion(withReplies(), "1", { placeholder: true, removedIds: [] });
    const root = after.pages[0]?.items[0]?.root;
    expect(root).toMatchObject({
      commentId: "1",
      deleted: true,
      body: null,
      author: null,
      hasReplies: true,
    });
    expect(after.pages[0]?.items[0]?.replies).toHaveLength(2);
  });

  it("removedIds 含回复：从楼里去掉", () => {
    const after = applyDeletion(withReplies(), "3", { placeholder: false, removedIds: ["3"] });
    expect(after.pages[0]?.items[0]?.replies.map((c) => c.commentId)).toEqual(["2"]);
  });

  it("removedIds 含根评论：整楼消失", () => {
    const after = applyDeletion(withReplies(), "4", { placeholder: false, removedIds: ["4"] });
    expect(rootIds(after)).toEqual(["1"]);
  });
});

describe("单楼", () => {
  const single = () => frozen({ targetId: "2", thread: makeThread({ replies: [makeReply("2")] }) });

  it("删根评论（整条去掉）→ missing", () => {
    expect(
      applyDeletionSingle(single(), "1", { placeholder: false, removedIds: ["2", "1"] }),
    ).toEqual({
      missing: true,
    });
  });

  it("回复与替换", () => {
    const appended = appendReplySingle(single(), makeReply("8"));
    expect("thread" in appended && appended.thread.replies.map((c) => c.commentId)).toEqual([
      "2",
      "8",
    ]);
    const replaced = replaceCommentSingle(single(), makeReply("2", "1", { body: "新" }));
    expect("thread" in replaced && replaced.thread.replies[0]?.body).toBe("新");
  });

  it("missing 原样返回", () => {
    expect(appendReplySingle({ missing: true }, makeReply("8"))).toEqual({ missing: true });
  });
});

describe("toPlaceholder", () => {
  it("只留结构字段", () => {
    expect(toPlaceholder(makeComment({ mine: true, edited: true, mentions: [] }))).toMatchObject({
      deleted: true,
      author: null,
      body: null,
      mine: false,
      edited: false,
      replyTo: null,
      hasReplies: true,
    });
  });

  it("占位不带有帮助数、没有「我点过没有」", () => {
    expect(toPlaceholder(makeComment({ helpfulCount: 5, helpful: true }))).toMatchObject({
      helpfulCount: 0,
      helpful: null,
    });
  });
});

describe("公开计数随本人发表、删除变化", () => {
  const ROOT = "1";
  const OLD = "2026-10-29T08:00:00Z";
  const NEW = "2026-10-30T09:00:00Z";

  /// 一页一楼：根评论 1 带两条回复 2、3，楼内 2 条，对象下共 3 条；`next` 给了表示后面还有没加载的页
  function counted(next: string | null = null): ThreadsData {
    return frozen({
      pages: [
        makeThreadPage(
          [
            makeThread({
              replies: [makeReply("2"), makeReply("3")],
              replyCount: 2,
              lastReplyAt: OLD,
            }),
          ],
          { total: 3, nextCursor: next },
        ),
      ],
      pageParams: [null],
    });
  }
  const thread = (data: ThreadsData) => data.pages[0]?.items.find((t) => t.root.commentId === ROOT);
  const total = (data: ThreadsData) => data.pages[0]?.total;

  it.each(["asc", "latest", "helpful"] as const)(
    "should_add_one_to_total_when_root_posted_in_%s",
    (sort) => {
      const { data } = insertRoot(counted(), makeComment({ commentId: "9", rootId: "9" }), sort);
      expect(total(data)).toBe(4);
      expect(
        data.pages.flatMap((p) => p.items).find((t) => t.root.commentId === "9"),
      ).toMatchObject({ replyCount: 0, lastReplyAt: null });
    },
  );

  it("should_add_one_to_total_when_root_posted_offpage", () => {
    const result = insertRoot(counted("c1"), makeComment({ commentId: "9", rootId: "9" }), "asc");
    expect(result.placed).toBe("offpage");
    expect(total(result.data)).toBe(4);
  });

  it("should_count_reply_and_move_last_reply_time_when_reply_posted", () => {
    const after = appendReply(counted(), ROOT, makeReply("4", ROOT, { createdAt: NEW }));
    expect(thread(after)).toMatchObject({ replyCount: 3, lastReplyAt: NEW });
    expect(total(after)).toBe(4);
  });

  it("should_uncount_reply_and_keep_last_reply_time_when_own_reply_removed", () => {
    const after = removeOwnComment(counted(), makeReply("3"), {
      placeholder: false,
      removedIds: ["3"],
    });
    expect(thread(after)?.replies.map((c) => c.commentId)).toEqual(["2"]);
    expect(thread(after)).toMatchObject({ replyCount: 1, lastReplyAt: OLD });
    expect(total(after)).toBe(2);
  });

  it("should_uncount_own_reply_left_as_placeholder", () => {
    const after = removeOwnComment(counted(), makeReply("2"), {
      placeholder: true,
      removedIds: [],
    });
    expect(thread(after)?.replyCount).toBe(1);
    expect(total(after)).toBe(2);
  });

  it("should_uncount_own_reply_not_loaded_in_this_thread", () => {
    const after = removeOwnComment(counted(), makeReply("8"), {
      placeholder: false,
      removedIds: ["8"],
    });
    expect(thread(after)?.replyCount).toBe(1);
    expect(total(after)).toBe(2);
  });

  it("should_clear_last_reply_time_when_last_reply_removed", () => {
    const one = frozen({
      pages: [
        makeThreadPage(
          [makeThread({ replies: [makeReply("2")], replyCount: 1, lastReplyAt: OLD })],
          { total: 2 },
        ),
      ],
      pageParams: [null],
    });
    const after = removeOwnComment(one, makeReply("2"), { placeholder: false, removedIds: ["2"] });
    expect(thread(after)).toMatchObject({ replyCount: 0, lastReplyAt: null });
  });

  it("should_only_uncount_total_when_own_root_left_as_placeholder", () => {
    const after = removeOwnComment(counted(), makeComment(), { placeholder: true, removedIds: [] });
    expect(thread(after)).toMatchObject({ replyCount: 2, lastReplyAt: OLD });
    expect(thread(after)?.root.deleted).toBe(true);
    expect(total(after)).toBe(2);
  });

  it("should_uncount_total_once_when_own_thread_removed", () => {
    const after = removeOwnComment(counted(), makeComment(), {
      placeholder: false,
      removedIds: [ROOT],
    });
    expect(thread(after)).toBeUndefined();
    expect(total(after)).toBe(2);
  });

  it("should_not_count_when_thread_dropped_by_reconciliation", () => {
    const after = applyDeletion(counted(), ROOT, { placeholder: false, removedIds: [ROOT] });
    expect(total(after)).toBe(3);
  });

  it("should_not_go_below_zero", () => {
    const empty = frozen({
      pages: [makeThreadPage([makeThread({ replyCount: 0 })], { total: 0 })],
      pageParams: [null],
    });
    const after = removeOwnComment(empty, makeComment(), {
      placeholder: false,
      removedIds: [ROOT],
    });
    expect(total(after)).toBe(0);
  });

  it("should_take_server_counts_when_thread_reconciled", () => {
    const removed = removeOwnComment(counted(), makeReply("3"), {
      placeholder: false,
      removedIds: ["3"],
    });
    const server = makeThread({
      replies: [makeReply("2")],
      replyCount: 1,
      lastReplyAt: "2026-10-28T00:00:00Z",
    });
    expect(thread(replaceThread(removed, server))).toMatchObject({
      replyCount: 1,
      lastReplyAt: "2026-10-28T00:00:00Z",
    });
  });

  it("should_count_in_single_thread_view", () => {
    const single = frozen({
      targetId: ROOT,
      thread: makeThread({ replies: [makeReply("2")], replyCount: 1, lastReplyAt: OLD }),
    });
    const added = appendReplySingle(single, makeReply("4", ROOT, { createdAt: NEW }));
    expect("thread" in added && added.thread).toMatchObject({ replyCount: 2, lastReplyAt: NEW });
    const removed = removeOwnCommentSingle(single, makeReply("2"), {
      placeholder: false,
      removedIds: ["2"],
    });
    expect("thread" in removed && removed.thread).toMatchObject({
      replyCount: 0,
      lastReplyAt: null,
      replies: [],
    });
  });
});
