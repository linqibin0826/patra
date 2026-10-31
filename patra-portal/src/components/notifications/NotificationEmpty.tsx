import { AtSign, MessageSquare, ThumbsUp } from "lucide-react";
import type { NotificationTab } from "@/lib/notification/inbox-schema";

const COPY = {
  replies: {
    icon: MessageSquare,
    title: "还没有人回复你",
    body: "在文献页参与讨论、在期刊页分享投稿经验，别人的回复会出现在这里。",
  },
  mentions: { icon: AtSign, title: "还没有人 @ 你", body: "别人在评论里 @ 你时，会出现在这里。" },
  helpful: {
    icon: ThumbsUp,
    title: "还没有收到「有帮助」",
    body: "别人觉得你的评论或投稿经验有帮助时，同一条内容每天合成一条，出现在这里。",
  },
} as const;

/// 一个标签下没有提醒（简报 §3.19；有帮助一份见 v0.16 简报 §3.1）：居中，上方 48px 线性图标
export function NotificationEmpty({ tab }: { tab: NotificationTab }) {
  const { icon: Icon, title, body } = COPY[tab];
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
      <Icon className="size-12 text-fg-3" strokeWidth={1.25} aria-hidden="true" />
      <h2 className="font-serif text-xl font-medium text-fg-1">{title}</h2>
      <p className="max-w-[40em] text-sm text-fg-2">{body}</p>
    </div>
  );
}
