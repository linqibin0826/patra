import type { EvidenceLevelCode } from "@/types/portal";

/** 证据等级中文名（与 BE EvidenceLevel.label、详情页 EvidenceBadge 一致） */
export const EVIDENCE_LABELS: Record<EvidenceLevelCode, string> = {
  SYSTEMATIC_REVIEW: "系统综述 / Meta 分析",
  RANDOMIZED_CONTROLLED_TRIAL: "随机对照试验",
  COHORT_OR_CASE_CONTROL: "队列 / 病例对照",
  NON_SYSTEMATIC_REVIEW: "非系统综述 / 临床研究",
  CASE_REPORT: "病例报告",
  UNKNOWN: "未分级",
};

/** PubMed Publication Type → 中文名（short 为 facet 行里替代英文原值的短名）。未收录的类型按原值展示。 */
const TYPE_NAME_ENTRIES: [string, { zh: string; short?: string }][] = [
  ["Journal Article", { zh: "期刊论文" }],
  ["Review", { zh: "综述" }],
  ["Systematic Review", { zh: "系统综述" }],
  ["Scoping Review", { zh: "范围综述" }],
  ["Meta-Analysis", { zh: "Meta 分析" }],
  ["Network Meta-Analysis", { zh: "网状 Meta 分析" }],
  ["Randomized Controlled Trial", { zh: "随机对照试验", short: "RCT" }],
  ["Controlled Clinical Trial", { zh: "对照临床试验" }],
  ["Pragmatic Clinical Trial", { zh: "实用性临床试验" }],
  ["Equivalence Trial", { zh: "等效性试验" }],
  ["Clinical Trial", { zh: "临床试验" }],
  ["Clinical Trial, Phase I", { zh: "I 期临床试验" }],
  ["Clinical Trial, Phase II", { zh: "II 期临床试验" }],
  ["Clinical Trial, Phase III", { zh: "III 期临床试验" }],
  ["Clinical Trial, Phase IV", { zh: "IV 期临床试验" }],
  ["Clinical Trial Protocol", { zh: "临床试验方案" }],
  ["Clinical Study", { zh: "临床研究" }],
  ["Observational Study", { zh: "观察性研究" }],
  ["Multicenter Study", { zh: "多中心研究" }],
  ["Comparative Study", { zh: "比较研究" }],
  ["Evaluation Study", { zh: "评价研究" }],
  ["Validation Study", { zh: "验证研究" }],
  ["Twin Study", { zh: "双生子研究" }],
  ["Case Reports", { zh: "病例报告" }],
  ["Letter", { zh: "快报" }],
  ["Editorial", { zh: "社论" }],
  ["Comment", { zh: "评论" }],
  ["News", { zh: "新闻" }],
  ["Practice Guideline", { zh: "实践指南" }],
  ["Guideline", { zh: "指南" }],
  ["Consensus Development Conference", { zh: "共识会议" }],
  ["Published Erratum", { zh: "勘误" }],
  ["Retracted Publication", { zh: "已撤回文献" }],
  ["Retraction of Publication", { zh: "撤稿声明" }],
  ["Preprint", { zh: "预印本" }],
  ["Historical Article", { zh: "历史文献" }],
  ["Biography", { zh: "传记" }],
  ["Interview", { zh: "访谈" }],
  ["Lecture", { zh: "讲座" }],
  ["Video-Audio Media", { zh: "音视频" }],
  ["Dataset", { zh: "数据集" }],
  ["Technical Report", { zh: "技术报告" }],
  ["Patient Education Handout", { zh: "患者教育材料" }],
  ["English Abstract", { zh: "英文摘要" }],
  ["Research Support, Non-U.S. Gov't", { zh: "研究资助（非美国政府）" }],
  ["Research Support, N.I.H., Extramural", { zh: "研究资助（NIH 院外）" }],
  ["Research Support, N.I.H., Intramural", { zh: "研究资助（NIH 院内）" }],
  ["Research Support, U.S. Gov't, P.H.S.", { zh: "研究资助（美国公共卫生署）" }],
  ["Research Support, U.S. Gov't, Non-P.H.S.", { zh: "研究资助（美国政府非 PHS）" }],
];

/** 小写键：与 BE lower(trim()) 比较一致，URL 里写成 review 也能查到中文名。 */
const TYPE_NAMES = new Map(TYPE_NAME_ENTRIES.map(([value, name]) => [value.toLowerCase(), name]));

/** 语言基码 → 中文名（小写键）。 */
const LANGUAGE_NAMES = new Map<string, string>([
  ["en", "英文"],
  ["zh", "中文"],
  ["de", "德文"],
  ["fr", "法文"],
  ["es", "西班牙文"],
  ["ru", "俄文"],
  ["ja", "日文"],
  ["it", "意大利文"],
  ["pt", "葡萄牙文"],
  ["ko", "韩文"],
  ["pl", "波兰文"],
  ["nl", "荷兰文"],
  ["tr", "土耳其文"],
  ["cs", "捷克文"],
  ["sv", "瑞典文"],
  ["da", "丹麦文"],
  ["no", "挪威文"],
  ["fi", "芬兰文"],
  ["hu", "匈牙利文"],
  ["el", "希腊文"],
  ["he", "希伯来文"],
  ["ar", "阿拉伯文"],
  ["fa", "波斯文"],
  ["uk", "乌克兰文"],
]);

/** 类型 chip 文案：中文名；未收录回退原值。 */
export function typeName(value: string): string {
  return TYPE_NAMES.get(value.toLowerCase())?.zh ?? value;
}

/** 类型 facet 行文案："综述 · Review"；有短名时用短名；未收录回退原值。 */
export function typeFacetLabel(value: string): string {
  const name = TYPE_NAMES.get(value.toLowerCase());
  return name ? `${name.zh} · ${name.short ?? value}` : value;
}

/** 语言 chip 文案：中文名；未收录回退原值。 */
export function languageName(code: string): string {
  return LANGUAGE_NAMES.get(code.toLowerCase()) ?? code;
}

/** 语言 facet 行文案："英文 · en"；未收录回退原值。 */
export function languageFacetLabel(code: string): string {
  const zh = LANGUAGE_NAMES.get(code.toLowerCase());
  return zh ? `${zh} · ${code}` : code;
}
