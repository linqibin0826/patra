import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeCard, makeComment, makeReply } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { type CommentActions, CommentItem } from "@/components/discussion/CommentItem";
import type { Comment } from "@/lib/interaction/discussion-schema";

const NOW = Date.parse("2026-10-27T06:20:00Z");

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

function renderItem(comment: Comment, extra: Partial<Parameters<typeof CommentItem>[0]> = {}) {
  const handlers = actions();
  const view = renderDiscussion(
    <CommentItem
      comment={comment}
      level={comment.parentId ? "reply" : "root"}
      permalink={`/papers/11?comment=${comment.commentId}`}
      now={NOW}
      actions={handlers}
      {...extra}
    />,
  );
  return { ...view, handlers };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("CommentItem 署名", () => {
  it("可见作者：昵称链主页 + @用户名，时间是永久链接", () => {
    renderItem(makeComment());
    expect(screen.getByRole("link", { name: "陈宇" })).toHaveAttribute("href", "/u/chen-yu");
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    const time = screen.getByRole("link", { name: "3 天前" });
    expect(time).toHaveAttribute("href", "/papers/11?comment=1");
    expect(time).toHaveAttribute("title", "2026-10-24 14:20");
  });

  it("点时间：走客户端切到单楼视图", async () => {
    const { handlers } = renderItem(makeComment());
    await userEvent.setup().click(screen.getByRole("link", { name: "3 天前" }));
    expect(handlers.onOpenPermalink).toHaveBeenCalled();
  });

  it("主页不可见：昵称不是链接，没有 @用户名", () => {
    renderItem(makeComment({ author: makeCard({ username: null, profileVisible: false }) }));
    expect(screen.queryByRole("link", { name: "陈宇" })).toBeNull();
    expect(screen.getByText("陈宇")).toBeInTheDocument();
    expect(screen.queryByText(/^@/)).toBeNull();
  });

  it("匿名作者本人看：匿名作者（你）+ 作者标记", () => {
    renderItem(makeComment({ anonymous: true, mine: true, isOwner: true }));
    expect(screen.getByText("匿名作者（你）")).toBeInTheDocument();
    expect(screen.getByText("作者")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /陈宇/ })).toBeNull();
  });

  it("已注销用户", () => {
    renderItem(makeComment({ author: null }));
    expect(screen.getByText("已注销用户")).toBeInTheDocument();
  });

  it("已编辑：点开修改历史", async () => {
    const { handlers } = renderItem(makeComment({ edited: true }));
    await userEvent.setup().click(screen.getByRole("button", { name: "已编辑" }));
    expect(handlers.onShowHistory).toHaveBeenCalled();
  });

  it("回复 @X 与查看对话", async () => {
    const reply = makeReply("5", "1", {
      replyTo: {
        deleted: false,
        anonymous: false,
        author: makeCard({ userId: "9", username: "zhou-min" }),
      },
    });
    const { handlers } = renderItem(reply);
    expect(screen.getByRole("link", { name: "@zhou-min" })).toHaveAttribute("href", "/u/zhou-min");
    await userEvent.setup().click(screen.getByRole("button", { name: "查看对话" }));
    expect(handlers.onShowConversation).toHaveBeenCalled();
  });

  it("自己回复自己：不显示回复 @X，也不出查看对话", () => {
    renderItem(
      makeReply("5", "1", {
        replyTo: { deleted: false, anonymous: false, author: makeCard() },
      }),
    );
    expect(screen.queryByText(/回复 @/)).toBeNull();
    expect(screen.queryByRole("link", { name: "@chen-yu" })).toBeNull();
    expect(screen.queryByRole("button", { name: "查看对话" })).toBeNull();
  });

  it("没有回复对象时不出查看对话", () => {
    renderItem(makeReply("5"));
    expect(screen.queryByRole("button", { name: "查看对话" })).toBeNull();
  });

  it("回复按钮", async () => {
    const { handlers } = renderItem(makeComment());
    await userEvent.setup().click(screen.getByRole("button", { name: "回复" }));
    expect(handlers.onReply).toHaveBeenCalled();
  });
});

