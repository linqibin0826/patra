// patra-learn/src/lib/content/public-boundary.ts
// 公开边界（决策 E）：学习站与仓库都公开，内容里不许出现凭据、tailnet / 内网地址与本机主机名。
// 这里只写模式，不写任何真实地址或主机名（本文件同样公开）。gitleaks 照旧兜底密钥。
import type { ContentIssue, RawFile } from "./types";

interface Hit {
  rule: "secret" | "private-address";
  kind: string;
  start: number;
  end: number;
  text: string;
}

/// 行尾句号不吞进地址；网段写法（/0～/31，且不在 URL 主机位置）在 isNetworkNotation 里放行
const IPV4 = /(?<![\d.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?!\d|\.\d)/g;

/// `10.0.0.0/8` 这种网段写法；`/32` 是单个主机，`http://10.1.2.3/1` 是 URL 路径，都不算
function isNetworkNotation(line: string, start: number, end: number): boolean {
  const prefix = line.slice(end).match(/^\/(\d{1,2})(?!\d)/);
  return prefix !== null && Number(prefix[1]) <= 31 && !line.slice(0, start).endsWith("://");
}

/// tailnet（100.64.0.0/10）与 RFC 1918 内网段；网段写法（带 /前缀）在正则里已排除
function privateRange(a: number, b: number): string | undefined {
  if (a === 100 && b >= 64 && b <= 127) return "tailnet IP";
  if (a === 10) return "内网 IP";
  if (a === 172 && b >= 16 && b <= 31) return "内网 IP";
  if (a === 192 && b === 168) return "内网 IP";
  return undefined;
}

const NAME_PATTERNS: [string, RegExp][] = [
  ["tailnet 主机名", /\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.ts\.net\b/gi],
  // 连字符主机名（xxx-mac-mini）与中文系统默认名（xxxdeMac-mini）；单独的 Mac mini / MacBook 放行
  ["本机主机名", /\b[a-z0-9]+(?:-[a-z0-9]+)*-?mac(?:-?mini|book(?:-[a-z0-9]+)*)\b/gi],
  ["本机主机名", /\b[a-z0-9-]+\.local\b/gi],
];

const SECRET_PATTERNS: [string, RegExp][] = [
  ["私钥", /-----BEGIN [A-Z ]*PRIVATE KEY-----/g],
  ["GitHub 令牌", /\bgh[pousr]_[A-Za-z0-9]{36,}\b/g],
  ["GitHub 令牌", /\bgithub_pat_[A-Za-z0-9_]{22,}/g],
  ["AWS 访问键", /\bAKIA[0-9A-Z]{16}\b/g],
  ["API key", /\bsk-(?:ant-)?[A-Za-z0-9_-]{20,}/g],
  ["Slack 令牌", /\bxox[abprs]-[A-Za-z0-9-]{10,}/g],
  ["JWT", /\beyJ[\w-]{10,}\.[\w-]{10,}\.[\w-]{10,}/g],
];

/// `password: 值`、`REDIS_PASSWORD=值` 这类赋值；关键词可带 `_` / `-` 前后缀。
/// 值要像真值（≥12 位且同时含字母与数字），示意值放行；值里不含 `.` 与括号，避免把方法调用当凭据
const ASSIGNMENT =
  /(?<![A-Za-z0-9])(?:[A-Za-z0-9]+[_-])*(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)(?:[_-][A-Za-z0-9]+)*["']?\s*[:=]\s*["']?([A-Za-z0-9+/=_\-!@#%^&*~]{12,})/gi;

function scanLine(line: string): Hit[] {
  const hits: Hit[] = [];
  const add = (hit: Hit) => {
    if (hits.some((h) => hit.start < h.end && h.start < hit.end)) return;
    hits.push(hit);
  };
  for (const [kind, pattern] of SECRET_PATTERNS) {
    for (const m of line.matchAll(pattern)) {
      add({ rule: "secret", kind, start: m.index, end: m.index + m[0].length, text: m[0] });
    }
  }
  for (const m of line.matchAll(ASSIGNMENT)) {
    const value = m[1] ?? "";
    if (/[A-Za-z]/.test(value) && /\d/.test(value)) {
      add({
        rule: "secret",
        kind: "凭据赋值",
        start: m.index,
        end: m.index + m[0].length,
        text: value,
      });
    }
  }
  for (const m of line.matchAll(IPV4)) {
    const [a, b] = [Number(m[1]), Number(m[2])];
    const kind = privateRange(a, b);
    if (kind && !isNetworkNotation(line, m.index, m.index + m[0].length)) {
      add({
        rule: "private-address",
        kind,
        start: m.index,
        end: m.index + m[0].length,
        text: m[0],
      });
    }
  }
  for (const [kind, pattern] of NAME_PATTERNS) {
    for (const m of line.matchAll(pattern)) {
      add({
        rule: "private-address",
        kind,
        start: m.index,
        end: m.index + m[0].length,
        text: m[0],
      });
    }
  }
  return hits.sort((x, y) => x.start - y.start);
}

/// 凭据只回显前 4 位
function preview(hit: Hit): string {
  return hit.rule === "secret" ? `${hit.text.slice(0, 4)}…` : hit.text;
}

const ADVICE: Record<Hit["rule"], string> = {
  // biome-ignore lint/suspicious/noTemplateCurlyInString: 这里就是要原样显示 ${环境变量} 写法
  secret: "删掉凭据，改成 ${环境变量} 或 <占位> 写法",
  "private-address":
    "改成 <mini 的地址> 这类占位；具体主机例子用文档专用地址 192.0.2.x，网段写成 10.0.0.0/8",
};

/// 扫一个内容文件的原文，每处命中报一条
export function checkPublicBoundary(raw: RawFile): ContentIssue[] {
  const issues: ContentIssue[] = [];
  raw.text.split(/\r?\n/).forEach((line, index) => {
    for (const hit of scanLine(line)) {
      issues.push({
        rule: hit.rule,
        file: raw.file,
        message: `第 ${index + 1} 行出现${hit.kind}（${preview(hit)}）：${ADVICE[hit.rule]}`,
      });
    }
  });
  return issues;
}
