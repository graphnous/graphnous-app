import type { StorybookConfig } from '@storybook/nextjs-vite';

const config: StorybookConfig = {
  "stories": [
    "../src/**/*.mdx",
    "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"
  ],
  "addons": [
    "@chromatic-com/storybook",
    "@storybook/addon-vitest",
    "@storybook/addon-a11y",
    "@storybook/addon-docs",
    "@storybook/addon-mcp"
  ],
  "framework": "@storybook/nextjs-vite",
  "staticDirs": [
    "../public"
  ],
  viteFinal: (viteConfig) => ({
    ...viteConfig,
    optimizeDeps: {
      ...viteConfig.optimizeDeps,
      // Phosphor's icon entry point re-exports some 1,500 modules; bundled
      // up front, stories do not load them one by one on first use
      include: [...(viteConfig.optimizeDeps?.include ?? []), '@phosphor-icons/react/ssr'],
    },
  }),
};
export default config;
