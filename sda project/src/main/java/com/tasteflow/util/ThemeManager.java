package com.tasteflow.util;

import com.tasteflow.App;
import javafx.scene.Scene;

/**
 * Singleton theme manager — toggles between dark-theme.css and light-theme.css
 * at runtime without restarting the application.
 *
 * Usage:
 * <pre>
 *   ThemeManager.getInstance().init(scene);          // call once in App.start()
 *   ThemeManager.getInstance().toggle();              // flip dark ↔ light
 *   boolean dark = ThemeManager.getInstance().isDark();
 * </pre>
 */
public final class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    private static final String DARK_CSS  = "dark-theme.css";
    private static final String LIGHT_CSS = "light-theme.css";

    private Scene scene;
    private boolean dark = true; // start in dark mode

    private ThemeManager() {}

    public static ThemeManager getInstance() { return INSTANCE; }

    /** Bind to the scene and apply the initial theme. Call once in App.start(). */
    public void init(Scene scene) {
        this.scene = scene;
        applyTheme();
    }

    /** Toggle between dark and light mode. */
    public void toggle() {
        dark = !dark;
        applyTheme();
    }

    /** Set a specific mode. */
    public void setDark(boolean darkMode) {
        this.dark = darkMode;
        applyTheme();
    }

    public boolean isDark() { return dark; }

    private void applyTheme() {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String css = dark ? DARK_CSS : LIGHT_CSS;
        String resource = App.class.getResource(css).toExternalForm();
        scene.getStylesheets().add(resource);
    }
}
