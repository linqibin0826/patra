import { describe, expect, it } from "vitest";
import { pageTypeOf } from "@/lib/analytics/page-type";

describe("pageTypeOf", () => {
  it.each([
    ["/", "home"],
    ["/papers", "search"],
    ["/papers/", "search"],
    ["/papers/42", "article"],
    ["/journals", "journal"],
    ["/journals/7", "journal"],
    ["/library", "library"],
    ["/library/unfiled", "library"],
    ["/library/123", "library"],
    ["/collections/9", "collection"],
    ["/u/wangli", "profile"],
    ["/u/wangli/collections", "profile"],
    ["/settings/privacy", "settings"],
    ["/account/deletion-pending", "settings"],
    ["/notifications", "notifications"],
    ["/notifications/mentions", "notifications"],
    ["/posts/5", "post"],
    ["/history", "history"],
    ["/privacy", "privacy"],
    ["/login", "auth"],
    ["/register", "auth"],
    ["/forgot-password", "auth"],
    ["/reset-password", "auth"],
    ["/verify-email", "auth"],
    ["/confirm-email-change", "auth"],
    ["/revert-email-change", "auth"],
    ["/missing/paper", "other"],
    ["/unsubscribe", "other"],
    ["/papers/42/extra", "other"],
  ] as const)("%s → %s", (path, type) => {
    expect(pageTypeOf(path)).toBe(type);
  });
});
