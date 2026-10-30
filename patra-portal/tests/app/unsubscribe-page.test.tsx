import { describe, expect, it } from "vitest";
import UnsubscribePage, { metadata } from "@/app/unsubscribe/page";
import { UnsubscribeLanding } from "@/components/landing/UnsubscribeLanding";

describe("/unsubscribe", () => {
  it("标题是退订提醒邮件 · Patra，不进搜索引擎；页面就是退订落地页", () => {
    expect(metadata).toMatchObject({ title: "退订提醒邮件 · Patra", robots: { index: false } });
    expect(UnsubscribePage().type).toBe(UnsubscribeLanding);
  });
});
