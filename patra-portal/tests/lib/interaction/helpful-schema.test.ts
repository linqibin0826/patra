import { makeComment } from "@tests/fixtures/discussion";
import { makeExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import { commentSchema } from "@/lib/interaction/discussion-schema";
import { experiencePageSchema, experienceSchema } from "@/lib/interaction/experience-schema";
import { helpfulResultSchema, helpfulTargetSchema } from "@/lib/interaction/helpful-schema";

describe("有帮助相关的响应字段", () => {
  it("评论带有帮助数与「我点过没有」，没有会话时为 null", () => {
    const parsed = commentSchema.parse({ ...makeComment(), helpfulCount: 3, helpful: null });
    expect(parsed).toMatchObject({ helpfulCount: 3, helpful: null });
  });

  it("评论缺有帮助数时不合格", () => {
    const { helpfulCount: _, ...rest } = makeComment();
    expect(commentSchema.safeParse(rest).success).toBe(false);
  });

  it("经验带有帮助数与「我点过没有」", () => {
    const parsed = experienceSchema.parse({ ...makeExperience(), helpfulCount: 2, helpful: true });
    expect(parsed).toMatchObject({ helpfulCount: 2, helpful: true });
  });

  it("期刊经验列表的总条数：数字与数字字符串都收", () => {
    const page = { items: [makeExperience()], nextCursor: null };
    expect(experiencePageSchema.parse({ ...page, total: 18 }).total).toBe(18);
    expect(experiencePageSchema.parse({ ...page, total: "18" }).total).toBe(18);
  });

  it("点 / 取消的返回", () => {
    expect(helpfulResultSchema.parse({ helpful: true, helpfulCount: 4 })).toEqual({
      helpful: true,
      helpfulCount: 4,
    });
  });

  it("目标只有评论与经验两种", () => {
    expect(helpfulTargetSchema.safeParse({ type: "comment", id: "5" }).success).toBe(true);
    expect(helpfulTargetSchema.safeParse({ type: "publication", id: "5" }).success).toBe(false);
  });
});
