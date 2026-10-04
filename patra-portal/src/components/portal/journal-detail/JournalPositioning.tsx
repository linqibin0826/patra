import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { formatSubject } from "@/lib/subject-label";
import type { VenueDetail } from "@/types/portal";

/// 定位与范围：由结构化事实（学科、出版频率、索引、获取方式）组合成的标签行。
export function JournalPositioning({
  venue,
  subjects,
}: {
  venue: VenueDetail;
  subjects: string[];
}) {
  const tags: { k: string; v: string }[] = [];
  for (const s of subjects) {
    tags.push({ k: "学科", v: formatSubject(s) });
  }
  if (venue.frequency) {
    tags.push({ k: "出版", v: venue.frequency });
  }
  if (venue.medlineIndexed != null) {
    tags.push({ k: "索引", v: venue.medlineIndexed ? "MEDLINE 收录" : "未被 MEDLINE 收录" });
  }
  tags.push({
    k: "获取",
    v: venue.isOpenAccess ? `开放获取${venue.oaType ? ` · ${venue.oaType}` : ""}` : "订阅 / 混合",
  });

  return (
    <section aria-label="定位与范围">
      <SectionEyebrow className="mb-2">定位与范围</SectionEyebrow>
      <p className="mb-4 font-sans text-sm leading-normal text-fg-3">
        由结构化事实组合 —— 该刊暂无编辑撰写的简介文本。
      </p>
      <div className="flex flex-wrap gap-2">
        {tags.map((t) => (
          <span
            key={`${t.k}-${t.v}`}
            className="inline-flex items-center gap-2 rounded-full border border-border-default bg-paper-50 py-1.5 pr-3.5 pl-3 font-sans text-sm font-medium leading-normal text-fg-1"
          >
            <span className="font-mono text-3xs uppercase tracking-mono text-fg-3">{t.k}</span>
            {t.v}
          </span>
        ))}
      </div>
    </section>
  );
}
