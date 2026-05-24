import type { Topic, TopicHeatTier } from "@/types/portal";

export const TOPIC_CLOUD: readonly Topic[] = [
  { term: "GLP-1 受体激动剂", heat: 100, count: 1842, delta: "+34%" },
  { term: "mRNA 疫苗平台", heat: 96, count: 1521, delta: "+18%" },
  { term: "CRISPR-Cas9 体内编辑", heat: 92, count: 1304, delta: "+27%" },
  { term: "阿尔茨海默 单抗", heat: 88, count: 1209, delta: "+11%" },
  { term: "肠道菌群 · IBD", heat: 78, count: 984 },
  { term: "PD-1 联合化疗", heat: 76, count: 952 },
  { term: "妊娠期糖尿病", heat: 70, count: 812 },
  { term: "AlphaFold", heat: 68, count: 790 },
  { term: "ECMO 撤机时机", heat: 62, count: 691 },
  { term: "睡眠呼吸暂停 · 儿童", heat: 58, count: 640 },
  { term: "JAK 抑制剂", heat: 56, count: 618 },
  { term: "肾移植 · 排异", heat: 50, count: 541 },
  { term: "肺动脉高压", heat: 48, count: 502 },
  { term: "脓毒症 · 早期识别", heat: 46, count: 478 },
  { term: "线粒体替代", heat: 42, count: 421 },
  { term: "戊型肝炎 疫苗", heat: 40, count: 402 },
  { term: "微塑料 · 心血管", heat: 38, count: 378 },
  { term: "类器官 · 药筛", heat: 36, count: 356 },
  { term: "ApoE4 与 痴呆", heat: 34, count: 330 },
  { term: "数字疗法 · 抑郁", heat: 32, count: 298 },
  { term: "妇科肿瘤 PARP", heat: 30, count: 276 },
  { term: "胰岛素抵抗 · 代谢", heat: 28, count: 254 },
  { term: "肠系膜缺血", heat: 24, count: 204 },
  { term: "EBV 与 多发性硬化", heat: 22, count: 187 },
  { term: "CAR-T · 实体瘤", heat: 20, count: 176 },
  { term: "妊娠 高血压", heat: 18, count: 154 },
  { term: "围术期 镇痛", heat: 14, count: 121 },
  { term: "肠脑轴", heat: 12, count: 108 },
  { term: "结核 耐药", heat: 10, count: 96 },
  { term: "脑机接口 · 临床", heat: 9, count: 88 },
  { term: "睡眠时长 · 全因死亡", heat: 8, count: 76 },
  { term: "心衰 · SGLT2", heat: 6, count: 62 },
];

/**
 * 根据热度返回字号 tier
 * 阈值（来自 handoff topic-cloud.jsx 内同名函数）：>=88 → 1, >=70 → 2, >=45 → 3, >=25 → 4, else 5
 */
export function topicTier(heat: number): TopicHeatTier {
  if (heat >= 88) return 1;
  if (heat >= 70) return 2;
  if (heat >= 45) return 3;
  if (heat >= 25) return 4;
  return 5;
}
