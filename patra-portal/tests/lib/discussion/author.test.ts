import { makeCard, makeComment } from "@tests/fixtures/discussion";
import { describe, expect, it } from "vitest";
import { authorView, isSelfReply, replyToLabel } from "@/lib/discussion/author";

describe("authorView（spec 6.4 署名表）", () => {
  it("匿名作者，本人看", () => {
    expect(authorView({ author: makeCard(), anonymous: true, mine: true })).toEqual({
      kind: "anonymous",
      self: true,
    });
  });

  it("匿名作者，别人看", () => {
    expect(authorView({ author: null, anonymous: true, mine: false })).toEqual({
      kind: "anonymous",
      self: false,
    });
  });

  it.each([
    ["identity 省略", null],
    ["已注销", makeCard({ state: "DELETED", username: null, displayName: null })],
  ])("已注销用户：%s", (_, author) => {
    expect(authorView({ author, anonymous: false, mine: false })).toEqual({ kind: "deleted" });
  });

  it("主页不可见：hidden", () => {
    const card = makeCard({ username: null, avatarUrl: null, profileVisible: false });
    expect(authorView({ author: card, anonymous: false, mine: false })).toEqual({
      kind: "hidden",
      card,
    });
  });

  it("可见", () => {
    const card = makeCard();
    expect(authorView({ author: card, anonymous: false, mine: false })).toEqual({
      kind: "visible",
      card,
    });
  });
});

describe("replyToLabel", () => {
  it.each([
    [null, null],
    [
      { deleted: true, anonymous: false, author: null },
      { text: "回复已删除的评论", href: null },
    ],
    [
      { deleted: false, anonymous: true, author: null },
      { text: "回复 匿名作者", href: null },
    ],
    [
      { deleted: false, anonymous: false, author: null },
      { text: "回复 已注销用户", href: null },
    ],
    [
      {
        deleted: false,
        anonymous: false,
        author: makeCard({ state: "DELETED", username: null, displayName: null }),
      },
      { text: "回复 已注销用户", href: null },
    ],
    [
      { deleted: false, anonymous: false, author: makeCard({ userId: "9", username: "zhou-min" }) },
      { text: "回复 @zhou-min", href: "/u/zhou-min" },
    ],
    [
      {
        deleted: false,
        anonymous: false,
        author: makeCard({ username: null, displayName: "周敏" }),
      },
      { text: "回复 @周敏", href: null },
    ],
  ])("%o → %o", (replyTo, expected) => {
    expect(replyToLabel(replyTo)).toEqual(expected);
  });
});

describe("isSelfReply：自己回复自己（当作补充，不显示「回复 @X」）", () => {
  const reply = (overrides: Parameters<typeof makeComment>[0]) =>
    makeComment({ commentId: "5", parentId: "2", ...overrides });

  it("被回复的与这条是同一个人", () => {
    expect(
      isSelfReply(reply({ replyTo: { deleted: false, anonymous: false, author: makeCard() } })),
    ).toBe(true);
  });

  it("回复别人", () => {
    expect(
      isSelfReply(
        reply({ replyTo: { deleted: false, anonymous: false, author: makeCard({ userId: "9" }) } }),
      ),
    ).toBe(false);
  });

  it("匿名作者回复自己（别人看：两边都没有名片）", () => {
    expect(
      isSelfReply(
        reply({
          author: null,
          anonymous: true,
          replyTo: { deleted: false, anonymous: true, author: null },
        }),
      ),
    ).toBe(true);
  });

  it.each([
    ["被回复的已删除", { deleted: true, anonymous: false, author: null }],
    ["没有回复对象", null],
  ])("%s：不是", (_, replyTo) => {
    expect(isSelfReply(reply({ replyTo }))).toBe(false);
  });
});
