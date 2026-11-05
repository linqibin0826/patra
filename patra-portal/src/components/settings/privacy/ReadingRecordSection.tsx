import { ReadingRecordCard } from "@/components/settings/privacy/ReadingRecordCard";
import { fetchReadingRecording } from "@/lib/analytics/history";

/// 「阅读记录」卡片的取数（async RSC，调用方包在 Suspense 里）：行为数据服务慢或挂了，只影响这张卡片，上面三张照常
/// （简报 v0.18 §3.8、§3.11）
export async function ReadingRecordSection() {
  const recording = await fetchReadingRecording();
  return <ReadingRecordCard initial={recording.kind === "ok" ? recording.data : "unavailable"} />;
}

/// 「阅读记录」卡片加载中：一张卡片的骨架（简报 §4.5 `privacy.reading.loading`）
export function ReadingRecordSkeleton() {
  return (
    <div
      role="status"
      aria-label="正在加载阅读记录设置"
      className="flex flex-col gap-4 rounded-xl border border-border-default bg-bg-paper p-6 max-[880px]:px-4 max-[880px]:py-5"
    >
      <div className="h-6 w-24 shimmer rounded bg-paper-200" />
      <div className="h-4 w-3/4 shimmer rounded bg-paper-200" />
      <div className="h-4 w-1/2 shimmer rounded bg-paper-200" />
    </div>
  );
}
