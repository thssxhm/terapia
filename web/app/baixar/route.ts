import { NextResponse } from "next/server";
import { site } from "../../site.config";

// SEU-SITE/baixar → página do app na Google Play
export function GET() {
  return NextResponse.redirect(site.playStoreUrl, 302);
}
