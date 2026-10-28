import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeUserExperience } from "@tests/fixtures/experience";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { QueryClient } from "@tanstack/react-query";
import { ProfileExperiences } from "@/components/profile/ProfileExperiences";
import type { UserExperiencePage } from "@/lib/interaction/experience-schema";
import { queryKeys } from "@/lib/query-keys";

const fetchMock = vi.fn<typeof fetch>();
const NOW = Date.parse("2026-10-28T02:00:00Z");

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

const seed = (first: UserExperiencePage) => (client: QueryClient) =>
  client.setQueryData(queryKeys.experienceUser("7"), { pages: [first], pageParams: [null] });

function renderList(self: boolean, first?: UserExperiencePage) {
  return renderDiscussion(<ProfileExperiences userId="7" self={self} now={NOW} />, {
    seed: first ? seed(first) : () => {},
  });
}

describe("主页「投稿经验」（简报 §3.17）", () => {
  it("本人视角：说明行，匿名条目带仅你可见", () => {
    renderList(true, {
      items: [
        makeUserExperience(),
        makeUserExperience({ postId: "10", anonymous: true, mine: true }),
      ],
      nextCursor: null,
    });
    expect(
      screen.getByText("这里列出你写过的投稿经验，匿名发布的只有你看得到。"),
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole("link", { name: /Journal of the American Heart Association/ }),
    ).toHaveLength(2);
    expect(screen.getByText("匿名 · 仅你可见")).toBeInTheDocument();
  });

  it("别人视角：没有说明行", () => {
    renderList(false, { items: [makeUserExperience()], nextCursor: null });
    expect(screen.queryByText(/匿名发布的只有你看得到/)).not.toBeInTheDocument();
  });

  it("本人为空：去期刊列表", () => {
    renderList(true, { items: [], nextCursor: null });
    expect(screen.getByText("还没有写过投稿经验")).toBeInTheDocument();
    expect(screen.getByText("在期刊页写下你的投稿经历，帮后来的人选刊。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去期刊列表" })).toHaveAttribute("href", "/journals");
  });

  it("别人为空：只有一句", () => {
    renderList(false, { items: [], nextCursor: null });
    expect(screen.getByText("还没有公开的投稿经验")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "去期刊列表" })).not.toBeInTheDocument();
  });

  it("加载更多带游标", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ items: [makeUserExperience({ postId: "11" })], nextCursor: null }),
    );
    const user = userEvent.setup();
    renderList(true, { items: [makeUserExperience()], nextCursor: "c2" });
    await user.click(screen.getByRole("button", { name: "加载更多" }));
    await waitFor(() =>
      expect(screen.getAllByRole("link", { name: /Journal of/ })).toHaveLength(2),
    );
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/api/users/7/posts?cursor=c2");
  });

  it("取不到：错误块与重试", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const user = userEvent.setup();
    renderList(true);
    expect(await screen.findByRole("heading", { name: "投稿经验暂时打不开" })).toBeInTheDocument();
    fetchMock.mockResolvedValue(Response.json({ items: [makeUserExperience()], nextCursor: null }));
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByRole("link", { name: /Journal of/ })).toBeInTheDocument();
  });
});
