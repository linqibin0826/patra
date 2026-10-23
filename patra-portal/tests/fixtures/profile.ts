import type { MyProfile, PrivacySettings } from "@/lib/identity/profile-schema";

/// `GET /me/profile` 的一份完整响应体（简报画板「已填写」场景）
export const MY_PROFILE: MyProfile = {
  displayName: "陈宇",
  bio: "心内科医生，关注心衰与房颤。\n业余写写科普。",
  links: [
    { title: "实验室主页", url: "https://lab.example.edu/chen" },
    { title: null, url: "https://scholar.example.com/chen" },
  ],
  occupation: "PHYSICIAN",
  jobTitle: "主治医师",
  organization: { rorId: "04jztag35", name: "北京协和医院" },
  department: "心内科",
  researchInterests: [
    { ui: "D006333", name: "Heart Failure" },
    { ui: "D001281", name: "Atrial Fibrillation" },
  ],
};

/// 新用户：什么都没填
export const EMPTY_PROFILE: MyProfile = {
  displayName: null,
  bio: null,
  links: [],
  occupation: null,
  jobTitle: null,
  organization: null,
  department: null,
  researchInterests: [],
};

export const PUBLIC_PRIVACY: PrivacySettings = {
  profileVisibility: "PUBLIC",
  professionalVisibility: "PUBLIC",
  searchIndexing: false,
};
