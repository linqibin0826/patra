import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PendingDeletionRedirect } from "@/components/portal/PendingDeletionRedirect";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/papers",
  useSearchParams: () => new URLSearchParams("q=a"),
}));

afterEach(() => vi.clearAllMocks());

/// 缓存里放好当前用户（undefined 表示还在加载）后渲染
function renderWith(user: CurrentUser | null | undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, enabled: false } } });
  if (user !== undefined) {
    client.setQueryData(queryKeys.currentUser, user);
  }
  render(
    <QueryClientProvider client={client}>
      <PendingDeletionRedirect />
    </QueryClientProvider>,
  );
}

describe("PendingDeletionRedirect", () => {
  it("冷静期账号：去取消注销页，next 是当前路径与查询串", () => {
    renderWith({ ...USER, status: "PENDING_DELETION", deletionDueAt: "2026-11-06T00:00:00Z" });
    expect(router.replace).toHaveBeenCalledTimes(1);
    expect(router.replace).toHaveBeenCalledWith("/account/deletion-pending?next=%2Fpapers%3Fq%3Da");
  });

  it.each([
    ["正常账号", USER],
    ["未登录", null],
    ["还在加载", undefined],
  ] as const)("%s：不跳", (_label, user) => {
    renderWith(user);
    expect(router.replace).not.toHaveBeenCalled();
  });
});
