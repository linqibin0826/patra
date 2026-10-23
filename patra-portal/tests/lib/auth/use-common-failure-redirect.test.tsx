import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

afterEach(() => vi.clearAllMocks());

function setup() {
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, USER);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(() => useCommonFailureRedirect("/settings/account"), { wrapper });
  return { client, redirect: result.current };
}

describe("useCommonFailureRedirect", () => {
  it("pending-deletion：去取消注销页，next 是这一页", () => {
    const { client, redirect } = setup();
    redirect("pending-deletion");
    expect(router.replace).toHaveBeenCalledWith(
      "/account/deletion-pending?next=%2Fsettings%2Faccount",
    );
    expect(client.getQueryData(queryKeys.currentUser)).toEqual(USER);
  });

  it("signed-out：当前用户写成 null，去登录页", () => {
    const { client, redirect } = setup();
    redirect("signed-out");
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Faccount");
  });
});
