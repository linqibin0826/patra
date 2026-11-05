/// 热读补位卡片序号位置上的小胶囊「最新收录」（简报 v0.18 §3.5）：不冒充热读。在标题之前，读屏先读到它
export function FillerTag() {
  return (
    <span className="inline-flex h-5 shrink-0 items-center rounded-full bg-paper-200 px-2 text-2xs font-medium text-fg-2">
      最新收录
    </span>
  );
}
