package com.rutik.systemdesign.lld.creational.abstract_factory;

/**
 * ABSTRACT FACTORY PATTERN — Real World Example
 *
 * Scenario: Cross-Platform UI Component Library
 *
 * Problem:
 *   A desktop application must run on both Windows and macOS. Each platform
 *   has its own native look-and-feel for UI widgets: buttons, checkboxes, and
 *   text fields render and behave differently. Hard-coding platform-specific
 *   widget classes throughout the application would make the code brittle and
 *   impossible to port — every new platform would require touching every screen.
 *
 * Solution:
 *   An Abstract Factory (UIFactory) declares creation methods for each widget
 *   type. WindowsUIFactory and MacUIFactory each produce their platform's native
 *   widgets. The application's UI layer is constructed against the abstract
 *   interfaces only; at startup the correct factory is injected based on the
 *   detected OS. Adding a Linux theme = add LinuxUIFactory + Linux widgets only.
 *
 * Run: javac RealWorldExample.java && java CrossPlatformUIDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// Abstract Product: Button
// ─────────────────────────────────────────────────────────────────────────────
interface Button {
    void render();
    void onClick(String action);
    String getStyle();
}

// ─────────────────────────────────────────────────────────────────────────────
// Abstract Product: Checkbox
// ─────────────────────────────────────────────────────────────────────────────
interface Checkbox {
    void render();
    void toggle();
    boolean isChecked();
    String getStyle();
}

// ─────────────────────────────────────────────────────────────────────────────
// Abstract Product: TextField
// ─────────────────────────────────────────────────────────────────────────────
interface TextField {
    void render();
    void setText(String text);
    String getText();
    String getStyle();
}

// ─────────────────────────────────────────────────────────────────────────────
// Windows Concrete Products
// ─────────────────────────────────────────────────────────────────────────────
class WindowsButton implements Button {

    private final String label;

    public WindowsButton(String label) {
        this.label = label;
    }

    @Override
    public void render() {
        System.out.println("  [Windows Button] Rendering \"" + label
                + "\" with flat Fluent Design border and accent color");
    }

    @Override
    public void onClick(String action) {
        System.out.println("  [Windows Button] Click registered -> dispatching: " + action);
    }

    @Override
    public String getStyle() {
        return "Windows/Fluent";
    }
}

class WindowsCheckbox implements Checkbox {

    private final String label;
    private boolean checked;

    public WindowsCheckbox(String label) {
        this.label = label;
        this.checked = false;
    }

    @Override
    public void render() {
        String state = checked ? "[x]" : "[ ]";
        System.out.println("  [Windows Checkbox] " + state + " " + label
                + "  (square box, Segoe UI font)");
    }

    @Override
    public void toggle() {
        checked = !checked;
        System.out.println("  [Windows Checkbox] \"" + label + "\" toggled -> " + (checked ? "checked" : "unchecked"));
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public String getStyle() {
        return "Windows/Fluent";
    }
}

class WindowsTextField implements TextField {

    private final String placeholder;
    private String text;

    public WindowsTextField(String placeholder) {
        this.placeholder = placeholder;
        this.text = "";
    }

    @Override
    public void render() {
        String display = text.isEmpty() ? placeholder : text;
        System.out.println("  [Windows TextField] [ " + display + " ] "
                + "(rectangular, 1px border, Segoe UI)");
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getStyle() {
        return "Windows/Fluent";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// macOS Concrete Products
// ─────────────────────────────────────────────────────────────────────────────
class MacButton implements Button {

    private final String label;

    public MacButton(String label) {
        this.label = label;
    }

    @Override
    public void render() {
        System.out.println("  [Mac Button] Rendering \"" + label
                + "\" with rounded corners, gradient fill, and SF Pro font");
    }

    @Override
    public void onClick(String action) {
        System.out.println("  [Mac Button] Click registered with spring animation -> dispatching: " + action);
    }

    @Override
    public String getStyle() {
        return "macOS/Aqua";
    }
}

class MacCheckbox implements Checkbox {

    private final String label;
    private boolean checked;

    public MacCheckbox(String label) {
        this.label = label;
        this.checked = false;
    }

    @Override
    public void render() {
        String state = checked ? "(v)" : "( )";
        System.out.println("  [Mac Checkbox] " + state + " " + label
                + "  (rounded checkbox, SF Pro font, blue accent)");
    }

    @Override
    public void toggle() {
        checked = !checked;
        System.out.println("  [Mac Checkbox] \"" + label + "\" toggled -> " + (checked ? "checked" : "unchecked"));
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public String getStyle() {
        return "macOS/Aqua";
    }
}

class MacTextField implements TextField {

    private final String placeholder;
    private String text;

    public MacTextField(String placeholder) {
        this.placeholder = placeholder;
        this.text = "";
    }

    @Override
    public void render() {
        String display = text.isEmpty() ? placeholder : text;
        System.out.println("  [Mac TextField] ( " + display + " ) "
                + "(rounded, shadow, SF Pro, blue focus ring)");
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getStyle() {
        return "macOS/Aqua";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Abstract Factory: UIFactory
// ─────────────────────────────────────────────────────────────────────────────
interface UIFactory {
    Button createButton(String label);
    Checkbox createCheckbox(String label);
    TextField createTextField(String placeholder);
    String getPlatformName();
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Factory: Windows
// ─────────────────────────────────────────────────────────────────────────────
class WindowsUIFactory implements UIFactory {

    @Override
    public Button createButton(String label) {
        return new WindowsButton(label);
    }

    @Override
    public Checkbox createCheckbox(String label) {
        return new WindowsCheckbox(label);
    }

    @Override
    public TextField createTextField(String placeholder) {
        return new WindowsTextField(placeholder);
    }

    @Override
    public String getPlatformName() {
        return "Windows";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Factory: macOS
// ─────────────────────────────────────────────────────────────────────────────
class MacUIFactory implements UIFactory {

    @Override
    public Button createButton(String label) {
        return new MacButton(label);
    }

    @Override
    public Checkbox createCheckbox(String label) {
        return new MacCheckbox(label);
    }

    @Override
    public TextField createTextField(String placeholder) {
        return new MacTextField(placeholder);
    }

    @Override
    public String getPlatformName() {
        return "macOS";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Factory Provider — selects factory based on OS detection (or override)
// ─────────────────────────────────────────────────────────────────────────────
class UIFactoryProvider {

    /**
     * Auto-detects the host OS and returns the appropriate factory.
     * In production this would read from a config or environment variable.
     */
    public static UIFactory getFactory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return new MacUIFactory();
        }
        // Default to Windows for Windows or any unrecognised OS
        return new WindowsUIFactory();
    }

    /** Explicit override — useful for testing or cross-platform preview mode. */
    public static UIFactory getFactory(String platform) {
        switch (platform.toLowerCase()) {
            case "mac":
            case "macos":
                return new MacUIFactory();
            default:
                return new WindowsUIFactory();
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Application: Login Screen — built entirely against abstract interfaces
// ─────────────────────────────────────────────────────────────────────────────
class LoginScreen {

    // All fields are abstract types — no platform-specific imports
    private final Button loginButton;
    private final Button cancelButton;
    private final Checkbox rememberMe;
    private final TextField usernameField;
    private final TextField passwordField;
    private final String platform;

    public LoginScreen(UIFactory factory) {
        this.platform = factory.getPlatformName();
        // The factory ensures all widgets belong to the same platform family
        this.usernameField = factory.createTextField("Enter username");
        this.passwordField = factory.createTextField("Enter password");
        this.rememberMe    = factory.createCheckbox("Remember me");
        this.loginButton   = factory.createButton("Log In");
        this.cancelButton  = factory.createButton("Cancel");
    }

    public void render() {
        System.out.println("  [LoginScreen on " + platform + "] Rendering...");
        usernameField.render();
        passwordField.render();
        rememberMe.render();
        loginButton.render();
        cancelButton.render();
    }

    public void simulateLogin(String username, boolean remember) {
        System.out.println("\n  [LoginScreen] User types credentials...");
        usernameField.setText(username);
        passwordField.setText("••••••••");

        if (remember) {
            rememberMe.toggle();
        }

        System.out.println("\n  [LoginScreen] Rendering updated state:");
        usernameField.render();
        passwordField.render();
        rememberMe.render();

        System.out.println("\n  [LoginScreen] User clicks login:");
        loginButton.onClick("authenticate:" + username);

        System.out.println("  Widget style: " + loginButton.getStyle());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen — another screen built against the same factory
// ─────────────────────────────────────────────────────────────────────────────
class SettingsScreen {

    private final Button saveButton;
    private final Checkbox darkMode;
    private final Checkbox notifications;
    private final TextField displayNameField;
    private final String platform;

    public SettingsScreen(UIFactory factory) {
        this.platform        = factory.getPlatformName();
        this.displayNameField = factory.createTextField("Display name");
        this.darkMode         = factory.createCheckbox("Enable dark mode");
        this.notifications    = factory.createCheckbox("Enable notifications");
        this.saveButton       = factory.createButton("Save Settings");
    }

    public void render() {
        System.out.println("  [SettingsScreen on " + platform + "] Rendering...");
        displayNameField.render();
        darkMode.render();
        notifications.render();
        saveButton.render();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo Entry Point
// ─────────────────────────────────────────────────────────────────────────────
public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Abstract Factory Pattern: Cross-Platform UI Demo ===\n");

        // ── Windows UI ────────────────────────────────────────────────────────
        System.out.println("━━━ Running on Windows ━━━\n");
        UIFactory windowsFactory = UIFactoryProvider.getFactory("windows");

        System.out.println("--- Login Screen ---");
        LoginScreen winLogin = new LoginScreen(windowsFactory);
        winLogin.render();
        winLogin.simulateLogin("alice", true);

        System.out.println("\n--- Settings Screen ---");
        SettingsScreen winSettings = new SettingsScreen(windowsFactory);
        winSettings.render();

        // ── macOS UI ──────────────────────────────────────────────────────────
        System.out.println("\n━━━ Running on macOS ━━━\n");
        UIFactory macFactory = UIFactoryProvider.getFactory("mac");

        System.out.println("--- Login Screen ---");
        LoginScreen macLogin = new LoginScreen(macFactory);
        macLogin.render();
        macLogin.simulateLogin("bob", false);

        System.out.println("\n--- Settings Screen ---");
        SettingsScreen macSettings = new SettingsScreen(macFactory);
        macSettings.render();

        // ── Key insight ───────────────────────────────────────────────────────
        System.out.println("\n━━━ Key Insight ━━━");
        System.out.println("LoginScreen and SettingsScreen contain ZERO platform-specific code.");
        System.out.println("Swapping the factory swaps the entire UI family in one place.");
        System.out.println("Adding a Linux theme = add LinuxUIFactory + Linux widgets only.");
        System.out.println("\n=== Demo complete ===");
    }
}
