import { makeThread } from "@tests/fixtures/discussion";
import { makeExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import { threadPageSchema, threadSchema } from "@/lib/interaction/discussion-schema";
import { experienceSchema } from "@/lib/interaction/experience-schema";

describe("公开计数字段", () => {
  it("should_parse_reply_count_and_last_reply_at_on_thread", () => {
    const thread = { ...makeThread(), replyCount: 2, lastReplyAt: "2026-10-30T03:00:00Z" };
    expect(threadSchema.parse(thread)).toMatchObject({
      replyCount: 2,
      lastReplyAt: "2026-10-30T03:00:00Z",
    });
  });

  it("should_reject_thread_without_reply_count", () => {
    const { replyCount: _, ...thread } = { ...makeThread(), replyCount: 0 };
    expect(threadSchema.safeParse(thread).success).toBe(false);
  });

  it("should_accept_total_as_number_or_string", () => {
    const page = { items: [], nextCursor: null };
    expect(threadPageSchema.parse({ ...page, total: 7 }).total).toBe(7);
    expect(threadPageSchema.parse({ ...page, total: "7" }).total).toBe(7);
  });

  it("should_reject_experience_without_reply_count", () => {
    const { replyCount: _, ...experience } = { ...makeExperience(), replyCount: 0 };
    expect(experienceSchema.safeParse(experience).success).toBe(false);
  });
});
