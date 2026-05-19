import { NextResponse } from "next/server";

const VERSION = process.env.npm_package_version ?? "0.0.0";

export const dynamic = "force-dynamic";

export function GET(): NextResponse {
  return NextResponse.json({
    status: "ok",
    version: VERSION,
    timestamp: new Date().toISOString(),
  });
}
