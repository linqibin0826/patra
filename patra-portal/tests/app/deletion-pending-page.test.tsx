import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/identity/account-actions", () => ({ cancelDeletion: vi.fn() }));
vi.mock("@/lib/auth/actions", () => ({ logout: vi.fn() }));

import DeletionPendingPage, { metadata } from "@/app/account/deletion-pending/page";
import { lookupSession } from "@/lib/auth/current-user";

afterEach(() => vi.clearAllMocks());

const props = (next?: string) => ({
  searchParams: Promise.resolve(next === undefined ? {} : { next }),
});

describe("/account/deletion-pending", () => {
  it.each(["anonymous", "rejected"] as const)("%s：去登录页，next 原样带上", async (status) => {
    vi.mocked(lookupSession).mockResolvedValue({ status });
    await expect(DeletionPendingPage(props("/papers"))).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fpapers",
    );
  });

  it("正常账号：直接回 next", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "authenticated", user: USER });
    await expect(DeletionPendingPage(props("/papers"))).rejects.toThrow("NEXT_REDIRECT:/papers");
  });

  it("网关不可用：抛错交给错误页", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "unavailable" });
    await expect(DeletionPendingPage(props())).rejects.toThrow(/不可用/);
  });

  it("冷静期账号：标题、到期时间、没有返回入口、noindex", async () => {
    vi.mocked(lookupSession).mockResolvedValue({
      status: "authenticated",
      user: { ...USER, status: "PENDING_DELETION", deletionDueAt: "2026-11-06T00:00:00Z" },
    });
    const page = (await DeletionPendingPage(props("/papers"))) as ReactElement;
    render(<QueryClientProvider client={new QueryClient()}>{page}</QueryClientProvider>);
    expect(screen.getByRole("heading", { level: 1, name: "你的账号正在注销" })).toBeInTheDocument();
    expect(screen.getByText("账号将于 2026 年 11 月 6 日 08:00 永久删除。")).toBeInTheDocument();
    expect(
      screen.getByText("删除后资料、头像和关注领域无法恢复，用户名不会释放。"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "返回" })).not.toBeInTheDocument();
    expect(metadata).toMatchObject({ title: "取消注销 · Patra", robots: { index: false } });
  });
});
