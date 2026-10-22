import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  // A server.js with only the files it needs, for the Docker image
  output: "standalone",
  // The CopilotKit runtime of the chat runs on Node as it is, not bundled
  serverExternalPackages: ["@copilotkit/runtime"],
};

export default nextConfig;
