"use client";

import { useState } from "react";
import { DeleteAccountDialog } from "@/components/settings/account/DeleteAccountDialog";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { DELETE_ACCOUNT_MESSAGES } from "@/lib/auth/messages";
import type { CurrentUser } from "@/lib/auth/types";
import { btnDangerOutline } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 账号页 · 危险区（简报 §3.10 卡片 3）：三条后果说明 + 「注销账号…」，打开注销对话框
export function DeleteAccountCard({ user }: { user: CurrentUser }) {
  const [open, setOpen] = useState(false);
  return (
    <SettingsCard title="注销账号" variant="danger">
      <ul className="flex list-disc flex-col gap-1.5 pl-4.5 text-base leading-5.5 text-fg-2">
        {DELETE_ACCOUNT_MESSAGES.cardNotes.map((note) => (
          <li key={note}>{note}</li>
        ))}
      </ul>
      <div>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className={cn(btnDangerOutline, "px-3.5 py-1.5 text-sm")}
        >
          {DELETE_ACCOUNT_MESSAGES.open}
        </button>
      </div>
      <DeleteAccountDialog open={open} onOpenChange={setOpen} user={user} />
    </SettingsCard>
  );
}
