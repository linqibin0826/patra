import { MODE_LABEL } from "@/lib/notification/messages";
import type {
  LinkCategory,
  NotificationPreferences,
  PreferenceCategory,
  UnsubscribeLink,
} from "@/lib/notification/schema";

/// 退订落地页按链接类别与两类档位算出来的文案（简报 §3.6，PAP-148 spec 第 7.4 节，对照画板 `Unsub` 的
/// `KIND_TEXT` / `DONE_DESC`）

/// 类别名
const NAME: Record<PreferenceCategory, string> = { REPLY: "「回复我的」", MENTION: "「@ 我的」" };
const KEYS: readonly { category: PreferenceCategory; key: keyof NotificationPreferences }[] = [
  { category: "REPLY", key: "reply" },
  { category: "MENTION", key: "mention" },
];

/// 逐条邮件退订成功的固定说明
const TYPE_DONE: Record<PreferenceCategory, string> = {
  REPLY: "以后有人回复你、评论你的投稿经验，不再给这个地址发邮件；提醒页里照常能看到。",
  MENTION: "以后有人在评论里 @ 你，不再给这个地址发邮件；提醒页里照常能看到。",
};

/// 「这一类邮件」的称呼
const mailName = (category: LinkCategory) =>
  category === "DIGEST" ? "每日汇总邮件" : `${NAME[category]}提醒邮件`;

/// 就绪态信息表「邮件」一行：逐条邮件写类别与这一类当前的档位；汇总邮件列出此刻处于汇总的类别
export function kindLine(link: Pick<UnsubscribeLink, "category" | "reply" | "mention">): string {
  if (link.category === "DIGEST") {
    const inDigest = KEYS.filter(({ key }) => link[key] === "DIGEST").map(
      ({ category }) => NAME[category],
    );
    return `每日汇总 · 包括${inDigest.join("")}`;
  }
  const key = link.category === "REPLY" ? "reply" : "mention";
  return `${NAME[link.category]}提醒 · ${MODE_LABEL[link[key]]}`;
}

/// 结果屏标题：已退订这一类 / 这一类已经退订过了
export function doneTitle(category: LinkCategory, already: boolean): string {
  return already ? `${mailName(category)}已经退订过了` : `已退订${mailName(category)}`;
}

/// 结果屏说明。逐条邮件是固定的两句；汇总邮件按两类档位写：关掉的类别（`turnedOff`；已经退订过时传 null，按此刻
/// 是关的类别算）「改为不发邮件」，还开着的类别「仍是 {档位}」
export function doneDescription(
  category: LinkCategory,
  modes: NotificationPreferences,
  turnedOff: readonly PreferenceCategory[] | null,
): string {
  if (category !== "DIGEST") {
    return TYPE_DONE[category];
  }
  const off = KEYS.filter(({ category: each, key }) =>
    turnedOff ? turnedOff.includes(each) : modes[key] === "OFF",
  );
  const on = KEYS.filter(({ key }) => modes[key] !== "OFF");
  if (off.length === 0) {
    return `${on.map(({ category: each }) => NAME[each]).join("")}现在都是离站时逐条，不会再收到每日汇总。`;
  }
  if (off.length === KEYS.length) {
    return "「回复我的」「@ 我的」都改为不发邮件，提醒页里照常能看到。";
  }
  const stillOn = on
    .filter((each) => !off.includes(each))
    .map(({ category: each, key }) => `${NAME[each]}仍是${MODE_LABEL[modes[key]]}`);
  const head = `${off.map(({ category: each }) => NAME[each]).join("")}改为不发邮件，提醒页里照常能看到`;
  return stillOn.length > 0 ? `${head}；${stillOn.join("，")}。` : `${head}。`;
}
