/**
 * The colour scheme the user chose: the system's, or light or dark
 * whatever the system's is.
 */
export type ThemePreference = "system" | "light" | "dark";

/**
 * The colour scheme the page shows.
 */
export type ResolvedTheme = "light" | "dark";

/**
 * Where the choice is kept in the browser, across visits and tabs.
 */
export const THEME_STORAGE_KEY = "graphnous-theme";

/**
 * Sets the stored choice on the page before it is first painted, so a
 * chosen theme does not flash the system's first. Run as an inline script
 * in the document's head; the theme's colours follow data-theme on <html>.
 */
export const themeScript = `(function () {
  try {
    var theme = localStorage.getItem("${THEME_STORAGE_KEY}");
    if (theme === "light" || theme === "dark") {
      document.documentElement.setAttribute("data-theme", theme);
    }
  } catch (e) {}
})();`;
