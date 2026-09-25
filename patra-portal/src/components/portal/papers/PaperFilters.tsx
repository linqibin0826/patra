"use client";

import { useState } from "react";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { FacetCheckRow } from "@/components/portal/browse/FacetCheckRow";
import { FacetGroup } from "@/components/portal/browse/FacetGroup";
import { FacetToggleRow } from "@/components/portal/browse/FacetToggleRow";
import { FilterPanel } from "@/components/portal/browse/FilterPanel";
import { VenueFacetSearch } from "@/components/portal/papers/VenueFacetSearch";
import { EVIDENCE_LABELS, languageFacetLabel, typeFacetLabel } from "@/lib/paper-labels";
import {
  clearAllConditions,
  containsIgnoreCase,
  filterCount,
  hasSearchConditions,
  isRecentYearsActive,
  RECENT_YEAR_SPANS,
  selectExactYear,
  selectRecentYears,
  toggleListValue,
  toggleOpenAccess,
  visibleFacetOptions,
} from "@/lib/portal-api/paper-search";
import { useSuggestedVenueNames } from "@/lib/portal-api/venue-suggest-query";
import { cn } from "@/lib/utils";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { PaperSearchQuery, PublicationFacets } from "@/types/portal";

/** 长列表 facet 折叠时显示的项数 */
const TOP_N = 6;

interface PaperFiltersProps {
  facets: PublicationFacets;
  currentYear: number;
  /** 已选期刊的服务端刊名 */
  venueNames: Readonly<Record<string, string>>;
}

function MoreToggle({
  expanded,
  label,
  onClick,
}: {
  expanded: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-expanded={expanded}
      onClick={onClick}
      className="mt-1 px-1 text-xs text-clay-700 hover:text-clay-800"
    >
      {expanded ? "收起" : `${label} ↓`}
    </button>
  );
}

function evidenceLabel(value: string): string {
  return (EVIDENCE_LABELS as Record<string, string>)[value] ?? value;
}

