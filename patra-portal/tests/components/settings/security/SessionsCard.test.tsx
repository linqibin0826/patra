import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SESSIONS } from "@tests/fixtures/sessions";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SessionsCard } from "@/components/settings/security/SessionsCard";
import { revokeOtherSessions, revokeSession } from "@/lib/identity/security-actions";
import type { SessionItem } from "@/lib/identity/session-list";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/security-actions", () => ({
  revokeSession: vi.fn(),
  revokeOtherSessions: vi.fn(),
}));
const withReauth = vi.fn(async (call: () => Promise<unknown>) => call());
vi.mock("@/lib/reauth/use-reauth", () => ({ useReauth: () => ({ withReauth }) }));
const logout = { mutate: vi.fn(), isPending: false };
vi.mock("@/lib/auth/logout-mutation", () => ({ useLogout: () => logout }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

const NOW = Date.parse("2026-10-23T08:00:00Z");

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

/// 渲染卡片；`sessions` 为 undefined 时让请求挂起（加载中）
function renderCard(sessions?: SessionItem[], status = 200) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      sessions === undefined && status === 200
        ? new Promise<Response>(() => {})
        : Promise.resolve(
            status === 200 ? Response.json({ sessions }) : Response.json({ ok: false }, { status }),
          ),
    ),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.currentUser, USER);
  render(
    <QueryClientProvider client={client}>
      <SessionsCard />
    </QueryClientProvider>,
  );
  return client;
}

const rows = async () =>
  within(await screen.findByRole("list", { name: "登录设备" })).getAllByRole("listitem");

describe("SessionsCard", () => {
  it("加载中：骨架", () => {
    renderCard();
    expect(screen.getByRole("status", { name: "正在加载登录设备" })).toBeInTheDocument();
  });

  it("加载失败：提示与重试", async () => {
    renderCard(undefined, 502);
    // 查询自己会重试一次（约 1 秒后），之后才显示失败
    expect(await screen.findByText("设备列表加载失败", {}, { timeout: 4_000 })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });

  it("401：当前用户写成 null，去登录页", async () => {
    const client = renderCard(undefined, 401);
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Fsecurity"),
    );
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
  });

  it("本机行：本机胶囊、当前在线、退出登录走普通登出", async () => {
    renderCard(SESSIONS);
    const [here] = await rows();
    expect(here).toHaveTextContent("Chrome 141 · macOS");
    expect(here).toHaveTextContent("本机");
    expect(here).toHaveTextContent("中国 上海 · 101.88.12.4");
    expect(here).toHaveTextContent("登录于 2026-10-20 09:12 · 当前在线");
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(within(here as HTMLElement).getByRole("button", { name: "退出登录" }));
    expect(logout.mutate).toHaveBeenCalled();
  });

  it("其他行：内网、属地未知、未知设备、最后活跃相对时间", async () => {
    renderCard(SESSIONS);
    const [, phone, tablet] = await rows();
    expect(phone).toHaveTextContent("内网 · 192.168.97.1");
    expect(phone).toHaveTextContent("最后活跃 2 小时前");
    expect(tablet).toHaveTextContent("未知设备");
    expect(tablet).toHaveTextContent("属地未知 · 101.88.12.9");
    expect(tablet).toHaveTextContent("最后活跃 10 月 12 日");
  });

  it("omits_separator_when_ip_missing", async () => {
    renderCard([{ ...(SESSIONS[1] as SessionItem), ipAddress: null }]);
    const [row] = await rows();
    expect(row).toHaveTextContent(/内网(?! ·)/);
  });

  it("treats_blank_device_name_as_unknown", async () => {
    renderCard([{ ...(SESSIONS[1] as SessionItem), deviceName: "  " }]);
    const [row] = await rows();
    expect(row).toHaveTextContent("未知设备");
  });

  it("踢单个：经再认证，成功后移除该行并提示", async () => {
    vi.mocked(revokeSession).mockResolvedValue({ ok: true, revokedSessions: 1 });
    renderCard(SESSIONS);
    await rows();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "退出 Safari 18 · iOS" }));
    expect(withReauth).toHaveBeenCalledTimes(1);
    expect(revokeSession).toHaveBeenCalledWith("723511223344556678");
    expect(toast.success).toHaveBeenCalledWith("已退出该设备");
    await waitFor(() => expect(screen.queryByText("Safari 18 · iOS")).not.toBeInTheDocument());
  });

  it("踢单个失败：错误提示，行还在", async () => {
    vi.mocked(revokeSession).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderCard(SESSIONS);
    await rows();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "退出 Safari 18 · iOS" }));
    expect(toast.error).toHaveBeenCalledWith("没有退出成功，请稍后再试");
    expect(screen.getByText("Safari 18 · iOS")).toBeInTheDocument();
  });

  it("在再认证里取消：没有提示，行还在", async () => {
    withReauth.mockResolvedValueOnce({ ok: false, reason: "cancelled" });
    renderCard(SESSIONS);
    await rows();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "退出 Safari 18 · iOS" }));
    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "退出 Safari 18 · iOS" })).not.toHaveAttribute(
      "aria-disabled",
    );
  });

  it("退出其他所有设备：提示数量，只剩本机", async () => {
    vi.mocked(revokeOtherSessions).mockResolvedValue({ ok: true, revokedSessions: 2 });
    renderCard(SESSIONS);
    await rows();
    // 之后重新拉取时，服务端只剩本机
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ sessions: [SESSIONS[0]] })),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "退出其他所有设备" }));
    expect(toast.success).toHaveBeenCalledWith("已退出其他 2 台设备");
    await waitFor(async () => expect(await rows()).toHaveLength(1));
  });

  it("只有本机：退出其他所有设备不可用并说明", async () => {
    renderCard([SESSIONS[0] as SessionItem]);
    await rows();
    const button = screen.getByRole("button", { name: "退出其他所有设备" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).toHaveAttribute("title", "没有其他设备登录着");
    expect(screen.getByText("没有其他设备登录着。")).toBeInTheDocument();
  });
});
