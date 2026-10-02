import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig } from 'vitest/config';

import { storybookTest } from '@storybook/addon-vitest/vitest-plugin';

import { playwright } from '@vitest/browser-playwright';

const dirname =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url));

/**
 * Runs every story as a test, with its accessibility checks, in a browser
 * that prefers the colour scheme: the theme follows it, so each story is
 * checked in light and in dark.
 *
 * Run the two projects one after the other (npm run test:storybook): they
 * share the Storybook plugin's dependency cache, and optimizing it from both
 * at once breaks modules.
 */
function stories(colorScheme: 'light' | 'dark') {
  return {
    extends: true as const,
    plugins: [
      // More info at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon
      storybookTest({ configDir: path.join(dirname, '.storybook') }),
    ],
    test: {
      name: `storybook-${colorScheme}`,
      browser: {
        enabled: true,
        headless: true,
        provider: playwright({ contextOptions: { colorScheme } }),
        instances: [{ browser: 'chromium' as const }],
      },
    },
  };
}

export default defineConfig({
  test: {
    projects: [stories('light'), stories('dark')],
  },
});
