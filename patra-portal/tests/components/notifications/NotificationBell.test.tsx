import type { QueryClient } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { renderNotifications } from "@tests/support/notifications";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { UnreadBadge } from "@/components/notifications/UnreadBadge";
import type { UnreadData } from "@/lib/notifications/queries";
import { queryKeys } from "@/lib/query-keys";

let mockPathname = "/papers";
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return { ...actual, usePathname: () => mockPathname };
});

const fetchMock = vi.fn<typeof fetch>(() => new Promise(() => {}));

afterEach(() => {
  mockPathname = "/papers";
  vi.unstubAllGlobals();
  fetchMock.mockClear();
});

/// 未读数已在缓存里；`helpful` 是有帮助的未读，不计入 `total`
const withCounts = (total: number, helpful = 0) => ({
  seed: (client: QueryClient) =>
    client.setQueryData<UnreadData>(queryKeys.notificationUnread, {
      counts: { total, replies: total, mentions: 0, helpful },
      etag: null,
    }),
});

describe("NotificationBell", () => {
  it("未登录不渲染", () => {
    vi.stubGlobal("fetch", fetchMock);
    renderNotifications(<NotificationBell />, { user: null });
    expect(screen.queryByRole("link", { name: /提醒/ })).not.toBeInTheDocument();
  });

  it("未读数还没回来：只有铃铛，名称「提醒」，链到 /notifications", () => {
    vi.stubGlobal("fetch", fetchMock);
    renderNotifications(<NotificationBell />);
    const bell = screen.getByRole("link", { name: "提醒" });
    expect(bell).toHaveAttribute("href", "/notifications");
    expect(bell).not.toHaveTextContent(/\d/);
  });

  it("0 条不挂徽标", () => {
    renderNotifications(<NotificationBell />, withCounts(0));
    expect(screen.getByRole("link", { name: "提醒" })).not.toHaveTextContent(/\d/);
  });

  it("3 条：徽标 3，名称「提醒，3 条未读」", () => {
    renderNotifications(<NotificationBell />, withCounts(3));
    expect(screen.getByRole("link", { name: "提醒，3 条未读" })).toHaveTextContent("3");
  });

  it("有帮助的未读不进铃铛：回复与 @ 都是 0 时不挂徽标", () => {
    renderNotifications(<NotificationBell />, withCounts(0, 9));
    expect(screen.getByRole("link", { name: "提醒" })).not.toHaveTextContent(/\d/);
  });

  it("超过 99 写 99+", () => {
    renderNotifications(<NotificationBell />, withCounts(120));
    expect(screen.getByRole("link", { name: "提醒，99+ 条未读" })).toHaveTextContent("99+");
  });

  it("在 /notifications/** 时是当前项", () => {
    mockPathname = "/notifications/mentions";
    renderNotifications(<NotificationBell />, withCounts(1));
    expect(screen.getByRole("link", { name: /^提醒/ })).toHaveAttribute("aria-current", "page");
  });

  it("取不到未读数：名称「提醒（暂不可用）」、悬停说明，不挂徽标，仍可点", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ reason: "unavailable" }, { status: 502 })),
    );
    renderNotifications(<NotificationBell />);
    const bell = await screen.findByRole("link", { name: "提醒（暂不可用）" });
    expect(bell).toHaveAttribute("title", "提醒暂不可用");
    expect(bell).toHaveAttribute("href", "/notifications");
    expect(bell).not.toHaveTextContent(/\d/);
  });
});

describe("UnreadBadge", () => {
  it("挂载时的数字不弹；之后数字变化回弹一次", () => {
    const { rerender } = renderNotifications(<UnreadBadge count={2} />);
    expect(screen.getByText("2")).not.toHaveClass("anim-bounce");
    rerender(<UnreadBadge count={3} />);
    expect(screen.getByText("3")).toHaveClass("anim-bounce");
  });

  it("0 不渲染；从 0 变成有未读时回弹", () => {
    const { container, rerender } = renderNotifications(<UnreadBadge count={0} />);
    expect(container).toBeEmptyDOMElement();
    rerender(<UnreadBadge count={1} />);
    expect(screen.getByText("1")).toHaveClass("anim-bounce");
  });
});
