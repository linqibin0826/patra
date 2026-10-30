import { formatCount } from "@/lib/format-count";
import { formatDateTime, formatRelative } from "@/lib/format-time";

interface ThreadStatsProps {
  /// 这一楼未删除的回复数
  replyCount: number;
  /// 最新一条未删除回复的发表时刻
  lastReplyAt: string | null;
  now: number;
}

/// 楼内统计（简报 §3.4）：根评论操作行右端的「N 条回复 · 最后回复 <时间>」，这一楼有回复就显示（1 条也显示）
export function ThreadStats({ replyCount, lastReplyAt, now }: ThreadStatsProps) {
  if (replyCount === 0 || lastReplyAt === null) {
    return null;
  }
  return (
    <span className="text-xs text-fg-3">
      {formatCount(replyCount)} 条回复 · 最后回复{" "}
      <time dateTime={lastReplyAt} title={formatDateTime(lastReplyAt)}>
        {formatRelative(lastReplyAt, now)}
      </time>
    </span>
  );
}
