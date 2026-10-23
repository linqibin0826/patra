// @vitest-environment node
import { describe, expect, it } from "vitest";
import type { z } from "zod";
import { emailChangeSchema, loginSchema, registerSchema } from "@/lib/auth/form-schemas";

/// 每个字段的第一条错误：表单只显示这一条（RHF 的 zodResolver 默认也只取第一条）
function firstErrors(schema: z.ZodType, values: unknown): Record<string, string> {
  const result = schema.safeParse(values);
  const errors: Record<string, string> = {};
  for (const issue of result.success ? [] : result.error.issues) {
    errors[issue.path.join(".")] ??= issue.message;
  }
  return errors;
}

/// 64 + 1 + 185 + 4 = 254 个字符的合法邮箱
const EMAIL_254 = `${"a".repeat(64)}@${"b".repeat(185)}.com`;
const VALID = { email: "reader@example.com", password: "correct horse" };
const USERNAME = { username: "reader" };

describe("邮箱（两页相同）", () => {
  it("去掉首尾空白后提交", () => {
    expect(loginSchema.parse({ ...VALID, email: "  reader@example.com\t" }).email).toBe(
      "reader@example.com",
    );
  });

  it.each(["", "   "])("去掉空白后为空（%j）：请输入邮箱", (email) => {
    expect(firstErrors(loginSchema, { ...VALID, email })).toEqual({ email: "请输入邮箱" });
  });

  it.each(["reader", "reader@", "reader@example", "读者@example.com"])(
    "不符合 z.email()（%j）：邮箱格式不正确",
    (email) => {
      expect(firstErrors(loginSchema, { ...VALID, email })).toEqual({ email: "邮箱格式不正确" });
    },
  );

  it("254 个字符可以，255 个字符是「邮箱格式不正确」", () => {
    expect(firstErrors(loginSchema, { ...VALID, email: EMAIL_254 })).toEqual({});
    expect(firstErrors(loginSchema, { ...VALID, email: `a${EMAIL_254}` })).toEqual({
      email: "邮箱格式不正确",
    });
  });
});

describe("登录页的密码", () => {
  it("只要求非空，不去空格、不限长度", () => {
    expect(firstErrors(loginSchema, { ...VALID, password: "" })).toEqual({
      password: "请输入密码",
    });
    expect(loginSchema.parse({ ...VALID, password: " 1 " }).password).toBe(" 1 ");
    expect(firstErrors(loginSchema, { ...VALID, password: "a".repeat(100) })).toEqual({});
  });
});

describe("注册页的密码", () => {
  const register = (password: string, confirmPassword = password) =>
    firstErrors(registerSchema, { email: VALID.email, ...USERNAME, password, confirmPassword });

  it("为空：请输入密码", () => {
    expect(register("", "x")).toMatchObject({ password: "请输入密码" });
  });

  it("按码点计长度：汉字和表情各算 1 位，8 位、64 位两个边界都可以", () => {
    expect(register("密码密码密码密码")).toEqual({});
    expect(register("😀".repeat(8))).toEqual({});
    expect(register("a".repeat(64))).toEqual({});
    // 33 个表情是 66 个 UTF-16 单元，按码点只有 33 位
    expect(register("😀".repeat(33))).toEqual({});
  });

  it("少于 8 位：密码至少 8 位（7 个表情按 UTF-16 单元算是 14 位，也要拦）", () => {
    expect(register("1234567")).toEqual({ password: "密码至少 8 位" });
    expect(register("😀".repeat(7))).toEqual({ password: "密码至少 8 位" });
  });

  it("多于 64 位：密码最长 64 位", () => {
    expect(register("a".repeat(65))).toEqual({ password: "密码最长 64 位" });
  });

  it("不去空格：首尾的空格算在长度里", () => {
    expect(register("       1")).toEqual({});
  });
});

describe("注册页的用户名", () => {
  const base = {
    email: "reader@example.com",
    password: "correct horse",
    confirmPassword: "correct horse",
  };

  it("为空：请输入用户名", () => {
    expect(firstErrors(registerSchema, { ...base, username: "" }).username).toBe("请输入用户名");
  });

  it("不合规时按原因码出文案", () => {
    expect(firstErrors(registerSchema, { ...base, username: "12345" }).username).toBe(
      "用户名不能全是数字",
    );
  });

  it("去掉首尾空白、转小写后提交", () => {
    const parsed = registerSchema.safeParse({ ...base, username: " Chen-Yu " });
    expect(parsed.success && parsed.data.username).toBe("chen-yu");
  });
});

describe("注册页的确认密码", () => {
  const values = { email: VALID.email, ...USERNAME, password: "correct horse" };

  it("为空：请再输入一次密码", () => {
    expect(firstErrors(registerSchema, { ...values, confirmPassword: "" })).toEqual({
      confirmPassword: "请再输入一次密码",
    });
  });

  it("和密码不同：两次输入的密码不一致", () => {
    expect(firstErrors(registerSchema, { ...values, confirmPassword: "correct horse " })).toEqual({
      confirmPassword: "两次输入的密码不一致",
    });
  });

  it("其他字段有错时也照样比对", () => {
    expect(firstErrors(registerSchema, { ...values, email: "", confirmPassword: "wrong" })).toEqual(
      { email: "请输入邮箱", confirmPassword: "两次输入的密码不一致" },
    );
  });
});

describe("emailChangeSchema", () => {
  const schema = emailChangeSchema("Chen.Yu@example.com");
  const message = (newEmail: string) => {
    const result = schema.safeParse({ newEmail });
    return result.success ? null : result.error.issues[0]?.message;
  };

  it("空、格式不对、与当前相同（忽略大小写）各有说法", () => {
    expect(message("  ")).toBe("请输入邮箱");
    expect(message("not-an-email")).toBe("邮箱格式不正确");
    expect(message(" chen.yu@EXAMPLE.com ")).toBe("新邮箱和现在的一样");
  });

  it("合规时解析为去空白后的值", () => {
    expect(schema.parse({ newEmail: " new@example.com " })).toEqual({
      newEmail: "new@example.com",
    });
  });
});
