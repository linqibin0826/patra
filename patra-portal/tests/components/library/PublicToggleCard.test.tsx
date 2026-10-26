import { screen, waitFor } from "@testing-library/react";
import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PublicToggleCard } from "@/components/library/PublicToggleCard";
import { setVisibility } from "@/lib/interaction/collection-actions";
import type { Collection } from "@/lib/interaction/schema";

vi.mock("@/lib/interaction/collection-actions", () => ({ setVisibility: vi.fn() }));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/library/9",
  useSearchParams: () => new URLSearchParams(),
}));

const set = vi.mocked(setVisibility);
const LINK = "http://mini/collections/9";

beforeEach(() => {
  set.mockReset();
});

function renderCard(options: {
  collection?: Collection;
  emailVerified?: boolean;
  profileVisibility?: "PUBLIC" | "SIGNED_IN" | "SELF" | null;
}) {
  return renderWithLibrary(
    <PublicToggleCard
      collection={options.collection ?? makeCollection()}
      emailVerified={options.emailVerified ?? true}
      email="chen.yu@example.com"
      profileVisibility={options.profileVisibility ?? "PUBLIC"}
      publicLink={LINK}
    />,
    { overview: makeOverview() },
  );
}

const toggle = () => screen.getByRole("switch", { name: "公开" });

describe("PublicToggleCard", () => {
  it("public.off", () => {
    renderCard({});
    expect(toggle()).not.toBeChecked();
    expect(screen.getByText("别人可以通过链接或你的主页看到这个收藏夹。")).toBeInTheDocument();
  });

  it("public.unverified：开关禁用，提示验证", () => {
    renderCard({ emailVerified: false });
    expect(toggle()).toBeDisabled();
    expect(
      screen.getByText("验证邮箱后才能公开收藏夹。验证邮件已发到 chen.yu@example.com。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /重新发送验证邮件/ })).toBeInTheDocument();
  });

  it("public.saving → public.on.public：设置中…，成功后显示链接框", async () => {
    let answer: (value: Awaited<ReturnType<typeof setVisibility>>) => void = () => {};
    set.mockReturnValue(new Promise((resolve) => (answer = resolve)));
    const { user } = renderCard({});
    await user.click(toggle());
    expect(screen.getByText("设置中…")).toBeInTheDocument();
    expect(toggle()).toBeDisabled();
    answer({ ok: true, collection: makeCollection({ visibility: "PUBLIC" }) });
    expect(
      await screen.findByText(
        "所有人都能看到，包括没有登录的访客；也会出现在你主页的「收藏夹」里。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "链接" })).toHaveValue(LINK);
    expect(set).toHaveBeenCalledWith("9", "PUBLIC");
  });

  it.each([
    ["SIGNED_IN", "你的主页设为仅登录用户可见，所以这个收藏夹也只有登录用户能看到。"],
    ["SELF", "你的主页设为仅自己可见，所以别人现在看不到这个收藏夹。"],
  ] as const)("public.on.%s：说明与更改主页可见性", (visibility, text) => {
    renderCard({
      collection: makeCollection({ visibility: "PUBLIC" }),
      profileVisibility: visibility,
    });
    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "更改主页可见性" })).toHaveAttribute(
      "href",
      "/settings/privacy",
    );
  });

  it.each([
    ["email-unverified", "验证邮箱后才能公开收藏夹。验证邮件已发到 chen.yu@example.com。"],
    ["account-restricted", "账号状态异常，暂时不能公开收藏夹。"],
    ["unavailable", "没有设置成功，请稍后再试。"],
  ] as const)("被拒 %s：回到关", async (reason, text) => {
    set.mockResolvedValue({ ok: false, reason });
    const { user } = renderCard({});
    await user.click(toggle());
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(toggle()).not.toBeChecked();
  });

  it("关掉公开：立即生效，不确认", async () => {
    set.mockResolvedValue({ ok: true, collection: makeCollection() });
    const { user } = renderCard({ collection: makeCollection({ visibility: "PUBLIC" }) });
    await user.click(toggle());
    expect(toggle()).not.toBeChecked();
    await waitFor(() => expect(set).toHaveBeenCalledWith("9", "PRIVATE"));
    expect(screen.getByText("别人可以通过链接或你的主页看到这个收藏夹。")).toBeInTheDocument();
  });

  it("关掉失败：回到开", async () => {
    set.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = renderCard({ collection: makeCollection({ visibility: "PUBLIC" }) });
    await user.click(toggle());
    expect(await screen.findByText("没有设置成功，请稍后再试。")).toBeInTheDocument();
    expect(toggle()).toBeChecked();
  });
});
