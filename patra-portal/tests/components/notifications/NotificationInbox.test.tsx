import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeNotification } from "@tests/fixtures/notification";
import { renderNotifications } from "@tests/support/notifications";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationInbox } from "@/components/notifications/NotificationInbox";
import type { Notification } from "@/lib/notification/inbox-schema";
import { queryKeys } from "@/lib/query-keys";

const mockReplace = vi.fn();
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => "/notifications",
    useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
  };
});
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const DAY = 24 * 60 * 60 * 1000;

/// 一条生成于 `ms` 的提醒（列表用真实时钟分组，时间都相对现在取）
function at(id: string, ms: number, excerpt: string): Notification {
  return makeNotification({
    notificationId: id,
    commentId: id,
    occurredAt: new Date(ms).toISOString(),
    excerpt,
  });
}

/// 门户接口的桩：按地址回列表页、未读数、已读
function stubApi(routes: {
  pages?: { items: Notification[]; nextCursor: string | null }[];
  list?: () => Response;
  counts?: { total: number; replies: number; mentions: number; helpful: number };
  markAll?: () => Response;
}) {
  let page = 0;
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.startsWith("/api/me/notifications/unread-count")) {
      return Response.json(routes.counts ?? { total: 1, replies: 1, mentions: 0, helpful: 0 });
    }
    if (url === "/api/me/notifications/read" && init?.method === "PUT") {
      return routes.markAll?.() ?? Response.json({ total: 0, replies: 0, mentions: 0, helpful: 0 });
    }
    if (url.endsWith("/read")) {
      return Response.json({ total: 0, replies: 0, mentions: 0, helpful: 0 });
    }
    if (routes.list) {
      return routes.list();
    }
    const body = routes.pages?.[page] ?? { items: [], nextCursor: null };
    page += 1;
    return Response.json(body);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  mockReplace.mockClear();
});

