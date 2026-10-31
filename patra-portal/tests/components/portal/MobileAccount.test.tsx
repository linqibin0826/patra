import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MobileAccount } from "@/components/portal/MobileAccount";
import { logout } from "@/lib/auth/actions";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

// 覆写 usePathname / useSearchParams，保留 next/navigation 其余导出
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => "/papers",
    useSearchParams: () => new actual.ReadonlyURLSearchParams("q=GLP-1"),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const LONG_NAME = "心血管内科博士后研究员张某某心血管内科博士后研究员张某某";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 当前用户：`undefined` 表示还没有数据（`/api/auth/me` 一直不回来），`null` 表示未登录
function renderAccount(user: CurrentUser | null | undefined) {
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
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <MobileAccount onClose={onClose} />
    </QueryClientProvider>,
  );
  return { onClose, user: userEvent.setup() };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("MobileAccount · 三种形态", () => {
  it("登录状态未定：中性占位，读屏名称「正在确认登录状态」，不先出现「登录」", () => {
    renderAccount(undefined);
    expect(screen.getByRole("status", { name: "正在确认登录状态" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it("未登录：「登录」按钮，下面一行「还没有账号？注册」，两个链接都带当前地址作 next", () => {
    renderAccount(null);
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%3Fq%3DGLP-1",
    );
    expect(screen.getByText("还没有账号？")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register?next=%2Fpapers%3Fq%3DGLP-1",
    );
  });

  it.each(["登录", "注册"])("未登录：点「%s」先关闭菜单", async (name) => {
    const { user, onClose } = renderAccount(null);
    await user.click(screen.getByRole("link", { name }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("已登录：头像、昵称、@用户名，昵称过长时截断；不出现邮箱；下面是个人主页、设置、退出登录", () => {
    renderAccount({ ...USER, displayName: LONG_NAME });
    const name = screen.getByText(LONG_NAME);
    expect(name).toHaveAttribute("title", LONG_NAME);
    expect(name).toHaveClass("truncate");
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    expect(screen.queryByText(USER.email)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "主页" })).toHaveAttribute("href", "/u/chen-yu");
    expect(screen.getByRole("link", { name: "文献库" })).toHaveAttribute("href", "/library");
    expect(screen.getByRole("link", { name: "设置" })).toHaveAttribute("href", "/settings/profile");
    expect(screen.getByRole("button", { name: "退出登录" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it.each(["主页", "文献库", "设置"])("已登录：点「%s」先关闭菜单", async (name) => {
    const { user, onClose } = renderAccount(USER);
    await user.click(screen.getByRole("link", { name }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("MobileAccount · 退出登录", () => {
  it("进行中：「退出登录」禁用并说明原因，菜单不关；再点不会再发一次", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { user, onClose } = renderAccount(USER);
    const button = screen.getByRole("button", { name: "退出登录" });
    await user.click(button);
    await waitFor(() => expect(button).toHaveAttribute("aria-disabled", "true"));
    expect(button).toHaveAttribute("title", "正在退出登录");
    await user.click(button);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    pending.answer();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("成功：关闭菜单，提示「已退出登录」", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { user, onClose } = renderAccount(USER);
    await user.click(screen.getByRole("button", { name: "退出登录" }));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("promise 被拒绝：重新请求当前用户，菜单不关，「退出登录」恢复可点", async () => {
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { user, onClose } = renderAccount(USER);
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    const button = screen.getByRole("button", { name: "退出登录" });
    await user.click(button);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    await waitFor(() => expect(button).not.toHaveAttribute("aria-disabled"));
    expect(onClose).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });
});

describe("MobileAccount · 提醒", () => {
  it("已登录：四个按钮排成 2×2（主页、文献库 / 提醒、设置），「提醒」挂未读数", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ total: 5, replies: 5, mentions: 0, helpful: 0 })),
    );
    renderAccount(USER);
    const links = screen.getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(links).toEqual(["/u/chen-yu", "/library", "/notifications", "/settings/profile"]);
    expect(screen.getByRole("link", { name: "主页" }).parentElement).toHaveClass("grid-cols-2");
  });

  it("「提醒」的名称带未读数，点了先关闭菜单", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ total: 5, replies: 5, mentions: 0, helpful: 0 })),
    );
    const { user, onClose } = renderAccount(USER);
    await user.click(await screen.findByRole("link", { name: "提醒，5 条未读" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("未读数取不到：「提醒」按钮里写「暂不可用」", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ reason: "unavailable" }, { status: 502 })),
    );
    renderAccount(USER);
    expect(await screen.findByRole("link", { name: "提醒（暂不可用）" })).toHaveTextContent(
      "暂不可用",
    );
  });

  it("「提醒」的未读徽标收在按钮内的右上角", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ total: 5, replies: 5, mentions: 0, helpful: 0 })),
    );
    renderAccount(USER);
    const link = await screen.findByRole("link", { name: "提醒，5 条未读" });
    const badge = within(link).getByText("5");
    expect(badge).toHaveClass("absolute", "top-1.5", "right-1.5");
  });
});
