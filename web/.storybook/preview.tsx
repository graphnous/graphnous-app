import type { Decorator, Preview } from '@storybook/nextjs-vite';
import { Geist, Geist_Mono } from 'next/font/google';

import '../src/app/globals.css';

// The fonts the root layout loads, so stories look like the app
const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

/**
 * Renders the story in the chosen colour scheme, on the theme's background:
 * the system's, or forced to light or dark as data-theme does in the app.
 */
const withTheme: Decorator = (Story, context) => {
  const theme = context.globals.theme;

  document.documentElement.className = `${geistSans.variable} ${geistMono.variable}`;

  if (theme === 'light' || theme === 'dark') {
    document.documentElement.dataset.theme = theme;
  } else {
    delete document.documentElement.dataset.theme;
  }

  // Whole-page stories, such as the app shell, set parameters.padded: false
  const padded = context.parameters.padded !== false;

  return (
    <div className={`bg-background font-sans text-foreground antialiased ${padded ? 'p-4' : ''}`}>
      <Story />
    </div>
  );
};

const preview: Preview = {
  decorators: [withTheme],

  globalTypes: {
    theme: {
      description: 'Colour scheme',
      toolbar: {
        title: 'Theme',
        icon: 'mirror',
        items: [
          { value: 'system', title: 'System' },
          { value: 'light', title: 'Light' },
          { value: 'dark', title: 'Dark' },
        ],
        dynamicTitle: true,
      },
    },
  },

  initialGlobals: {
    theme: 'system',
  },

  parameters: {
    // The decorator paints the theme's background
    backgrounds: { disable: true },
    layout: 'fullscreen',

    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },

    a11y: {
      // Accessibility violations fail the story's test
      test: 'error',
      config: {
        rules: [
          {
            // Floating UI's focus guards are hidden and focusable on purpose:
            // they move focus into and out of a popover or menu
            id: 'aria-hidden-focus',
            selector: '[aria-hidden="true"]:not([data-floating-ui-focus-guard])',
          },
        ],
      },
    },
  },
};

export default preview;
