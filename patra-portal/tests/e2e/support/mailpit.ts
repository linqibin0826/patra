/// 开发环境的邮件收集器 Mailpit（mini 上 patra-core 栈）。邮件里的链接域名是 mini 配置的门户，测试只取令牌或路径，
/// 拼本机的落地页地址打开。mini 平时经真实服务商发信，跑读邮件的冒烟前要先把 notification 切回 Mailpit
/// （挪开 mini 上的 `~/.patra/secrets/mail.env` 并重启 notification，跑完挪回）。

const POLL_MS = 500;
const TIMEOUT_MS = 15_000;

interface MessageSummary {
  ID: string;
  Subject: string;
  Created: string;
}

/// Mailpit 的地址（`PATRA_MAILPIT_BASE_URL`，如 `http://100.103.73.27:8025`）
function baseUrl(): string {
  const value = process.env.PATRA_MAILPIT_BASE_URL;
  if (!value) {
    throw new Error("未设置 PATRA_MAILPIT_BASE_URL");
  }
  return value.replace(/\/+$/, "");
}

/// 发给 `to`、主题含 `subject`、`after`（毫秒时间戳）之后收到的最新一封邮件里 `#token=` 后的 43 个字符。
/// 邮件是异步发的：每 500ms 查一次，15 秒还没有就抛错。
export async function latestToken(to: string, subject: string, after: number): Promise<string> {
  const deadline = Date.now() + TIMEOUT_MS;
  while (Date.now() < deadline) {
    const query = new URLSearchParams({ query: `to:"${to}"` });
    const res = await fetch(`${baseUrl()}/api/v1/search?${query}`);
    const { messages } = (await res.json()) as { messages: MessageSummary[] };
    const hit = messages.find(
      (message) =>
        message.Subject.includes(subject) && Date.parse(message.Created) >= after - 1_000,
    );
    if (hit) {
      const detail = (await (await fetch(`${baseUrl()}/api/v1/message/${hit.ID}`)).json()) as {
        Text: string;
      };
      const token = /#token=([A-Za-z0-9_-]{43})/.exec(detail.Text)?.[1];
      if (token) {
        return token;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_MS));
  }
  throw new Error(`没有等到发给 ${to} 的「${subject}」邮件`);
}

/// 一封邮件：Mailpit 里的 ID 与纯文本正文
export interface Mail {
  id: string;
  text: string;
}

/// 等一封发给 `to`、主题含 `subject`、正文含 `marker`、`after`（毫秒时间戳）之后收到的邮件。提醒邮件要等 10～11
/// 分钟才发，调用方给够 `timeoutMs`；每 5 秒查一次，到时还没有就抛错
export async function waitForMail(options: {
  to: string;
  subject: string;
  marker: string;
  after: number;
  timeoutMs: number;
}): Promise<Mail> {
  const { to, subject, marker, after, timeoutMs } = options;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const query = new URLSearchParams({ query: `to:"${to}" subject:"${subject}"` });
    const res = await fetch(`${baseUrl()}/api/v1/search?${query}`);
    const { messages } = (await res.json()) as { messages: MessageSummary[] };
    for (const message of messages) {
      if (Date.parse(message.Created) < after - 1_000) {
        continue;
      }
      const detail = (await (await fetch(`${baseUrl()}/api/v1/message/${message.ID}`)).json()) as {
        Text: string;
      };
      if (detail.Text.includes(marker)) {
        return { id: message.ID, text: detail.Text };
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 5_000));
  }
  throw new Error(`没有等到发给 ${to}、含「${marker}」的「${subject}」邮件`);
}

/// 一封邮件的全部邮件头（同名头可能有多个值）
export async function mailHeaders(id: string): Promise<Record<string, string[]>> {
  const res = await fetch(`${baseUrl()}/api/v1/message/${id}/headers`);
  return (await res.json()) as Record<string, string[]>;
}

/// 纯文本正文里 `{label}：{地址}` 那一行的地址
export function linkAfter(text: string, label: string): string {
  const url = new RegExp(`${label}：(\\S+)`).exec(text)?.[1];
  if (!url) {
    throw new Error(`邮件里没有「${label}」链接`);
  }
  return url;
}

/// 邮件里的链接是 mini 配置的门户域名，测试只取路径、查询串与片段，在本机门户上打开
export function localPath(url: string): string {
  const { pathname, search, hash } = new URL(url);
  return `${pathname}${search}${hash}`;
}
