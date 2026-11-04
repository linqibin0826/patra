import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/identity/oauth-registration", () => ({ fetchPendingRegistration: vi.fn() }));
vi.mock("@/components/auth/ProviderRegistrationFlow", () => ({
  ProviderRegistrationFlow: vi.fn(() => null),
}));

import RegisterCompletePage, { metadata } from "@/app/register/complete/page";
import { fetchPendingRegistration } from "@/lib/identity/oauth-registration";

describe("/register/complete", () => {
  it("不让搜索引擎收录", () => {
    expect(metadata).toEqual({ title: "完成注册 · Patra", robots: { index: false } });
  });

  it("地址栏里的 ticket 不算数：取内容时不传任何参数，只读 Cookie", async () => {
    vi.mocked(fetchPendingRegistration).mockResolvedValue("expired");
    await RegisterCompletePage({ searchParams: Promise.resolve({ ticket: "attacker-ticket" }) });
    expect(fetchPendingRegistration).toHaveBeenCalledWith();
  });

  it("把取到的内容和校验过的 next 交给流程", async () => {
    vi.mocked(fetchPendingRegistration).mockResolvedValue("expired");
    const element = await RegisterCompletePage({
      searchParams: Promise.resolve({ next: "//evil.com" }),
    });
    expect(element.props).toEqual({ pending: "expired", next: "/" });
  });
});
