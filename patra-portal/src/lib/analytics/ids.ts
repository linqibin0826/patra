import { v7 } from "uuid";

/// 契约里 `page_path` 的上限
const MAX_PATH_LENGTH = 512;

/// 事件、会话、列表、检索的 ID：小写 UUIDv7。按时间有序，ClickHouse 按它排序更友好（埋点方案 §4.2）
export function newId(): string {
  return v7();
}

/// 设备标识：32 字节随机数的 base64url（无填充，43 个字符），与登录时 identity 用的格式相同。
/// 只用 Web Crypto 与 `btoa`，浏览器、Node、proxy 里都能跑
export function newDeviceId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
}

/// 路径超过契约上限时截断（超长的整条会进 dlq）
export function truncatePath(path: string): string {
  return path.length > MAX_PATH_LENGTH ? path.slice(0, MAX_PATH_LENGTH) : path;
}
