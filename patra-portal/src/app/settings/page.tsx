import { redirect } from "next/navigation";

/// `/settings` 跳到个人资料分区（简报 §2.1）
export default function SettingsIndexPage(): never {
  redirect("/settings/profile");
}
