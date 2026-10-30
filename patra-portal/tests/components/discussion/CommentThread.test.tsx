import { screen, within } from "@testing-library/react";
import { makeComment, makeReply, makeThread } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { CommentActions } from "@/components/discussion/CommentItem";
import { CommentThread } from "@/components/discussion/CommentThread";
import type { Thread } from "@/lib/interaction/discussion-schema";

const NOW = Date.parse("2026-10-30T12:00:00Z");
const LAST = "2026-10-30T10:00:00Z";

function actions(): CommentActions {
  return {
    onReply: vi.fn(),
    onShowConversation: vi.fn(),
    onShowHistory: vi.fn(),
    onEdit: vi.fn(),
    onDelete: vi.fn(),
    onCopyLink: vi.fn(),
    onOpenPermalink: vi.fn(),
    onHelpfulGone: vi.fn(),
  };
}

function renderThread(thread: Thread, extra: { expanding?: boolean } = {}) {
  return renderDiscussion(
    <CommentThread
      thread={thread}
      now={NOW}
      permalinkOf={(comment) => `/papers/11?comment=${comment.commentId}`}
      actions={actions()}
      onExpand={vi.fn()}
      {...extra}
    />,
  );
}

const replies = (...ids: string[]) => ids.map((id) => makeReply(id));

describe("CommentThread · 楼内统计", () => {
  it("should_show_stats_on_root_only", () => {
    renderThread(makeThread({ replies: replies("2"), replyCount: 1, lastReplyAt: LAST }));
    const [root, reply] = screen.getAllByRole("article");
    expect(root).toHaveTextContent("1 条回复 · 最后回复 2 小时前");
    expect(reply).not.toHaveTextContent("条回复");
  });

  it("should_show_stats_beside_deleted_root_placeholder", () => {
    renderThread(
      makeThread({
        root: makeComment({ deleted: true, body: null, author: null }),
        replies: replies("2"),
        replyCount: 1,
        lastReplyAt: LAST,
      }),
    );
    const placeholder = screen.getByText("该评论已删除").parentElement;
    expect(placeholder).toHaveTextContent("该评论已删除1 条回复 · 最后回复 2 小时前");
  });

  it("should_not_show_stats_without_replies", () => {
    renderThread(makeThread());
    expect(screen.queryByText(/条回复/)).not.toBeInTheDocument();
  });

  it("should_leave_no_empty_slot_in_action_row_without_replies", () => {
    renderThread(makeThread());
    const reply = screen.getByRole("button", { name: "回复" });
    // 窄屏下空的槽位会占满一行、多出一行空白
    expect(reply.parentElement?.lastElementChild).toBe(reply);
  });
});

describe("CommentThread · 展开其余回复", () => {
  const expand = () => screen.getByRole("button", { name: /展开|加载中/ });

  it("should_say_how_many_replies_remain", () => {
    renderThread(
      makeThread({
        replies: replies("2", "3", "4"),
        hasMoreReplies: true,
        replyCount: 5,
        lastReplyAt: LAST,
      }),
    );
    expect(expand()).toHaveTextContent("展开其余 2 条回复");
  });

  it("should_count_only_loaded_replies_not_deleted", () => {
    renderThread(
      makeThread({
        replies: [
          makeReply("2"),
          makeReply("3", "1", { deleted: true, body: null }),
          makeReply("4"),
        ],
        hasMoreReplies: true,
        replyCount: 4,
        lastReplyAt: LAST,
      }),
    );
    expect(expand()).toHaveTextContent("展开其余 2 条回复");
  });

  it("should_say_expand_all_when_only_placeholders_remain", () => {
    renderThread(
      makeThread({
        replies: replies("2", "3", "4"),
        hasMoreReplies: true,
        replyCount: 3,
        lastReplyAt: LAST,
      }),
    );
    expect(expand()).toHaveTextContent(/^展开全部回复$/);
  });

  it("should_say_loading_while_expanding", () => {
    renderThread(
      makeThread({
        replies: replies("2", "3", "4"),
        hasMoreReplies: true,
        replyCount: 5,
        lastReplyAt: LAST,
      }),
      { expanding: true },
    );
    expect(expand()).toHaveTextContent("加载中…");
    expect(within(expand()).queryByText(/展开/)).not.toBeInTheDocument();
  });
});