describe("CommentItem 占位与菜单", () => {
  it("占位：只有一行文字，没有操作", () => {
    const { container } = renderItem(
      makeComment({ deleted: true, author: null, body: null, hasReplies: true }),
    );
    expect(screen.getByText("该评论已删除")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
    expect(container.querySelectorAll("a")).toHaveLength(0);
  });

  it("本人：复制链接、编辑、删除", async () => {
    const { handlers } = renderItem(makeComment({ mine: true }));
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "评论操作" }));
    const menu = await screen.findByRole("menu");
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((item) => item.textContent),
    ).toEqual(["复制链接", "编辑", "删除"]);
    await user.click(within(menu).getByRole("menuitem", { name: "删除" }));
    expect(handlers.onDelete).toHaveBeenCalled();
  });

  it("别人：只有复制链接", async () => {
    renderItem(makeComment());
    await userEvent.setup().click(screen.getByRole("button", { name: "评论操作" }));
    await screen.findByRole("menu");
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["复制链接"]);
  });

  it("单楼视图的目标：aria-current", () => {
    const { container } = renderItem(makeComment(), { current: true });
    expect(container.querySelector('[aria-current="true"]')).not.toBeNull();
  });
});

describe("CommentItem 长评论", () => {
  it("超过 12 行时折叠，可展开、收起", async () => {
    class TallObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe(target: Element) {
        Object.defineProperty(target, "scrollHeight", { value: 13 * 24, configurable: true });
        this.callback([], this as unknown as ResizeObserver);
      }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal("ResizeObserver", TallObserver);
    renderItem(makeComment({ body: "很长" }));
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "展开全文" }));
    expect(screen.getByRole("button", { name: "收起" })).toBeInTheDocument();
  });
});

describe("CommentItem 有帮助", () => {
  it("操作行第一个是有帮助，排在回复前", () => {
    renderItem(makeComment({ helpfulCount: 2 }));
    const names = screen
      .getAllByRole("button")
      .map((button) => button.getAttribute("aria-label") ?? button.textContent);
    const helpful = names.findIndex((name) => name?.startsWith("有帮助"));
    expect(helpful).toBeGreaterThanOrEqual(0);
    expect(helpful).toBeLessThan(names.indexOf("回复"));
    expect(screen.getByRole("button", { name: "有帮助，2 人觉得有帮助" })).toBeVisible();
  });

  it("自己的评论只显示人数", () => {
    renderItem(makeComment({ mine: true, helpfulCount: 2 }));
    expect(screen.queryByRole("button", { name: /^有帮助/ })).toBeNull();
    expect(screen.getByText("2 人觉得有帮助")).toBeVisible();
  });

  it("占位没有有帮助与数字", () => {
    renderItem(makeComment({ deleted: true, helpfulCount: 0, helpful: null }));
    expect(screen.queryByText(/有帮助/)).toBeNull();
  });

  it("只读与原地编辑时没有有帮助", () => {
    renderItem(makeComment({ helpfulCount: 1 }), { readOnly: true });
    expect(screen.queryByText(/有帮助/)).toBeNull();
  });
});

describe("CommentItem · 操作行右端", () => {
  it("should_render_trailing_in_action_row", () => {
    renderItem(makeComment(), { trailing: <span>楼内统计</span> });
    const reply = screen.getByRole("button", { name: "回复" });
    expect(reply.parentElement).toContainElement(screen.getByText("楼内统计"));
  });

  it("should_render_trailing_after_deleted_placeholder", () => {
    renderItem(makeComment({ deleted: true, body: null, author: null }), {
      trailing: <span>楼内统计</span>,
    });
    expect(screen.getByText("该评论已删除").parentElement).toContainElement(
      screen.getByText("楼内统计"),
    );
  });

  it("should_hide_trailing_while_editing", () => {
    renderItem(makeComment(), { trailing: <span>楼内统计</span>, editing: <p>编辑器</p> });
    expect(screen.queryByText("楼内统计")).not.toBeInTheDocument();
  });
});
