import type { PublicProfile } from "@/lib/identity/public-profile";

/// `GET /users/{username}` 的一份完整响应体（画板 profile.public-full）
export const PUBLIC_PROFILE: PublicProfile = {
  userId: "352303128713027814",
  username: "chen-yu",
  displayName: "陈宇",
  avatarUrl: null,
  avatarPalette: 1,
  audience: "PUBLIC",
  joinedAt: "2026-10",
  bio: "心内科医生，关注心衰与房颤。\n业余写写科普。",
  links: [
    { title: "实验室主页", url: "https://lab.example.edu/chen" },
    { title: null, url: "https://www.scholar.example.com/chen" },
  ],
  professional: {
    occupation: "PHYSICIAN",
    jobTitle: "主治医师",
    organization: { rorId: "04jztag35", name: "北京协和医院" },
    department: "心内科",
  },
  researchInterests: [
    { ui: "D006333", name: "Heart Failure" },
    { ui: "D001281", name: "Atrial Fibrillation" },
  ],
  indexable: true,
};
