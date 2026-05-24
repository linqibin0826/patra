import type { SearchMode } from "@/types/portal";

export const SEARCH_MODES: readonly SearchMode[] = [
  {
    id: "keyword",
    label: "关键词",
    placeholder: "JAK1 抑制剂 · 特应性皮炎 · 1岁以下",
    mono: false,
  },
  { id: "pmid", label: "PMID", placeholder: "38491203", mono: true },
  { id: "doi", label: "DOI", placeholder: "10.1016/j.jaci.2024.03.018", mono: true },
  { id: "author", label: "作者", placeholder: "Topol, Eric J.", mono: false },
];
