import { Venation } from "@/components/portal/Venation";

interface ExploreFeedEmptyProps {
  reason: "empty" | "error";
}

/// 文献流空态 / 取数失败：一片静态小叶 + 一句说明，留在区块原位不打断首页其余模块。
export function ExploreFeedEmpty({ reason }: ExploreFeedEmptyProps) {
  const text = reason === "error" ? "加载失败，请稍后重试" : "暂无文献";
  return (
    <div
      data-feed-state={reason}
      className="flex min-h-56 flex-col items-center justify-center gap-4 border-y border-border-default text-sm text-fg-3"
    >
      <Venation id={`feed-${reason}-leaf`} className="h-20 w-auto opacity-50" />
      {text}
    </div>
  );
}
