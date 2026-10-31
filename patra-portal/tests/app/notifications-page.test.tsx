import { USER } from "@tests/fixtures/user";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  unstable_rethrow: vi.fn(),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));

import HelpfulPage from "@/app/notifications/helpful/page";
import { metadata } from "@/app/notifications/layout";
import MentionsPage from "@/app/notifications/mentions/page";
import RepliesPage from "@/app/notifications/page";
import { NotificationInbox } from "@/components/notifications/NotificationInbox";
import { lookupSession } from "@/lib/auth/current-user";

beforeEach(() => {
  vi.mocked(lookupSession).mockResolvedValue({ status: "authenticated", user: USER });
});

describe("/notifications", () => {
  it("未登录去登录页，next 是这一页", async () => {
    vi.mocked(lookupSession).mockResolvedValue({ status: "anonymous" });
    await expect(RepliesPage()).rejects.toThrow("NEXT_REDIRECT:/login?next=%2Fnotifications");
    await expect(MentionsPage()).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fnotifications%2Fmentions",
    );
    await expect(HelpfulPage()).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fnotifications%2Fhelpful",
    );
  });

  it("已登录：三个地址各是一个标签", async () => {
    const replies = await RepliesPage();
    const mentions = await MentionsPage();
    const helpful = await HelpfulPage();
    expect(replies.type).toBe(NotificationInbox);
    expect(replies.props).toEqual({ tab: "replies" });
    expect(mentions.props).toEqual({ tab: "mentions" });
    expect(helpful.type).toBe(NotificationInbox);
    expect(helpful.props).toEqual({ tab: "helpful" });
  });

  it("标题「提醒 · Patra」，不收录", () => {
    expect(metadata).toMatchObject({ title: "提醒 · Patra", robots: { index: false } });
  });
});
