// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  FIELD_MESSAGES,
  fieldErrorMessage,
  fieldErrorMessages,
  loginAlertMessage,
  REGISTER_UNAVAILABLE_MESSAGE,
  TOAST_MESSAGES,
} from "@/lib/auth/messages";

describe("fieldErrorMessage", () => {
  it.each([
    ["REQUIRED", "请输入邮箱"],
    ["INVALID_FORMAT", "邮箱格式不正确"],
    ["TOO_LONG", "邮箱格式不正确"],
    ["SOMETHING_NEW", "邮箱格式不正确"],
    [null, "邮箱格式不正确"],
  ])("邮箱的原因码 %j：%s", (code, message) => {
    expect(fieldErrorMessage("email", code)).toBe(message);
  });

  it.each([
    ["REQUIRED", "请输入密码"],
    ["TOO_SHORT", "密码至少 8 位"],
    ["TOO_LONG", "密码最长 64 位"],
    ["TOO_COMMON", "这个密码太常见，容易被猜到，请换一个"],
    ["INVALID_CHARACTER", "密码不符合要求"],
    ["SOMETHING_NEW", "密码不符合要求"],
    [null, "密码不符合要求"],
  ])("密码的原因码 %j：%s", (code, message) => {
    expect(fieldErrorMessage("password", code)).toBe(message);
  });

  // 普通对象按原因码查表时，`constructor` 这类名字会查到对象原型上的属性
  it("原因码和对象原型上的属性同名时，仍是该字段的通用错误", () => {
    expect(fieldErrorMessage("password", "constructor")).toBe("密码不符合要求");
  });
});

describe("fieldErrorMessages", () => {
  it("同一字段只取第一条，email、password 以外的字段丢掉", () => {
    expect(
      fieldErrorMessages([
        { field: "deviceId", code: "REQUIRED" },
        { field: "password", code: "TOO_SHORT" },
        { field: "password", code: "REQUIRED" },
        { field: "email", code: null },
      ]),
    ).toEqual({ password: "密码至少 8 位", email: "邮箱格式不正确" });
  });

  it("一条都对不上 email、password 时返回空对象", () => {
    expect(fieldErrorMessages([{ field: "deviceId", code: "REQUIRED" }])).toEqual({});
  });
});

describe("loginAlertMessage", () => {
  it.each([
    [{ ok: false, reason: "bad-credentials" } as const, "邮箱或密码错误"],
    [{ ok: false, reason: "banned" } as const, "该账号已被封禁，无法登录"],
    [{ ok: false, reason: "unavailable" } as const, "登录没有成功，请稍后再试"],
  ])("%j：%s", (failure, message) => {
    expect(loginAlertMessage(failure)).toBe(message);
  });

  it.each([
    [0, "尝试次数过多，请 1 分钟后再试"],
    [59, "尝试次数过多，请 1 分钟后再试"],
    [60, "尝试次数过多，请 1 分钟后再试"],
    [61, "尝试次数过多，请 2 分钟后再试"],
    [null, "尝试次数过多，请稍后再试"],
  ])("限流，等待 %j 秒：%s", (retryAfterSeconds, message) => {
    expect(loginAlertMessage({ ok: false, reason: "throttled", retryAfterSeconds })).toBe(message);
  });
});

describe("其余文案", () => {
  it("注册失败的提示条", () => {
    expect(REGISTER_UNAVAILABLE_MESSAGE).toBe("注册没有成功，请稍后再试");
  });

  it("三条成功提示", () => {
    expect(TOAST_MESSAGES).toEqual({
      loggedIn: "已登录",
      registered: "注册成功，已登录",
      loggedOut: "已退出登录",
    });
  });

  it("前端校验用的字段文案", () => {
    expect(FIELD_MESSAGES).toMatchObject({
      emailRequired: "请输入邮箱",
      emailInvalid: "邮箱格式不正确",
      passwordRequired: "请输入密码",
      passwordTooShort: "密码至少 8 位",
      passwordTooLong: "密码最长 64 位",
      confirmRequired: "请再输入一次密码",
      confirmMismatch: "两次输入的密码不一致",
      emailTaken: "该邮箱已注册，",
      emailTakenLink: "去登录",
    });
  });
});
