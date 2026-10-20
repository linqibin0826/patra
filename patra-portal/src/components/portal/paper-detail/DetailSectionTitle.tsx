/// 详情页区块标题（spec §5.4）：陶土竖条 + 「中文 · ENGLISH」小字，替代原来的 SectionEyebrow。
export function DetailSectionTitle({ zh, en, id }: { zh: string; en: string; id?: string }) {
  return (
    <h2
      id={id}
      className="m-0 flex items-center gap-2.5 font-sans text-2xs font-semibold tracking-caps text-fg-3"
    >
      <span aria-hidden className="h-3.5 w-1 bg-clay-500" />
      {zh} · {en}
    </h2>
  );
}
