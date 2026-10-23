import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));

import { lookupSession } from "@/lib/auth/current-user";
import { requireUser } from "@/lib/auth/require-user";

afterEach(() => vi.clearAllMocks());

describe("requireUser", () => {
  it("已登录：返回当前用户", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "authenticated", user: USER });
    await expect(requireUser("/settings/profile")).resolves.toEqual(USER);
  });

  it.each(["anonymous", "rejected"] as const)("%s：去登录页，next 是这一页", async (status) => {
    vi.mocked(lookupSession).mockResolvedValue({ status });
    await expect(requireUser("/settings/privacy")).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fsettings%2Fprivacy",
    );
  });

  it("网关不可用：抛错交给错误页", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "unavailable" });
    await expect(requireUser("/settings/profile")).rejects.toThrow(/不可用/);
  });
});
