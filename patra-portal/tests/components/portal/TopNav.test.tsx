import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { TopNav } from "@/components/portal/TopNav";
import type { CurrentUser } from "@/lib/auth/types";

// 覆写 usePathname / useSearchParams / useRouter，保留 next/navigation 其余导出（Next <Link> 渲染所需）
let mockPathname = "/";
const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => mockPathname,
    useSearchParams: () => new actual.ReadonlyURLSearchParams(),
    useRouter: () => ({ push: mockPush }),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));

/// 账号区要读当前用户：每次用新的 QueryClient，`/api/auth/me` 回 `user`（默认未登录），未读数接口回 3 条
function renderTopNav(user: CurrentUser | null = null) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) =>
      url.startsWith("/api/me/notifications/unread-count")
        ? Response.json({ total: 3, replies: 2, mentions: 1, helpful: 0 })
        : Response.json({ user }),
    ),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TopNav />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  mockPathname = "/";
  mockPush.mockClear();
  vi.unstubAllGlobals();
});

describe("TopNav 快速检索", () => {
  it("⌘K 打开快速检索对话框", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.keyboard("{Meta>}k{/Meta}");
    expect(await screen.findByRole("dialog", { name: "快速检索" })).toBeInTheDocument();
  });

  it("Ctrl+K 同样打开", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.keyboard("{Control>}k{/Control}");
    expect(await screen.findByRole("dialog", { name: "快速检索" })).toBeInTheDocument();
  });

  it("点导航「快速检索」打开对话框，提交关键词跳 /papers?q= 并关闭", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.click(screen.getByRole("button", { name: /快速检索/ }));
    const dialog = await screen.findByRole("dialog", { name: "快速检索" });
    await user.type(within(dialog).getByRole("textbox"), "GLP-1");
    await user.click(within(dialog).getByRole("button", { name: "搜索" }));
    expect(mockPush).toHaveBeenCalledWith("/papers?q=GLP-1");
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "快速检索" })).not.toBeInTheDocument(),
    );
  });

  it("对话框的键位提示只写真实可用的按键（不写「Tab 切换检索方式」）", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.keyboard("{Meta>}k{/Meta}");
    const dialog = await screen.findByRole("dialog", { name: "快速检索" });
    expect(within(dialog).getByText("↵ 检索")).toBeInTheDocument();
    expect(within(dialog).queryByText(/Tab/)).not.toBeInTheDocument();
  });

  it("「关于」指向页脚 #about", () => {
    renderTopNav();
    expect(screen.getByRole("link", { name: "关于" })).toHaveAttribute("href", "#about");
  });
});

describe("TopNav 快捷键提示", () => {
  const setPlatform = (platform: string) =>
    Object.defineProperty(window.navigator, "platform", { value: platform, configurable: true });
  afterEach(() => {
    Reflect.deleteProperty(window.navigator, "platform");
  });

  it("Apple 平台写 ⌘K", async () => {
    setPlatform("MacIntel");
    renderTopNav();
    expect(await screen.findByText("⌘K")).toBeInTheDocument();
  });

  it("其他平台写 Ctrl K（Windows / Linux 实际按 Ctrl+K）", async () => {
    setPlatform("Win32");
    renderTopNav();
    expect(await screen.findByText("Ctrl K")).toBeInTheDocument();
    expect(screen.queryByText("⌘K")).not.toBeInTheDocument();
  });
});

