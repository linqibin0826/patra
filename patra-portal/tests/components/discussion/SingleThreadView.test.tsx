import type { QueryClient } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeComment, makeReply, makeThread } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DiscussionView } from "@/components/discussion/DiscussionView";
import { PAPER_DISCUSSION_COPY } from "@/lib/discussion/copy";
import { queryKeys } from "@/lib/query-keys";

let params = new URLSearchParams("comment=3");
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/papers/1",
  useSearchParams: () => params,
}));
vi.mock("@/lib/interaction/discussion-actions", () => ({
  publishComment: vi.fn(),
  editComment: vi.fn(),
  deleteComment: vi.fn(),
}));

const SEED = {
  status: "authenticated",
  emailVerified: true,
  email: "a@b.c",
  avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
} as const;

function renderSingle(data: unknown, commentId = "3") {
  params = new URLSearchParams(`comment=${commentId}`);
  return renderDiscussion(
    <DiscussionView
      subject={{ type: "publication", key: "1" }}
      copy={PAPER_DISCUSSION_COPY}
      viewer={SEED}
      now={Date.parse("2026-10-27T06:20:00Z")}
      basePath="/papers/1"
      shareBase="http://patra.test/papers/1"
    />,
    {
      seed: (client: QueryClient) =>
        client.setQueryData(queryKeys.discussionSingle("publication", "1", commentId), data),
    },
  );
}

beforeEach(() => {
  Element.prototype.scrollIntoView = vi.fn();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json([])),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

const SINGLE = {
  targetId: "3",
  thread: makeThread({
    replies: [makeReply("2"), makeReply("3", "1", { body: "目标这条" }), makeReply("4")],
  }),
};

describe("单楼视图", () => {
  it("提示条、整楼全部展开、目标高亮、没有排序", () => {
    renderSingle(SINGLE);
    expect(screen.getByText("你正在看讨论中的一楼。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "查看全部讨论" })).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(4);
    expect(document.querySelector('[aria-current="true"]')).toHaveTextContent("目标这条");
    expect(screen.queryByRole("button", { name: "最新" })).toBeNull();
    expect(screen.getByRole("button", { name: "回复这一楼…" })).toBeInTheDocument();
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
  });

  it("目标不存在：提示，不显示列表", () => {
    renderSingle({ missing: true });
    expect(screen.getByText("这条评论已删除，或者不在这里的讨论中。")).toBeInTheDocument();
    expect(screen.queryAllByRole("article")).toHaveLength(0);
  });

  it("目标是占位：照常显示一楼，占位高亮", () => {
    renderSingle(
      {
        targetId: "1",
        thread: makeThread({
          root: makeComment({ deleted: true, author: null, body: null, hasReplies: true }),
          replies: [makeReply("2")],
        }),
      },
      "1",
    );
    expect(document.querySelector('[aria-current="true"]')).toHaveTextContent("该评论已删除");
  });

  it("查看全部讨论：去掉 comment 参数", async () => {
    const replace = vi.spyOn(window.history, "replaceState");
    window.history.pushState(null, "", "/papers/1?comment=3&sort=latest");
    renderSingle(SINGLE);
    await userEvent.setup().click(screen.getByRole("button", { name: "查看全部讨论" }));
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?sort=latest");
  });
});
