import { describe, expect, it } from "vitest";
import { linkedToast, linkNoticeAlert, loginNoticeAlert } from "@/lib/auth/messages";
import { oauthNoticeHref, parseOAuthNotice } from "@/lib/auth/oauth-notice";

describe("parseOAuthNotice", () => {
  it("取出合法的 oauth 与 provider", () => {
    expect(parseOAuthNotice({ oauth: "cancelled", provider: "github" })).toEqual({
      kind: "cancelled",
      provider: "github",
    });
  });

  it("参数出现多次时取第一个", () => {
    expect(parseOAuthNotice({ oauth: ["rejected", "banned"], provider: ["orcid", "x"] })).toEqual({
      kind: "rejected",
      provider: "orcid",
    });
  });

  it.each([
    [{}],
    [{ oauth: "cancelled" }],
    [{ provider: "github" }],
    [{ oauth: "evil", provider: "github" }],
    [{ oauth: "cancelled", provider: "evil" }],
    [{ oauth: "__proto__", provider: "github" }],
  ])("手工改坏的 %j 不显示", (params) => {
    expect(parseOAuthNotice(params)).toBeNull();
  });
});

describe("oauthNoticeHref", () => {
  it("没有查询串的路径", () => {
    expect(oauthNoticeHref("/login", { kind: "rejected", provider: "github" })).toBe(
      "/login?oauth=rejected&provider=github",
    );
  });

  it("保留路径原有的查询串", () => {
    expect(
      oauthNoticeHref("/login?next=%2Fpapers%2F1", { kind: "rejected", provider: "orcid" }),
    ).toBe("/login?next=%2Fpapers%2F1&oauth=rejected&provider=orcid");
  });
});

describe("登录页的提示", () => {
  it.each([
    ["cancelled", "info", "你取消了 GitHub 授权。可以再试一次，或换一种方式。"],
    [
      "rejected",
      "danger",
      "这次授权没有成功，可能是等得太久或换了浏览器。请重新点一次，或换一种方式。",
    ],
    ["unreachable", "danger", "暂时连不上 GitHub，请稍后再试，或换一种方式。"],
    ["banned", "danger", "该账号已被封禁，无法登录"],
    ["unavailable", "danger", "登录没有成功，请稍后再试"],
  ] as const)("%s", (kind, tone, text) => {
    expect(loginNoticeAlert({ kind, provider: "github" })).toEqual({ tone, text });
  });

  it("登录页不该收到的绑定结果：按服务错误说", () => {
    expect(loginNoticeAlert({ kind: "taken", provider: "github" })?.text).toBe(
      "登录没有成功，请稍后再试",
    );
  });
});

describe("设置页的提示", () => {
  it.each([
    ["cancelled", "info", "你取消了 ORCID 授权，没有绑定。"],
    [
      "rejected",
      "danger",
      "这次授权没有成功，可能是等得太久或换了账号，没有绑定任何账号。请重新点「绑定」。",
    ],
    ["unreachable", "danger", "暂时连不上 ORCID，没有绑定。请稍后再试。"],
    [
      "taken",
      "danger",
      "你刚才授权的 ORCID 账号已经绑定了另一个 Patra 账号，不能再绑到这里。如果那个账号也是你的，先登录它解绑，再回来绑定。",
    ],
    [
      "already-linked",
      "danger",
      "你已经绑定了一个 ORCID 账号，每家只能绑一个。要换账号，先解绑现在这个。",
    ],
    ["unavailable", "danger", "绑定没有成功，请稍后再试"],
  ] as const)("%s", (kind, tone, text) => {
    expect(linkNoticeAlert({ kind, provider: "orcid" })).toEqual({ tone, text });
  });

  it("绑定成功不是提示条，是 toast", () => {
    expect(linkNoticeAlert({ kind: "linked", provider: "orcid" })).toBeNull();
    expect(linkedToast("orcid")).toBe("已绑定 ORCID，以后可以用它登录。通知邮件已发到你的邮箱。");
  });
});