describe("TopNav", () => {
  it("渲染 banner role", () => {
    renderTopNav();
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });

  it("在首页路径下，首页 link 标记为 aria-current=page", () => {
    mockPathname = "/";
    renderTopNav();
    const homeLink = screen.getByRole("link", { name: /首页/ });
    expect(homeLink).toHaveAttribute("aria-current", "page");
  });

  it("期刊 link 指向 /journals 且不再 disabled", () => {
    renderTopNav();
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("href", "/journals");
    expect(journalLink).not.toHaveAttribute("aria-disabled");
  });

  it("在 /journals 路径下，期刊 高亮、首页 不再高亮", () => {
    mockPathname = "/journals";
    renderTopNav();
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("aria-current", "page");
    const homeLink = screen.getByRole("link", { name: /首页/ });
    expect(homeLink).not.toHaveAttribute("aria-current", "page");
  });

  it("在 /journals 子路由（详情）下，期刊 仍高亮", () => {
    mockPathname = "/journals/123";
    renderTopNav();
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("aria-current", "page");
  });

  it("主题 tab 已移除（不再渲染）", () => {
    renderTopNav();
    expect(screen.queryByText("主题")).not.toBeInTheDocument();
  });

  it("点击汉堡按钮打开 Sheet drawer", async () => {
    const user = userEvent.setup();
    renderTopNav();
    const menuBtn = screen.getByRole("button", { name: /打开菜单|menu/i });
    await user.click(menuBtn);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("文献 link 指向 /papers，不再 disabled / SOON", () => {
    renderTopNav();
    const link = screen.getByRole("link", { name: "文献" });
    expect(link).toHaveAttribute("href", "/papers");
    expect(link).not.toHaveAttribute("aria-disabled");
    expect(screen.queryByText(/soon/i)).not.toBeInTheDocument();
  });

  it("在 /papers 与 /papers/[id] 下，文献 高亮", () => {
    mockPathname = "/papers/123";
    renderTopNav();
    expect(screen.getByRole("link", { name: "文献" })).toHaveAttribute("aria-current", "page");
  });

  it("移动菜单的关闭按钮用中文可访问名「关闭」", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "关闭" })).toBeInTheDocument();
  });

  it("移动菜单中文献同样可点", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("link", { name: "文献" })).toHaveAttribute("href", "/papers");
  });
});

describe("TopNav 账号区", () => {
  it("桌面：「关于」右边是账号区，确认登录状态前是占位，未登录时换成「登录」", async () => {
    renderTopNav();
    const placeholder = screen.getByRole("status", { name: "正在确认登录状态" });
    const about = screen.getByRole("link", { name: "关于" });
    expect(
      about.compareDocumentPosition(placeholder) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(await screen.findByRole("link", { name: "登录" })).toHaveAttribute("href", "/login");
  });

  it("邮箱未验证时，导航栏下方出现验证横幅（在 header 之外，随页面滚走）", async () => {
    renderTopNav({ ...USER, emailVerified: false });
    const text = await screen.findByText(/验证邮箱后，别人才能看到你的主页/);
    expect(screen.getByRole("banner")).not.toContainElement(text);
  });

  it("桌面：已登录时是头像", async () => {
    renderTopNav(USER);
    expect(await screen.findByRole("button", { name: "账号菜单" })).toHaveTextContent("陈");
  });

  it("移动菜单最底部是账号块，点「登录」先关闭菜单", async () => {
    const user = userEvent.setup();
    renderTopNav();
    await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register",
    );
    await user.click(within(dialog).getByRole("link", { name: "登录" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});

describe("TopNav 提醒入口", () => {
  it("已登录：桌面一个、窄屏顶栏一个；窄屏的在检索与菜单之间", async () => {
    renderTopNav(USER);
    const bells = await screen.findAllByRole("link", { name: "提醒，3 条未读" });
    expect(bells).toHaveLength(2);
    const narrow = bells.find((bell) => bell.className.includes("max-[880px]:inline-flex"));
    const search = screen.getByRole("button", { name: /快速检索/ });
    const menu = screen.getByRole("button", { name: "打开菜单" });
    expect(narrow).toBeDefined();
    expect(
      search.compareDocumentPosition(narrow as Node) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      (narrow as Node).compareDocumentPosition(menu) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("未登录：没有铃铛，也不请求未读数", async () => {
    renderTopNav(null);
    expect(await screen.findByRole("link", { name: "登录" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /^提醒/ })).not.toBeInTheDocument();
    const urls = vi.mocked(fetch).mock.calls.map(([url]) => String(url));
    expect(urls.some((url) => url.includes("notifications"))).toBe(false);
  });
});
