import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  // The component library is TypeScript source
  transpilePackages: ["graphnous-theme"],
};

export default nextConfig;
