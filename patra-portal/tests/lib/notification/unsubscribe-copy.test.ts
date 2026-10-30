import { describe, expect, it } from "vitest";
import { doneDescription, doneTitle, kindLine } from "@/lib/notification/unsubscribe-copy";

describe("kindLine（信息表「邮件」一行）", () => {
  it("逐条邮件：类别 + 这一类当前的档位", () => {
    expect(kindLine({ category: "MENTION", reply: "INSTANT", mention: "INSTANT" })).toBe(
      "「@ 我的」提醒 · 离站时逐条",
    );
    expect(kindLine({ category: "REPLY", reply: "DIGEST", mention: "INSTANT" })).toBe(
      "「回复我的」提醒 · 每日汇总",
    );
  });

  it("每日汇总：列出此刻处于汇总的类别", () => {
    expect(kindLine({ category: "DIGEST", reply: "INSTANT", mention: "DIGEST" })).toBe(
      "每日汇总 · 包括「@ 我的」",
    );
    expect(kindLine({ category: "DIGEST", reply: "DIGEST", mention: "DIGEST" })).toBe(
      "每日汇总 · 包括「回复我的」「@ 我的」",
    );
  });
});

describe("doneTitle", () => {
  it("已退订 / 已经退订过了", () => {
    expect(doneTitle("MENTION", false)).toBe("已退订「@ 我的」提醒邮件");
    expect(doneTitle("DIGEST", false)).toBe("已退订每日汇总邮件");
    expect(doneTitle("REPLY", true)).toBe("「回复我的」提醒邮件已经退订过了");
    expect(doneTitle("DIGEST", true)).toBe("每日汇总邮件已经退订过了");
  });
});

describe("doneDescription", () => {
  it("逐条邮件：简报里的两句固定说明", () => {
    const modes = { reply: "OFF", mention: "INSTANT" } as const;
    expect(doneDescription("REPLY", modes, ["REPLY"])).toBe(
      "以后有人回复你、评论你的投稿经验，不再给这个地址发邮件；提醒页里照常能看到。",
    );
    expect(doneDescription("MENTION", modes, null)).toBe(
      "以后有人在评论里 @ 你，不再给这个地址发邮件；提醒页里照常能看到。",
    );
  });

  it("每日汇总关掉两类", () => {
    expect(doneDescription("DIGEST", { reply: "OFF", mention: "OFF" }, ["REPLY", "MENTION"])).toBe(
      "「回复我的」「@ 我的」都改为不发邮件，提醒页里照常能看到。",
    );
  });

  it("每日汇总只关掉一类：另一类还开着就写它的档位", () => {
    expect(doneDescription("DIGEST", { reply: "INSTANT", mention: "OFF" }, ["MENTION"])).toBe(
      "「@ 我的」改为不发邮件，提醒页里照常能看到；「回复我的」仍是离站时逐条。",
    );
    expect(doneDescription("DIGEST", { reply: "OFF", mention: "OFF" }, ["MENTION"])).toBe(
      "「@ 我的」改为不发邮件，提醒页里照常能看到。",
    );
  });

  it("每日汇总已经退订过：按此刻是关的类别写；两类都是离站时逐条时另一句", () => {
    expect(doneDescription("DIGEST", { reply: "OFF", mention: "INSTANT" }, null)).toBe(
      "「回复我的」改为不发邮件，提醒页里照常能看到；「@ 我的」仍是离站时逐条。",
    );
    expect(doneDescription("DIGEST", { reply: "INSTANT", mention: "INSTANT" }, null)).toBe(
      "「回复我的」「@ 我的」现在都是离站时逐条，不会再收到每日汇总。",
    );
  });
});
