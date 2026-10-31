import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NavAccount } from "@/components/portal/NavAccount";
import { logout } from "@/lib/auth/actions";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

// 覆写 usePathname / useSearchParams，保留 next/navigation 其余导出
let mockPathname = "/papers";
let mockSearch = "q=GLP-1";
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => mockPathname,
    useSearchParams: () => new actual.ReadonlyURLSearchParams(mockSearch),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

/// 30 个字素的昵称、30 个字符的用户名（简报 §4.3 nav.menu-long）
const LONG_NAME = "心血管内科博士后研究员张某某心血管内科博士后研究员张某某";
const LONG_USERNAME = "postdoctoral-researcher-zhang1";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 当前用户：`undefined` 表示还没有数据（`/api/auth/me` 一直不回来），`null` 表示未登录
function renderNav(user: CurrentUser | null | undefined) {
  // 和 createQueryClient 一样，数据 60 秒内不过期：挂载时不会因为数据过期而重新请求
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  });
  if (user === undefined) {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
  } else {
    client.setQueryData(queryKeys.currentUser, user);
  }
  render(
    <QueryClientProvider client={client}>
      <NavAccount />
    </QueryClientProvider>,
  );
  return { client, user: userEvent.setup() };
}

/// 已登录，点开用户菜单
async function openMenu(user: CurrentUser = USER) {
  const nav = renderNav(user);
  await nav.user.click(screen.getByRole("button", { name: "账号菜单" }));
  return { ...nav, menu: await screen.findByRole("menu") };
}

