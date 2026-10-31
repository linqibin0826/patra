import { describe, expect, it } from "vitest";
import { readUnsubscribeFragment } from "@/lib/notification/unsubscribe-link";

const TOKEN = "Ab3_-".repeat(8).concat("xyz");

describe("readUnsubscribeFragment", () => {
  it("片段里的令牌与类别", () => {
    expect(readUnsubscribeFragment(`#token=${TOKEN}&category=DIGEST`)).toEqual({
      token: TOKEN,
      category: "DIGEST",
    });
    expect(readUnsubscribeFragment(`#category=REPLY&token=${TOKEN}`)).toEqual({
      token: TOKEN,
      category: "REPLY",
    });
  });

  it.each([
    ["缺类别", `#token=${TOKEN}`],
    ["类别小写", `#token=${TOKEN}&category=reply`],
    ["链接里不会有 ALL", `#token=${TOKEN}&category=ALL`],
    ["缺令牌", "#category=REPLY"],
    ["令牌少一个字符", `#token=${TOKEN.slice(1)}&category=REPLY`],
    ["没有片段", ""],
  ])("%s：null", (_, hash) => {
    expect(readUnsubscribeFragment(hash)).toBeNull();
  });
});
