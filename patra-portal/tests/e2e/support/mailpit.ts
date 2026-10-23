/// 开发环境的邮件收集器 Mailpit（mini 上 patra-core 栈）。identity 发出的链接域名是 mini 上的门户，测试只取令牌，
/// 拼本机的落地页地址打开。

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