afterEach(() => {
  mockPathname = "/papers";
  mockSearch = "q=GLP-1";
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("NavAccount · 三种形态", () => {
  it("登录状态未定：圆形占位，读屏名称「正在确认登录状态」，不先出现「登录」", () => {
    renderNav(undefined);
    expect(screen.getByRole("status", { name: "正在确认登录状态" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it.each([
    ["/papers", "q=GLP-1", "/login?next=%2Fpapers%3Fq%3DGLP-1"],
    ["/journals/7", "", "/login?next=%2Fjournals%2F7"],
    ["/", "", "/login"],
  ])("未登录：「登录」带上当前地址作 next（%s?%s）", (pathname, search, href) => {
    mockPathname = pathname;
    mockSearch = search;
    renderNav(null);
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute("href", href);
  });

  it.each([
    ["陈宇", "陈"],
    ["alice", "A"],
    ["·小林", "小"],
  ])("已登录：显示默认头像，字取昵称（%s → %s）", (displayName, initial) => {
    renderNav({ ...USER, displayName });
    expect(screen.getByRole("button", { name: "账号菜单" })).toHaveTextContent(initial);
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it("已登录且有头像：显示 128 档的图片", () => {
    renderNav({ ...USER, avatarUrl: "/users/352303128713027814/avatar?v=0123456789abcdef" });
    const img = screen.getByRole("button", { name: "账号菜单" }).querySelector("img");
    expect(img).toHaveAttribute(
      "src",
      "/api/users/352303128713027814/avatar?v=0123456789abcdef&size=128",
    );
  });
});

describe("NavAccount · 文献库入口", () => {
  it("已登录：头像左边「文献库」链接", () => {
    renderNav(USER);
    expect(screen.getByRole("link", { name: "文献库" })).toHaveAttribute("href", "/library");
    expect(screen.getByRole("link", { name: "文献库" })).not.toHaveAttribute("aria-current");
  });

  it("在 /library/** 时是当前项", () => {
    mockPathname = "/library/unfiled";
    renderNav(USER);
    expect(screen.getByRole("link", { name: "文献库" })).toHaveAttribute("aria-current", "page");
    mockPathname = "/papers";
  });

  it("未登录：没有「文献库」", () => {
    renderNav(null);
    expect(screen.queryByRole("link", { name: "文献库" })).not.toBeInTheDocument();
  });
});

describe("NavAccount · 用户菜单", () => {
  it("头部是昵称和 @用户名，不出现邮箱；下面是个人主页、我的文献库、提醒、设置、退出登录", async () => {
    const { menu } = await openMenu();
    expect(within(menu).getByRole("group", { name: "陈宇 @chen-yu" })).toBeInTheDocument();
    expect(within(menu).queryByText(USER.email)).not.toBeInTheDocument();
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((item) => item.textContent),
    ).toEqual(["个人主页", "我的文献库", expect.stringMatching(/^提醒/), "设置", "退出登录"]);
    expect(within(menu).getByRole("menuitem", { name: "我的文献库" })).toHaveAttribute(
      "href",
      "/library",
    );
    expect(within(menu).getByRole("menuitem", { name: "个人主页" })).toHaveAttribute(
      "href",
      "/u/chen-yu",
    );
    expect(within(menu).getByRole("menuitem", { name: "设置" })).toHaveAttribute(
      "href",
      "/settings/profile",
    );
  });

  it("昵称和用户名过长时截断，悬停看全", async () => {
    const { menu } = await openMenu({ ...USER, displayName: LONG_NAME, username: LONG_USERNAME });
    const name = within(menu).getByText(LONG_NAME);
    expect(name).toHaveAttribute("title", LONG_NAME);
    expect(name).toHaveClass("truncate");
    const handle = within(menu).getByText(`@${LONG_USERNAME}`);
    expect(handle).toHaveAttribute("title", `@${LONG_USERNAME}`);
    expect(handle).toHaveClass("truncate");
  });

  it("退出进行中：「退出登录」禁用并说明原因，菜单一直开着；再点不会再发一次", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { user, menu } = await openMenu();
    const item = within(menu).getByRole("menuitem", { name: "退出登录" });
    await user.click(item);
    await waitFor(() => expect(item).toHaveAttribute("aria-disabled", "true"));
    expect(item).toHaveAttribute("title", "正在退出登录");
    await user.click(item);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("menu")).toBeInTheDocument();
    pending.answer();
    await screen.findByRole("link", { name: "登录" });
  });

  it("退出成功：圆形变回「登录」，焦点落在「登录」上，提示「已退出登录」", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { user, menu } = await openMenu();
    await user.click(within(menu).getByRole("menuitem", { name: "退出登录" }));
    const login = await screen.findByRole("link", { name: "登录" });
    await waitFor(() => expect(login).toHaveFocus());
    expect(screen.queryByRole("button", { name: "账号菜单" })).not.toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("退出的 promise 被拒绝：重新请求当前用户，菜单保持打开，「退出登录」恢复可点", async () => {
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { user, menu } = await openMenu();
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    const item = within(menu).getByRole("menuitem", { name: "退出登录" });
    await user.click(item);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    await waitFor(() => expect(item).not.toHaveAttribute("aria-disabled"));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "账号菜单" })).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
  });
});

describe("NavAccount · 提醒入口", () => {
  it("已登录：「文献库」与头像之间是铃铛", () => {
    const { client } = renderNav(USER);
    client.setQueryData(queryKeys.notificationUnread, {
      counts: { total: 3, replies: 2, mentions: 1, helpful: 0 },
      etag: null,
    });
    const links = screen.getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(links).toEqual(["/library", "/notifications"]);
  });

  it("未登录没有铃铛", () => {
    renderNav(null);
    expect(screen.queryByRole("link", { name: /^提醒/ })).not.toBeInTheDocument();
  });

  it("用户菜单：「我的文献库」后是「提醒」，右端是未读数；0 不写数字", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ total: 3, replies: 2, mentions: 1, helpful: 0 })),
    );
    const { client, menu } = await openMenu();
    await waitFor(() =>
      expect(
        within(menu)
          .getAllByRole("menuitem")
          .map((item) => item.textContent),
      ).toEqual(["个人主页", "我的文献库", "提醒3", "设置", "退出登录"]),
    );
    expect(within(menu).getByRole("menuitem", { name: /提醒/ })).toHaveAttribute(
      "href",
      "/notifications",
    );
    client.setQueryData(queryKeys.notificationUnread, {
      counts: { total: 0, replies: 0, mentions: 0, helpful: 0 },
      etag: null,
    });
    await waitFor(() =>
      expect(within(menu).getByRole("menuitem", { name: /提醒/ })).toHaveTextContent(/^提醒$/),
    );
  });

  it("用户菜单：未读数取不到时写「暂不可用」", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ reason: "unavailable" }, { status: 502 })),
    );
    const { menu } = await openMenu();
    await waitFor(() =>
      expect(within(menu).getByRole("menuitem", { name: /提醒/ })).toHaveTextContent("暂不可用"),
    );
  });
});
