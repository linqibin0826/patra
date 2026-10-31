import type { NotificationMode, PreferenceCategory } from "@/lib/notification/schema";

/// 设置 › 通知的文案（简报 §3.5）

/// 三档：标题与说明。「默认」只标在出厂档位旁，选了别的档也一直显示
export const MODE_OPTIONS: readonly {
  mode: NotificationMode;
  title: string;
  description: string;
}[] = [
  { mode: "OFF", title: "关", description: "只在站内提醒，不发邮件。" },
  {
    mode: "INSTANT",
    title: "离站时逐条",
    description:
      "你不在 Patra 上时，提醒 10 分钟后仍未读就发一封；同一时间到的几条合成一封；在站内看过的不发。",
  },
  {
    mode: "DIGEST",
    title: "每日汇总",
    description: "每天早上 8:00（北京时间）发一封，列出过去 24 小时里还没读的；都读过了就不发。",
  },
];

/// 出厂档位
export const DEFAULT_MODE: NotificationMode = "INSTANT";

/// 档位名（落地页信息表、汇总说明里也用）
export const MODE_LABEL: Record<NotificationMode, string> = {
  OFF: "关",
  INSTANT: "离站时逐条",
  DIGEST: "每日汇总",
};

/// 两类：卡片标题、说明、单选组的读屏名（去掉画板里重复的「的」，spec 第 9 节）
export const CATEGORY_COPY: Record<
  PreferenceCategory,
  { title: string; description: string; groupLabel: string }
> = {
  REPLY: {
    title: "回复我的",
    description: "有人回复你的评论、评论你的投稿经验时",
    groupLabel: "「回复我的」提醒邮件",
  },
  MENTION: {
    title: "@ 我的",
    description: "有人在评论里 @ 你时",
    groupLabel: "「@ 我的」提醒邮件",
  },
};

export const SETTINGS_MESSAGES = {
  sendTo: "提醒邮件发到",
  changeEmail: "更改邮箱",
  defaultBadge: "默认",
  helpfulTitle: "有帮助",
  helpfulDescription: "别人觉得你的评论或投稿经验有帮助时",
  helpfulPill: "只在站内",
  helpfulNote:
    "同一条内容每天合成一条提醒，在提醒页的「有帮助」里看；不计入铃铛上的数字，也不发邮件。",
  securityTitle: "账号安全邮件总会发送",
  securityNote:
    "验证邮箱、找回密码、更改邮箱、新设备登录、注销账号等邮件关系到账号安全，不能在这里关闭，也不带退订链接。",
  unverified: "你的邮箱还没验证：验证之前，不论下面怎么设置，都不会给你发提醒邮件。",
  resend: "重新发送验证邮件",
  unavailableTitle: "通知设置暂时打不开",
  unavailableNote: "请稍后再试。账号安全邮件不受影响。",
  retry: "重试",
  loading: "正在读取通知设置",
} as const;

/// 退订落地页的文案（简报 §3.6；读失败一屏与读屏文字由 PAP-148 spec 第 9 节补）
export const UNSUBSCRIBE_MESSAGES = {
  reading: "正在读取退订链接",
  readyTitle: "退订提醒邮件",
  readyDescription: "退订后不再给这个地址发这类邮件，站内提醒照常。",
  address: "地址",
  mail: "邮件",
  unsubscribeType: "退订这类邮件",
  unsubscribeDigest: "退订每日汇总",
  turnOffAll: "关闭全部提醒邮件",
  turnOffOthers: "同时关闭其余提醒邮件",
  submitting: "退订中…",
  readyNote: "账号安全邮件（验证邮箱、找回密码等）不受影响，也不能退订。",
  failed: "暂时没能退订，请稍后再试。",
  doneNote: "点错了？登录后在通知设置里改回来。",
  allTitle: "已关闭全部提醒邮件",
  allDescription: "回复和 @ 都不再给这个地址发邮件，提醒页里照常能看到。账号安全邮件不受影响。",
  allNote: "想重新收提醒邮件，登录后到通知设置里打开。",
  invalidTitle: "退订链接无效",
  invalidDescription:
    "链接可能不完整（有的邮件客户端会截断长链接），或者对应的账号已经注销。登录后可以在通知设置里关闭提醒邮件。",
  invalidNote: "账号安全邮件不受通知设置影响。",
  unavailableTitle: "暂时打不开退订链接",
  unavailableDescription: "请稍后点「重试」。如果已经离开这一页，重新点邮件里的退订链接即可。",
  retry: "重试",
  retrying: "读取中…",
  toSettings: "去通知设置",
  loginToSettings: "登录后去通知设置",
} as const;