describe("NotificationInbox", () => {
  it("h1、两个标签（当前项、未读胶囊）、全部标为已读", async () => {
    stubApi({
      counts: { total: 3, replies: 2, mentions: 1, helpful: 0 },
      pages: [{ items: [makeNotification()], nextCursor: null }],
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    expect(screen.getByRole("heading", { level: 1, name: "提醒" })).toBeInTheDocument();
    const tabs = screen.getByRole("navigation", { name: "提醒分类" });
    const replies = within(tabs).getByRole("link", { name: /回复我的/ });
    expect(replies).toHaveAttribute("href", "/notifications");
    expect(replies).toHaveAttribute("aria-current", "page");
    expect(within(tabs).getByRole("link", { name: /@ 我的/ })).toHaveAttribute(
      "href",
      "/notifications/mentions",
    );
    expect(await within(replies).findByText("2")).toBeInTheDocument();
    expect(within(tabs).getByRole("link", { name: /@ 我的/ })).toHaveTextContent("1");
    expect(screen.getByRole("button", { name: "全部标为已读" })).toBeEnabled();
  });

  it("第三个标签「有帮助」：链到 /notifications/helpful，胶囊是有帮助自己的未读数", async () => {
    stubApi({
      counts: { total: 3, replies: 2, mentions: 1, helpful: 4 },
      pages: [{ items: [], nextCursor: null }],
    });
    renderNotifications(<NotificationInbox tab="helpful" />);
    const tabs = screen.getByRole("navigation", { name: "提醒分类" });
    const helpful = within(tabs).getByRole("link", { name: /有帮助/ });
    expect(helpful).toHaveAttribute("href", "/notifications/helpful");
    expect(helpful).toHaveAttribute("aria-current", "page");
    expect(await within(helpful).findByText("4")).toBeInTheDocument();
  });

  it("只有有帮助有未读时「全部标为已读」可点，点了三个标签一起清零", async () => {
    const fetchMock = stubApi({
      counts: { total: 0, replies: 0, mentions: 0, helpful: 2 },
      pages: [{ items: [], nextCursor: null }],
    });
    renderNotifications(<NotificationInbox tab="helpful" />);
    const button = screen.getByRole("button", { name: "全部标为已读" });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    expect(fetchMock).toHaveBeenCalledWith("/api/me/notifications/read", { method: "PUT" });
    const tabs = screen.getByRole("navigation", { name: "提醒分类" });
    await vi.waitFor(() =>
      expect(within(tabs).getByRole("link", { name: /有帮助/ })).not.toHaveTextContent(/\d/),
    );
  });

  it("四个数都是 0 时「全部标为已读」禁用", async () => {
    const fetchMock = stubApi({
      counts: { total: 0, replies: 0, mentions: 0, helpful: 0 },
      pages: [{ items: [], nextCursor: null }],
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/me/notifications/unread-count",
        expect.anything(),
      ),
    );
    expect(screen.getByRole("button", { name: "全部标为已读" })).toBeDisabled();
  });

  it("同一条在两页里出现时只渲染一行", async () => {
    stubApi({
      pages: [
        {
          items: [at("1", Date.now(), "第一条"), at("2", Date.now(), "会挪的那条")],
          nextCursor: "c2",
        },
        { items: [at("2", Date.now(), "会挪的那条")], nextCursor: null },
      ],
    });
    renderNotifications(<NotificationInbox tab="helpful" />);
    await screen.findByText("第一条");
    await userEvent.click(screen.getByRole("button", { name: "加载更多提醒" }));
    await screen.findByText("提醒保留 5 个月。");
    expect(screen.getAllByText("会挪的那条")).toHaveLength(1);
  });

  it("加载中 6 行骨架；没有未读时「全部标为已读」禁用", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
    renderNotifications(<NotificationInbox tab="replies" />);
    expect(screen.getAllByTestId("notification-skeleton-row")).toHaveLength(6);
    expect(screen.getByRole("button", { name: "全部标为已读" })).toBeDisabled();
  });

  it.each([
    [
      "replies",
      "还没有人回复你",
      "在文献页参与讨论、在期刊页分享投稿经验，别人的回复会出现在这里。",
    ],
    ["mentions", "还没有人 @ 你", "别人在评论里 @ 你时，会出现在这里。"],
    [
      "helpful",
      "还没有收到「有帮助」",
      "别人觉得你的评论或投稿经验有帮助时，同一条内容每天合成一条，出现在这里。",
    ],
  ] as const)("%s 为空", async (tab, title, body) => {
    stubApi({ pages: [{ items: [], nextCursor: null }] });
    renderNotifications(<NotificationInbox tab={tab} />);
    expect(await screen.findByText(title)).toBeInTheDocument();
    expect(screen.getByText(body)).toBeInTheDocument();
  });

  it("失败：「提醒暂时打不开」+ 重试，重试后恢复", async () => {
    let fail = true;
    stubApi({
      list: () =>
        fail
          ? Response.json({ reason: "unavailable" }, { status: 502 })
          : Response.json({ items: [makeNotification()], nextCursor: null }),
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    expect(await screen.findByText("提醒暂时打不开")).toBeInTheDocument();
    expect(screen.getByText("请稍后再试。")).toBeInTheDocument();
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("陈宇")).toBeInTheDocument();
  });

  it("加载更多；最后一页下面写「提醒保留 5 个月。」", async () => {
    const fetchMock = stubApi({
      pages: [
        { items: [makeNotification({ notificationId: "1" })], nextCursor: "c2" },
        { items: [makeNotification({ notificationId: "2", excerpt: "第二页" })], nextCursor: null },
      ],
    });
    renderNotifications(<NotificationInbox tab="mentions" />);
    await screen.findByText("陈宇");
    expect(screen.queryByText("提醒保留 5 个月。")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "加载更多提醒" }));
    expect(await screen.findByText("第二页")).toBeInTheDocument();
    expect(screen.getByText("提醒保留 5 个月。")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/me/notifications?tab=mentions&cursor=c2");
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });

  it("按今天、昨天、更早分组，每组一个二级标题", async () => {
    const now = Date.now();
    stubApi({
      pages: [
        {
          items: [
            at("1", now, "今天一"),
            at("2", now, "今天二"),
            at("3", now - DAY, "昨天的"),
            at("4", now - 10 * DAY, "更早的"),
          ],
          nextCursor: null,
        },
      ],
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    await screen.findByText("今天一");
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual([
      "今天",
      "昨天",
      "更早",
    ]);
    const today = screen.getByRole("region", { name: "今天" });
    expect(within(today).getAllByRole("listitem")).toHaveLength(2);
  });

  it("没有条目的组不出标题", async () => {
    stubApi({ pages: [{ items: [at("4", Date.now() - 10 * DAY, "更早的")], nextCursor: null }] });
    renderNotifications(<NotificationInbox tab="replies" />);
    await screen.findByText("更早的");
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual([
      "更早",
    ]);
  });

  it("加载更多后同一组的标题不重复，条目并进已有的组", async () => {
    const earlier = Date.now() - 10 * DAY;
    stubApi({
      pages: [
        { items: [at("1", earlier, "第一页")], nextCursor: "c2" },
        { items: [at("2", earlier - DAY, "第二页")], nextCursor: null },
      ],
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    await screen.findByText("第一页");
    await userEvent.click(screen.getByRole("button", { name: "加载更多提醒" }));
    await screen.findByText("第二页");
    expect(screen.getAllByRole("heading", { level: 2, name: "更早" })).toHaveLength(1);
    expect(
      within(screen.getByRole("region", { name: "更早" })).getAllByRole("listitem"),
    ).toHaveLength(2);
  });

  it("点一行：立即去掉未读标记并请求已读", async () => {
    const fetchMock = stubApi({ pages: [{ items: [makeNotification()], nextCursor: null }] });
    renderNotifications(<NotificationInbox tab="replies" />);
    const row = await screen.findByRole("link", { name: /陈宇/ });
    row.addEventListener("click", (event) => event.preventDefault());
    await userEvent.click(row);
    expect(row).toHaveAttribute("data-unread", "false");
    expect(fetchMock).toHaveBeenCalledWith("/api/me/notifications/100/read", {
      method: "PUT",
      keepalive: true,
    });
  });

  it("全部标为已读失败：顶部提示条，圆点恢复", async () => {
    stubApi({
      counts: { total: 1, replies: 1, mentions: 0, helpful: 0 },
      pages: [{ items: [makeNotification()], nextCursor: null }],
      markAll: () => Response.json({ reason: "unavailable" }, { status: 502 }),
    });
    renderNotifications(<NotificationInbox tab="replies" />);
    const row = await screen.findByRole("link", { name: /陈宇/ });
    await vi.waitFor(() =>
      expect(screen.getByRole("button", { name: "全部标为已读" })).toBeEnabled(),
    );
    await userEvent.click(screen.getByRole("button", { name: "全部标为已读" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有标记成功，请稍后再试");
    expect(row).toHaveAttribute("data-unread", "true");
  });

  it("列表 401（会话在页面开着时过期）：让当前用户失效，变成未登录后去登录页", async () => {
    stubApi({ list: () => Response.json({ reason: "signed-out" }, { status: 401 }) });
    const { client } = renderNotifications(<NotificationInbox tab="replies" />);
    await vi.waitFor(() =>
      expect(client.getQueryState(queryKeys.currentUser)?.isInvalidated).toBe(true),
    );
    client.setQueryData(queryKeys.currentUser, null);
    await vi.waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith("/login?next=%2Fnotifications"),
    );
  });

  it("列表还在加载时点「全部标为已读」，列表照常加载出来", async () => {
    let finishList: (res: Response) => void = () => {};
    const listResponse = new Promise<Response>((resolve) => {
      finishList = resolve;
    });
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init?: RequestInit) => {
        if (url.startsWith("/api/me/notifications/unread-count")) {
          return Promise.resolve(Response.json({ total: 1, replies: 1, mentions: 0, helpful: 0 }));
        }
        if (url === "/api/me/notifications/read" && init?.method === "PUT") {
          return Promise.resolve(Response.json({ total: 0, replies: 0, mentions: 0, helpful: 0 }));
        }
        return listResponse;
      }),
    );
    renderNotifications(<NotificationInbox tab="replies" />);
    const button = screen.getByRole("button", { name: "全部标为已读" });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    finishList(Response.json({ items: [makeNotification({ read: true })], nextCursor: null }));
    expect(await screen.findByText("陈宇")).toBeInTheDocument();
  });
});
