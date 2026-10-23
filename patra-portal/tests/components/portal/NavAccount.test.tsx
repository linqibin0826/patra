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

const LONG_EMAIL =
  "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org";

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
async function openMenu(email = USER.email) {
  const nav = renderNav({ ...USER, email });
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
    ["chen.yu@example.com", "C"],
    [LONG_EMAIL, "P"],
    ["2024cohort@example.org", "2"],
  ])("已登录：圆形标识显示邮箱的第一个字符（%s → %s）", (email, initial) => {
    renderNav({ ...USER, email });
    expect(screen.getByRole("button", { name: "账号菜单" })).toHaveTextContent(initial);
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });
});

describe("NavAccount · 用户菜单", () => {
  it("第一行是「当前账号」和完整邮箱，过长时截断、悬停看全；下面只有「退出登录」一项", async () => {
    const { menu } = await openMenu(LONG_EMAIL);
    expect(within(menu).getByRole("group", { name: `当前账号 ${LONG_EMAIL}` })).toBeInTheDocument();
    const email = within(menu).getByText(LONG_EMAIL);
    expect(email).toHaveAttribute("title", LONG_EMAIL);
    expect(email).toHaveClass("truncate");
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((item) => item.textContent),
    ).toEqual(["退出登录"]);
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
