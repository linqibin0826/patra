/// 用户中心的时间显示一律按北京时间（spec §11）：服务端与浏览器渲染出同一个字符串，不会水合不一致；
/// 安全邮件里写的也是北京时间。

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const PARTS_FORMAT = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
  hourCycle: "h23",
});

/// 北京时间的年月日时分（都是数字）
interface Parts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

/// 拆出北京时间的各部分；不依赖语言环境的标点，自己拼串
function partsOf(ms: number): Parts {
  const parts = Object.fromEntries(
    PARTS_FORMAT.formatToParts(ms).map((part) => [part.type, part.value]),
  );
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
  };
}

/// 补零到两位
const pad = (value: number) => String(value).padStart(2, "0");

/// `HH:mm`
const clock = (p: Parts) => `${pad(p.hour)}:${pad(p.minute)}`;

/// 北京时间的日历日序号（自 1970-01-01 起的天数），用来比较「同一天」「昨天」
const dayNumber = (p: Parts) => Date.UTC(p.year, p.month - 1, p.day) / DAY;

/// `2026 年 11 月 19 日`
export function formatDate(iso: string): string {
  const p = partsOf(Date.parse(iso));
  return `${p.year} 年 ${p.month} 月 ${p.day} 日`;
}

/// `10 月 24 日`（文献库「收藏」「加入」时间）
export function formatMonthDay(iso: string): string {
  const p = partsOf(Date.parse(iso));
  return `${p.month} 月 ${p.day} 日`;
}

/// `10 月 26 日 18:40`（修改历史的版本时间）
export function formatMonthDayTime(iso: string): string {
  const p = partsOf(Date.parse(iso));
  return `${p.month} 月 ${p.day} 日 ${clock(p)}`;
}

/// `2026-10-20 09:12`
export function formatDateTime(iso: string): string {
  const p = partsOf(Date.parse(iso));
  return `${p.year}-${pad(p.month)}-${pad(p.day)} ${clock(p)}`;
}

/// `2026 年 11 月 6 日 08:00`
export function formatDateMinute(iso: string): string {
  const p = partsOf(Date.parse(iso));
  return `${p.year} 年 ${p.month} 月 ${p.day} 日 ${clock(p)}`;
}

/// 与 `now` 同一天写 `15:42`，否则写 `11 月 6 日 15:42`
export function formatClock(iso: string, now: number): string {
  const p = partsOf(Date.parse(iso));
  return dayNumber(p) === dayNumber(partsOf(now))
    ? clock(p)
    : `${p.month} 月 ${p.day} 日 ${clock(p)}`;
}

/// 相对时间：`刚刚`（不到 1 分钟，含时钟偏差造成的未来时间）、`N 分钟前`、`N 小时前`（同一天）、
/// `昨天 HH:mm`、`N 天前`（7 天以内）、再早写日期（今年 `10 月 15 日`，往年带年份，画板 Security）
export function formatRelative(iso: string, now: number): string {
  const at = Date.parse(iso);
  const elapsed = now - at;
  if (elapsed < MINUTE) {
    return "刚刚";
  }
  if (elapsed < HOUR) {
    return `${Math.floor(elapsed / MINUTE)} 分钟前`;
  }
  const p = partsOf(at);
  const days = dayNumber(partsOf(now)) - dayNumber(p);
  if (days === 0) {
    return `${Math.floor(elapsed / HOUR)} 小时前`;
  }
  if (days === 1) {
    return `昨天 ${clock(p)}`;
  }
  if (days <= 7) {
    return `${days} 天前`;
  }
  return p.year === partsOf(now).year
    ? `${p.month} 月 ${p.day} 日`
    : `${p.year} 年 ${p.month} 月 ${p.day} 日`;
}

/// 按北京时间的自然日分的三组（提醒列表）
export type DayGroup = "today" | "yesterday" | "earlier";

/// 与 `now` 同一天（含时钟偏差造成的未来时间）是今天，前一天是昨天，再早是更早
export function dayGroupOf(iso: string, now: number): DayGroup {
  const days = dayNumber(partsOf(now)) - dayNumber(partsOf(Date.parse(iso)));
  if (days <= 0) {
    return "today";
  }
  return days === 1 ? "yesterday" : "earlier";
}

/// 分组之后的时间写法（分组已经说明是哪天）：今天写相对时间、昨天写 `21:40`、更早写 `10 月 21 日`（往年带年份）
export function formatGroupedTime(iso: string, now: number): string {
  const group = dayGroupOf(iso, now);
  if (group === "today") {
    return formatRelative(iso, now);
  }
  const p = partsOf(Date.parse(iso));
  if (group === "yesterday") {
    return clock(p);
  }
  return p.year === partsOf(now).year
    ? `${p.month} 月 ${p.day} 日`
    : `${p.year} 年 ${p.month} 月 ${p.day} 日`;
}

/// `now` 加 `days` 天后的 ISO 时间
export function addDays(now: number, days: number): string {
  return new Date(now + days * DAY).toISOString();
}
