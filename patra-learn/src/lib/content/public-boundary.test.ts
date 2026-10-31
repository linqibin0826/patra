import { describe, expect, it } from "vitest";
import { checkPublicBoundary } from "./public-boundary";

function check(text: string) {
  return checkPublicBoundary({ file: "content/entries/a.mdx", text });
}

function rules(text: string) {
  return check(text).map((i) => i.rule);
}

// 假凭据在运行时拼出来，避免整串字面量触发 gitleaks
const FAKE_SECRETS: [string, string][] = [
  ["私钥头", `-----BEGIN ${"RSA PRIVATE"} KEY-----`],
  ["GitHub 令牌", `ghp_${"a".repeat(36)}`],
  ["GitHub 细粒度令牌", `github_pat_${"b".repeat(40)}`],
  ["AWS 访问键", `AKIA${"A".repeat(16)}`],
  ["sk- 类 API key", `sk-ant-${"b".repeat(30)}`],
  ["Slack 令牌", `xoxb-${"1".repeat(12)}`],
  ["JWT", `eyJ${"x".repeat(12)}.eyJ${"y".repeat(12)}.${"z".repeat(16)}`],
  ["password 赋值", `password: ${"abc123"}def456gh`],
  ["API_KEY 赋值", `API_KEY=${"Zx9".repeat(5)}`],
];

describe("内网地址与主机名", () => {
  it.each([
    "100.64.1.2",
    "100.127.0.9",
    "foo.tail1234.ts.net",
    "10.1.2.3",
    "172.20.0.5",
    "192.168.1.10",
    "alice-mac-mini",
    "bob-macbook-pro",
  ])("%s 报 private-address", (text) => {
    expect(rules(`连到 ${text} 上`)).toEqual(["private-address"]);
  });

  it.each([
    "100.63.0.1",
    "100.128.0.1",
    "127.0.0.1",
    "localhost",
    "10.0.0.0/8",
    "192.168.0.0/16",
    "192.0.2.10",
    "198.51.100.7",
    "203.0.113.5",
    "部署在 Mac mini 上",
    "Spring Boot 4.1.2",
  ])("%s 放行", (text) => {
    expect(rules(text)).toEqual([]);
  });
});

describe("凭据", () => {
  it.each(FAKE_SECRETS)("%s 报 secret", (_name, text) => {
    expect(rules(`配置：\n${text}\n`)).toEqual(["secret"]);
  });

  it.each([
    "token: xxxxxxxx",
    // biome-ignore lint/suspicious/noTemplateCurlyInString: 测的就是 ${环境变量} 占位写法
    "password: ${DB_PASSWORD}",
    "secret: <你的密钥>",
    "password: changeme",
    "token 是一串随机字符",
  ])("%s 放行", (text) => {
    expect(rules(text)).toEqual([]);
  });

  it("说明里不回显完整凭据", () => {
    const secret = `ghp_${"a".repeat(36)}`;
    const [hit] = check(secret);
    expect(hit?.message).not.toContain(secret);
  });
});

describe("评审补的漏报与误报", () => {
  it.each([
    ["环境变量前缀", `REDIS_PASSWORD=${"abc123"}def456ghi`],
    ["下划线前缀", `client_secret: ${"Abc123"}Def456Gh`],
    ["下划线后缀", `SECRET_KEY=${"Zx9".repeat(5)}`],
    ["含符号的密码", `password: ${"Abc123!"}def456`],
  ])("%s 报 secret", (_name, text) => {
    expect(rules(text)).toEqual(["secret"]);
  });

  it("代码里的方法调用不算凭据", () => {
    expect(rules("String token = jwtService.issueV2(user);")).toEqual([]);
  });

  it.each([
    ["行尾带句号", "访问 192.168.1.5."],
    ["/32 是单个主机", "10.1.2.3/32"],
    ["URL 里的路径不是网段", "http://100.64.1.2/1"],
    ["中文系统默认主机名", "xxxdeMac-mini.local"],
    ["mDNS 名字", "my-box.local"],
  ])("%s 报 private-address", (_name, text) => {
    expect(rules(text)).toEqual(["private-address"]);
  });

  it.each(["我的 MacBook 上", "10.0.0.0/8 与 172.16.0.0/12"])("%s 放行", (text) => {
    expect(rules(text)).toEqual([]);
  });
});

describe("定位", () => {
  it("说明里带行号", () => {
    const [hit] = check("第一行\n第二行\n地址 10.1.2.3\n");
    expect(hit?.message).toContain("第 3 行");
  });

  it("同一行两处命中各报一条", () => {
    expect(rules("10.1.2.3 与 192.168.1.1")).toEqual(["private-address", "private-address"]);
  });
});
