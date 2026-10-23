/// 身份大类（release spec 2.1.2），顺序同资料页下拉（简报 §3.6）
export const OCCUPATIONS = [
  "PHYSICIAN",
  "NURSE",
  "PHARMACIST",
  "TECHNICIAN",
  "RESEARCHER",
  "STUDENT",
  "INDUSTRY",
  "OTHER",
] as const;

export type Occupation = (typeof OCCUPATIONS)[number];

const LABELS: Record<Occupation, string> = {
  PHYSICIAN: "医师",
  NURSE: "护士",
  PHARMACIST: "药师",
  TECHNICIAN: "医技",
  RESEARCHER: "科研人员",
  STUDENT: "学生",
  INDUSTRY: "行业从业者",
  OTHER: "其他",
};

/// 身份大类的中文名
export function occupationLabel(occupation: Occupation): string {
  return LABELS[occupation];
}
