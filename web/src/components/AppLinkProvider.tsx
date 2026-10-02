"use client";

import type { ReactNode } from "react";
import NextLink from "next/link";
import { LinkProvider } from "graphnous-theme";

/**
 * Makes the links of the components go through Next.js' router.
 */
export function AppLinkProvider({ children }: { children: ReactNode }) {
  return <LinkProvider component={NextLink}>{children}</LinkProvider>;
}