function PaperFilterControls({ facets, currentYear, venueNames }: PaperFiltersProps) {
  const { query, navigate } = useBrowseQuery<PaperSearchQuery>();
  const suggestedNames = useSuggestedVenueNames();
  const [typesExpanded, setTypesExpanded] = useState(false);
  const [langsExpanded, setLangsExpanded] = useState(false);

  const yearSelected = query.yearFrom !== null || query.yearTo !== null;
  const types = visibleFacetOptions(facets.types, query.type, typesExpanded, TOP_N);
  const evidence = visibleFacetOptions(facets.evidence, query.evidence, true, TOP_N);
  const langs = visibleFacetOptions(facets.languages, query.lang, langsExpanded, TOP_N);
  const selectedEvidence: readonly string[] = query.evidence;

  return (
    <div className="flex flex-col">
      {(facets.years.length > 0 || yearSelected) && (
        <FacetGroup title="发表年份" selCount={yearSelected ? 1 : 0}>
          <div className="mb-1.5 flex flex-wrap gap-1.5 px-1">
            {RECENT_YEAR_SPANS.map((span) => {
              const active = isRecentYearsActive(query, span, currentYear);
              return (
                <button
                  key={span}
                  type="button"
                  aria-pressed={active}
                  onClick={() => navigate((cur) => selectRecentYears(cur, span, currentYear))}
                  className={cn(
                    "rounded-full border px-2.5 py-0.5 text-xs transition-colors",
                    active
                      ? "border-clay-300 bg-clay-50 text-clay-800"
                      : "border-border-default text-fg-2 hover:bg-paper-200",
                  )}
                >
                  近 {span} 年
                </button>
              );
            })}
          </div>
          {facets.years.map((opt) => {
            const year = Number(opt.value);
            return (
              <FacetCheckRow
                key={opt.value}
                type="radio"
                name="paper-year"
                label={opt.value}
                count={opt.count}
                checked={query.yearFrom === year && query.yearTo === year}
                onToggle={() => navigate((cur) => selectExactYear(cur, year))}
              />
            );
          })}
        </FacetGroup>
      )}

      {types.length > 0 && (
        <FacetGroup title="文献类型" selCount={query.type.length}>
          {types.map((opt) => (
            <FacetCheckRow
              key={opt.value}
              label={typeFacetLabel(opt.value)}
              count={opt.count}
              checked={containsIgnoreCase(query.type, opt.value)}
              onToggle={() => navigate((cur) => toggleListValue(cur, "type", opt.value))}
            />
          ))}
          {facets.types.length > TOP_N && (
            <MoreToggle
              expanded={typesExpanded}
              label="更多类型"
              onClick={() => setTypesExpanded((v) => !v)}
            />
          )}
        </FacetGroup>
      )}

      {evidence.length > 0 && (
        <FacetGroup title="证据等级" selCount={query.evidence.length}>
          {evidence.map((opt) => (
            <FacetCheckRow
              key={opt.value}
              label={evidenceLabel(opt.value)}
              count={opt.count}
              muted={opt.value === "UNKNOWN"}
              checked={selectedEvidence.includes(opt.value)}
              onToggle={() => navigate((cur) => toggleListValue(cur, "evidence", opt.value))}
            />
          ))}
        </FacetGroup>
      )}

      <FacetGroup title="期刊" selCount={query.venue.length}>
        <VenueFacetSearch
          selected={query.venue}
          names={{ ...suggestedNames, ...venueNames }}
          onAdd={(venue) => navigate((cur) => toggleListValue(cur, "venue", venue.id))}
          onRemove={(id) => navigate((cur) => toggleListValue(cur, "venue", id))}
        />
      </FacetGroup>

      {langs.length > 0 && (
        <FacetGroup title="语言" selCount={query.lang.length}>
          {langs.map((opt) => (
            <FacetCheckRow
              key={opt.value}
              label={languageFacetLabel(opt.value)}
              count={opt.count}
              checked={query.lang.includes(opt.value)}
              onToggle={() => navigate((cur) => toggleListValue(cur, "lang", opt.value))}
            />
          ))}
          {facets.languages.length > TOP_N && (
            <MoreToggle
              expanded={langsExpanded}
              label="更多语言"
              onClick={() => setLangsExpanded((v) => !v)}
            />
          )}
        </FacetGroup>
      )}

      {(facets.openAccess > 0 || query.oa) && (
        <FacetGroup title="开放获取" selCount={query.oa ? 1 : 0}>
          <FacetToggleRow
            label="仅开放获取"
            count={facets.openAccess}
            checked={query.oa}
            onToggle={() => navigate(toggleOpenAccess)}
          />
        </FacetGroup>
      )}

      {hasSearchConditions(query) && (
        <button
          type="button"
          onClick={() => navigate(clearAllConditions)}
          className="mt-3 self-start px-1 text-xs text-fg-3 underline underline-offset-2 hover:text-clay-700"
        >
          清除全部筛选
        </button>
      )}
    </div>
  );
}

/// 文献检索 facet 栏：桌面左侧栏 / 移动底部抽屉（标题"筛选 · 已选 N 项"，页脚"清除全部 / 查看 N 篇结果"）。
/// 勾选状态读 Provider 乐观 query；空组（无可选项且未选）不渲染，OA 计数为 0 时整组隐藏。
export function PaperFilters(props: PaperFiltersProps) {
  const { query, navigate } = useBrowseQuery<PaperSearchQuery>();
  const close = useBrowseFilterUiStore((s) => s.close);

  return (
    <FilterPanel
      side="bottom"
      railClassName="w-60"
      sheetTitle={`筛选 · 已选 ${filterCount(query)} 项`}
      sheetFooter={
        <div className="flex w-full gap-2">
          <button
            type="button"
            onClick={() => navigate(clearAllConditions)}
            className="flex-1 rounded-lg border border-border-default bg-paper-50 px-4 py-2 text-sm font-medium text-fg-1"
          >
            清除全部
          </button>
          <button
            type="button"
            onClick={close}
            className="flex-1 rounded-lg bg-ink-900 px-4 py-2 text-sm font-medium text-paper-50"
          >
            查看 {props.facets.total.toLocaleString("en-US")} 篇结果
          </button>
        </div>
      }
    >
      <PaperFilterControls {...props} />
    </FilterPanel>
  );
}
