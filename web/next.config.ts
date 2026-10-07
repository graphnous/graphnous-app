import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  // The CopilotKit runtime of the chat runs on Node as it is, not bundled
  serverExternalPackages: ["@copilotkit/runtime"],
};

export default nextConfig;
