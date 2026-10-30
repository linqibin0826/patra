// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  authPageHref,
  deletionPendingHref,
  forgotPasswordHref,
  isFocusPage,
  safeNextPath,
} from "@/lib/auth/next-path";

describe("safeNextPath", () => {
  it.each([
    ["/papers?q=GLP-1", "/papers?q=GLP-1"],
    ["/journals/7", "/journals/7"],
    ["/", "/"],
    ["/papers?q=GLP-1#results", "/papers?q=GLP-1"],
  ])("站内地址 %j 返回 %j，# 之后的部分去掉", (raw, expected) => {
    expect(safeNextPath(raw)).toBe(expected);
  });

  it("返回解析后的路径：点号段已经合并", () => {
    expect(safeNextPath("/papers/../journals?page=2")).toBe("/journals?page=2");
  });

  it.each([
    "//evil.com",
    "/\\evil.com",
    "/\t/evil.com",
    "/\n/evil.com",
    "https://evil.com",
    "javascript:alert(1)",
    "papers",
  ])("站外或不以单个 / 开头的 %j 回到 /", (raw) => {
    expect(safeNextPath(raw)).toBe("/");
  });

  it.each(["/login", "/login/", "/register?x=1", "/papers/../login", "/register//"])(
    "登录页、注册页本身 %j 回到 /",
    (raw) => {
      expect(safeNextPath(raw)).toBe("/");
    },
  );

  it.each(["", null, undefined])("%j 回到 /", (raw) => {
    expect(safeNextPath(raw)).toBe("/");
  });

  // 编码过的斜杠和反斜杠解析后仍是本站的一个路径（打开是 404 页），不会变成站外地址
  it.each(["/%2F/evil.com", "/%5Cevil.com"])("编码过的 %j 仍是站内路径", (raw) => {
    expect(safeNextPath(raw)).toBe(raw);
  });
});

describe("authPageHref", () => {
  it("next 是站内地址时编码后放进查询串", () => {
    expect(authPageHref("/login", "/papers?q=GLP-1")).toBe("/login?next=%2Fpapers%3Fq%3DGLP-1");
    expect(authPageHref("/register", "/journals/7")).toBe("/register?next=%2Fjournals%2F7");
  });

  it.each(["/", "//evil.com", "/login"])("next 校验后是 / 时（%j）不带参数", (next) => {
    expect(authPageHref("/login", next)).toBe("/login");
    expect(authPageHref("/register", next)).toBe("/register");
  });
});

describe("deletionPendingHref", () => {
  it("带上经过校验的 next", () => {
    expect(deletionPendingHref("/papers?q=a")).toBe(
      "/account/deletion-pending?next=%2Fpapers%3Fq%3Da",
    );
  });

  it("next 是首页或不合法时不带参数", () => {
    expect(deletionPendingHref("/")).toBe("/account/deletion-pending");
    expect(deletionPendingHref("//evil.com")).toBe("/account/deletion-pending");
  });
});

describe("专注布局的页面", () => {
  it.each([
    "/login",
    "/register",
    "/forgot-password",
    "/verify-email",
    "/reset-password",
    "/confirm-email-change",
    "/revert-email-change",
    "/account/deletion-pending",
    "/unsubscribe",
    "/unsubscribe/one-click",
  ])("%s 不能作为 next", (page) => {
    expect(safeNextPath(page)).toBe("/");
    expect(safeNextPath(`${page}?next=%2Fpapers`)).toBe("/");
  });

  it("末尾带斜杠也算", () => {
    expect(isFocusPage("/account/deletion-pending/")).toBe(true);
  });

  it("设置页、首页不是专注页", () => {
    expect(isFocusPage("/settings/account")).toBe(false);
    expect(isFocusPage("/")).toBe(false);
  });
});

describe("forgotPasswordHref", () => {
  it("带上校验后的 next；是首页时不带", () => {
    expect(forgotPasswordHref("/papers?q=a")).toBe("/forgot-password?next=%2Fpapers%3Fq%3Da");
    expect(forgotPasswordHref("/")).toBe("/forgot-password");
    expect(forgotPasswordHref("/login")).toBe("/forgot-password");
  });
});

describe("safeNextPath：点路径段折叠后变成协议相对地址", () => {
  it.each(["/..//evil.com", "/%2e%2e//evil.com", "/./\\evil.com", "/a/..//evil.com"])(
    "%j 解析后是 //evil.com：一律回首页",
    (raw) => {
      expect(safeNextPath(raw)).toBe("/");
    },
  );

  it("取消注销页、登录页的链接也不会带出去", () => {
    expect(deletionPendingHref("/..//evil.com")).toBe("/account/deletion-pending");
    expect(authPageHref("/login", "/..//evil.com")).toBe("/login");
  });
});
