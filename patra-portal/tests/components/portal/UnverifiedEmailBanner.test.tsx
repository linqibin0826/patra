import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { UnverifiedEmailBanner } from "@/components/portal/UnverifiedEmailBanner";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));

function renderBanner(user: CurrentUser | null) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  });
  client.setQueryData(queryKeys.currentUser, user);
  return render(
    <QueryClientProvider client={client}>
      <UnverifiedEmailBanner />
    </QueryClientProvider>,
  );
}

describe("UnverifiedEmailBanner", () => {
  it("已登录、邮箱未验证、账号正常：显示，写出邮箱，带重新发送与更改邮箱", () => {
    renderBanner({ ...USER, emailVerified: false });
    expect(
      screen.getByText("验证邮箱后，别人才能看到你的主页。验证邮件已发到 chen.yu@example.com。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "更改邮箱" })).toHaveAttribute(
      "href",
      "/settings/account#email",
    );
    expect(screen.getByText("请验证邮箱：别人现在看不到你的主页")).toBeInTheDocument();
  });

  it.each<[string, CurrentUser | null]>([
    ["已验证", USER],
    ["冷静期", { ...USER, emailVerified: false, status: "PENDING_DELETION" }],
    ["未登录", null],
  ])("%s：不渲染", (_label, user) => {
    const { container } = renderBanner(user);
    expect(container).toBeEmptyDOMElement();
  });
});
