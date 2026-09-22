package com.knoxsurvivors.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dialog;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public final class Main {
    private static final Color BACKGROUND = new Color(7, 8, 10);
    private static final Color GREEN = new Color(188, 255, 0);
    private static final Color PURPLE = new Color(165, 65, 255);
    private static final Color MUTED = new Color(184, 188, 194);
    private static final Color WARNING = new Color(235, 185, 65);
    private static final Color ERROR = new Color(245, 105, 135);
    private static final String PREF_LAUNCH_OPTIONS = "customLaunchOptions";
    private final SteamLocator locator = new SteamLocator();
    private final InstallationValidator validator = new InstallationValidator();
    private final GameLauncher gameLauncher = new GameLauncher();
    private final Preferences preferences = Preferences.userNodeForPackage(Main.class);
    private final JFrame window = new JFrame("Knox Survivors");
    private final JLabel status = new JLabel("Checking Steam and Workshop files...", SwingConstants.CENTER);
    private final JCheckBox debugMode = new JCheckBox("Enable Project Zomboid Debug Mode");
    private final JTextField launchOptions = new JTextField();
    private final JTextField jvmOptions = new JTextField();
    private final ImageButton play = new ImageButton(
        new String[]{"/play.png", "/play.jpg", "/Launchplaybutton.png"},
        new String[]{"play.png", "play.jpg", "Launchplaybutton.png", "Assets/play.png"});
    private final ImageButton installUpdate = new ImageButton(
        new String[]{"/install-update.png", "/install-update.jpg", "/installupdatebutton.png"},
        new String[]{"install-update.png", "installupdatebutton.png", "Assets/install-update.png"});
    private final ImageButton settingsButton = new ImageButton(
        new String[]{"/settings.png", "/settings.jpg", "/setting.png"},
        new String[]{"settings.png", "setting.png", "Assets/settings.png"});
    private LauncherUpdater.Update availableUpdate;
    private LauncherInstallation installation;
    private JTextArea changelogArea;
    private JLabel gameDot;
    private JLabel gameDetail;
    private JLabel modDot;
    private JLabel modDetail;
    private JLabel runtimeDot;
    private JLabel runtimeDetail;
    private JLabel memoryDot;
    private JLabel memoryDetail;
    private JButton workshopHelp;
    private boolean refreshRunning;
    private String baseStatus = "";
    private Color baseStatusColor = MUTED;
    private String baseStatusTooltip = "";
    private String updateStatusSuffix = "";

    private enum CheckState {
        CHECKING, OK, WARNING, ERROR
    }

    private enum CheckTarget {
        GAME, MOD, RUNTIME, MEMORY
    }

    private record CheckUpdate(CheckTarget target, CheckState state, String detail) {}

    public static void main(String[] arguments) {
        LauncherLog.write("start version=" + LauncherUpdater.CURRENT_VERSION + " os=" + System.getProperty("os.name")
            + " java=" + System.getProperty("java.version"));
        LauncherLog.sessionSnapshot();
        LauncherUpdater updater = new LauncherUpdater();
        if (updater.launchCachedIfNewer() || updater.updateAndLaunchIfNewer()) return;
        SwingUtilities.invokeLater(() -> {
            UIManager.put("OptionPane.background", BACKGROUND);
            UIManager.put("Panel.background", BACKGROUND);
            UIManager.put("OptionPane.messageForeground", Color.WHITE);
            new Main().show();
        });
    }

    private void show() {
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setContentPane(content());
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent event) {
                if (window.isVisible() && !refreshRunning) refresh(false);
            }
        });
        refresh(true);
    }

    private JPanel content() {
        JPanel screen = new JPanel(new BorderLayout());
        screen.setPreferredSize(new Dimension(1152, 864));
        screen.setBackground(new Color(10, 12, 14));

        BorderedPanel panel = new BorderedPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(1152, 648));

        // Left side stays empty so the title + survivor art remains visible.
        JPanel artSpacer = new JPanel();
        artSpacer.setOpaque(false);
        panel.add(artSpacer, BorderLayout.CENTER);

        // Right column holds the three baked-text image buttons + status.
        // Darkest part of the art lives on the right, so nothing key is covered.
        JPanel column = new JPanel(new GridBagLayout());
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(468, 648));
        column.setBorder(BorderFactory.createEmptyBorder(81, 18, 54, 54));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;

        play.setPreferredSize(new Dimension(414, 138));
        play.setMinimumSize(new Dimension(414, 138));
        play.setMaximumSize(new Dimension(414, 138));
        play.setToolTipText("Verify files and start Project Zomboid with Knox Survivors.");
        play.setEnabled(false);
        play.addActionListener(event -> launch());
        c.gridy = 0;
        c.insets = new Insets(0, 0, 14, 0);
        column.add(play, c);

        installUpdate.setPreferredSize(new Dimension(342, 114));
        installUpdate.setMinimumSize(new Dimension(342, 114));
        installUpdate.setMaximumSize(new Dimension(342, 114));
        installUpdate.setToolTipText("Check for a launcher update.");
        installUpdate.setEnabled(false);
        installUpdate.addActionListener(event -> updateLauncher());
        c.gridy = 1;
        c.insets = new Insets(0, 0, 14, 0);
        column.add(installUpdate, c);

        settingsButton.setPreferredSize(new Dimension(342, 114));
        settingsButton.setMinimumSize(new Dimension(342, 114));
        settingsButton.setMaximumSize(new Dimension(342, 114));
        settingsButton.setToolTipText("Debug mode, custom launch options, JVM memory.");
        settingsButton.setEnabled(false);
        settingsButton.addActionListener(event -> openSettings());
        c.gridy = 2;
        c.insets = new Insets(0, 0, 22, 0);
        column.add(settingsButton, c);

        // No title/subtitle labels here: the background art already carries
        // KNOX SURVIVORS LAUNCHER baked in. The buttons also carry their own
        // PLAY / INSTALL-UPDATE / SETTINGS text, so no overlay text is drawn.
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setForeground(MUTED);
        status.setPreferredSize(new Dimension(414, 40));
        c.gridy = 3;
        c.insets = new Insets(0, 0, 6, 0);
        column.add(status, c);

        workshopHelp = new JButton("OPEN WORKSHOP PAGE IN STEAM");
        workshopHelp.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        workshopHelp.setForeground(GREEN);
        workshopHelp.setOpaque(false);
        workshopHelp.setContentAreaFilled(false);
        workshopHelp.setBorderPainted(false);
        workshopHelp.setFocusPainted(false);
        workshopHelp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        workshopHelp.setVisible(false);
        workshopHelp.addActionListener(event -> openWorkshopPage());
        c.gridy = 4;
        c.insets = new Insets(0, 0, 0, 0);
        column.add(workshopHelp, c);

        styleOptionField(launchOptions, "Same idea as Steam launch options, for example: -cachedir=\"D:\\Zomboid\"");
        styleOptionField(jvmOptions, "Detected only. Project Zomboid owns the JVM memory setting.");
        launchOptions.setText(preferences.get(PREF_LAUNCH_OPTIONS, ""));
        jvmOptions.setText("");
        styleOptionCheck();

        panel.add(column, BorderLayout.EAST);
        screen.add(panel, BorderLayout.NORTH);
        screen.add(infoBar(), BorderLayout.CENTER);
        return screen;
    }

    private JPanel infoBar() {
        JPanel bar = new JPanel(new BorderLayout(24, 0));
        bar.setBackground(new Color(10, 12, 14));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(105, 135, 35)),
            BorderFactory.createEmptyBorder(18, 40, 16, 40)));
        bar.setPreferredSize(new Dimension(1152, 216));

        JPanel changelogPanel = new JPanel(new BorderLayout(0, 8));
        changelogPanel.setOpaque(false);
        JLabel changelogHeader = new JLabel("CHANGELOG / UPDATES");
        changelogHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        changelogHeader.setForeground(GREEN);
        changelogPanel.add(changelogHeader, BorderLayout.NORTH);

        changelogArea = new JTextArea(loadChangelogText());
        changelogArea.setEditable(false);
        changelogArea.setFocusable(false);
        changelogArea.setLineWrap(true);
        changelogArea.setWrapStyleWord(true);
        changelogArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        changelogArea.setForeground(new Color(214, 217, 222));
        changelogArea.setBackground(new Color(16, 18, 22));
        changelogArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane changelogScroll = new JScrollPane(changelogArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        changelogScroll.setBorder(BorderFactory.createLineBorder(new Color(42, 46, 53)));
        changelogScroll.getViewport().setBackground(new Color(16, 18, 22));
        changelogPanel.add(changelogScroll, BorderLayout.CENTER);

        JPanel verifyPanel = new JPanel(new GridBagLayout());
        verifyPanel.setOpaque(false);
        verifyPanel.setPreferredSize(new Dimension(342, 180));
        GridBagConstraints v = new GridBagConstraints();
        v.gridx = 0;
        v.weightx = 1;
        v.fill = GridBagConstraints.HORIZONTAL;
        v.anchor = GridBagConstraints.NORTHWEST;

        JLabel verifyHeader = new JLabel("SYSTEM CHECK");
        verifyHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        verifyHeader.setForeground(GREEN);
        v.gridy = 0;
        v.insets = new Insets(0, 0, 10, 0);
        verifyPanel.add(verifyHeader, v);

        gameDot = new JLabel("\u25CF");
        gameDetail = new JLabel("Checking...");
        v.gridy = 1;
        v.insets = new Insets(0, 0, 10, 0);
        verifyPanel.add(verifyRow(gameDot, "Game", gameDetail), v);

        modDot = new JLabel("\u25CF");
        modDetail = new JLabel("Checking...");
        v.gridy = 2;
        v.insets = new Insets(0, 0, 10, 0);
        verifyPanel.add(verifyRow(modDot, "Workshop mod", modDetail), v);

        runtimeDot = new JLabel("\u25CF");
        runtimeDetail = new JLabel("Checking...");
        v.gridy = 3;
        v.insets = new Insets(0, 0, 10, 0);
        verifyPanel.add(verifyRow(runtimeDot, "Knox runtime", runtimeDetail), v);

        memoryDot = new JLabel("\u25CF");
        memoryDetail = new JLabel("Checking...");
        v.gridy = 4;
        v.insets = new Insets(0, 0, 0, 0);
        verifyPanel.add(verifyRow(memoryDot, "Memory", memoryDetail), v);

        JLabel launcherVersion = new JLabel("Launcher " + LauncherUpdater.CURRENT_VERSION);
        launcherVersion.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        launcherVersion.setForeground(new Color(120, 125, 133));
        v.gridy = 5;
        v.insets = new Insets(12, 0, 0, 0);
        verifyPanel.add(launcherVersion, v);

        bar.add(changelogPanel, BorderLayout.CENTER);
        bar.add(verifyPanel, BorderLayout.EAST);
        return bar;
    }

    private static JPanel verifyRow(JLabel dot, String name, JLabel detail) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        dot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        dot.setForeground(new Color(200, 160, 40));
        dot.setPreferredSize(new Dimension(20, 20));
        row.add(dot, BorderLayout.WEST);
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        JLabel title = new JLabel(name);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        title.setForeground(Color.WHITE);
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        detail.setForeground(MUTED);
        text.add(title, BorderLayout.NORTH);
        text.add(detail, BorderLayout.CENTER);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    private static String loadChangelogText() {
        String[] resourceNames = {"/changelog.txt"};
        for (String name : resourceNames) {
            try (InputStream stream = Main.class.getResourceAsStream(name)) {
                if (stream != null) {
                    String text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (!text.isEmpty()) return text;
                }
            } catch (Exception ignored) { }
        }
        String[] sidecars = {"changelog.txt", "Assets/changelog.txt"};
        for (String name : sidecars) {
            try {
                Path path = Paths.get(name);
                if (Files.isRegularFile(path)) {
                    String text = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (!text.isEmpty()) return text;
                }
            } catch (Exception ignored) { }
        }
        try {
            String jarDir = Paths.get(Main.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getParent().toString();
            for (String name : sidecars) {
                try {
                    Path path = Paths.get(jarDir, name);
                    if (Files.isRegularFile(path)) {
                        String text = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8).trim();
                        if (!text.isEmpty()) return text;
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return "Changelog not found.\nPlace changelog.txt next to the launcher JAR.";
    }

    private void styleOptionField(JTextField field, String tip) {
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        field.setForeground(Color.WHITE);
        field.setBackground(new Color(20, 22, 26));
        field.setCaretColor(GREEN);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(74, 78, 86)),
            BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        field.setToolTipText(tip);
    }

    private void styleOptionCheck() {
        debugMode.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        debugMode.setForeground(Color.WHITE);
        debugMode.setOpaque(false);
        debugMode.setFocusPainted(false);
        debugMode.setSelected(false);
        debugMode.setToolTipText("Starts Project Zomboid with the standard -debug option.");
        debugMode.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void openSettings() {
        JDialog dialog = new JDialog(window, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().setBackground(BACKGROUND);
        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(BACKGROUND);
        body.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 14, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel detectedLabel = new JLabel(detectedMemoryLine(), SwingConstants.CENTER);
        detectedLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        detectedLabel.setForeground(MUTED);
        body.add(detectedLabel, c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 14, 0);
        c.fill = GridBagConstraints.NONE;
        body.add(debugMode, c);

        JLabel optionsLabel = new JLabel("CUSTOM LAUNCH OPTIONS (optional)", SwingConstants.CENTER);
        optionsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        optionsLabel.setForeground(MUTED);
        c.gridy = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        body.add(optionsLabel, c);

        c.gridy = 3;
        c.insets = new Insets(0, 0, 16, 0);
        body.add(launchOptions, c);

        JLabel memoryLabel = new JLabel("JVM MEMORY (READ-ONLY DETECTION)", SwingConstants.CENTER);
        memoryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        memoryLabel.setForeground(MUTED);
        c.gridy = 4;
        c.insets = new Insets(0, 0, 6, 0);
        body.add(memoryLabel, c);

        c.gridy = 5;
        c.insets = new Insets(0, 0, 20, 0);
        body.add(jvmOptions, c);
        jvmOptions.setEditable(false);
        jvmOptions.setText(detectedMemoryLine());
        jvmOptions.setToolTipText("Project Zomboid owns this setting. Edit ProjectZomboid64.json or the game's launcher configuration.");

        JButton save = new JButton("SAVE");
        save.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        save.setBackground(GREEN);
        save.setForeground(new Color(5, 6, 8));
        save.setFocusPainted(false);
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.addActionListener(event -> {
            try {
                GameLauncher.parseLaunchOptions(launchOptions.getText().trim());
                // Memory remains under Project Zomboid's ownership. Settings
                // only gives the player a visible, reliable detection result.
            } catch (LauncherException problem) {
                JOptionPane.showMessageDialog(dialog, problem.getMessage(),
                    "Knox Survivors", JOptionPane.WARNING_MESSAGE);
                return;
            }
            preferences.put(PREF_LAUNCH_OPTIONS, launchOptions.getText().trim());
            dialog.dispose();
            refresh(false);
        });
        c.gridy = 6;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(save, c);

        dialog.setContentPane(body);
        dialog.pack();
        dialog.setSize(560, Math.max(380, dialog.getHeight()));
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }

    private static final class VerifyOutcome {
        LauncherInstallation installation;
        String failure;
        CheckState gameState = CheckState.CHECKING;
        String gameDetail = "Not checked.";
        CheckState modState = CheckState.CHECKING;
        String modDetail = "Not checked.";
        CheckState runtimeState = CheckState.CHECKING;
        String runtimeDetail = "Not checked.";
        CheckState memoryState = CheckState.CHECKING;
        String memoryDetail = "Not checked.";
        int workshopCopies;

        boolean hasWarning() {
            return gameState == CheckState.WARNING || modState == CheckState.WARNING
                || runtimeState == CheckState.WARNING || memoryState == CheckState.WARNING;
        }

        boolean criticalChecksPassed() {
            return gameState != CheckState.ERROR && modState != CheckState.ERROR
                && runtimeState != CheckState.ERROR && memoryState != CheckState.ERROR;
        }
    }

    private static VerifyOutcome verifyAll(SteamLocator locator, InstallationValidator validator,
            String savedJvmOptions, java.util.function.Consumer<CheckUpdate> progress) {
        VerifyOutcome out = new VerifyOutcome();
        LauncherInstallation found;
        try {
            found = locator.locate();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            String message = safeMessage(cause, "Project Zomboid or Knox Survivors could not be located.");
            out.failure = message;
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("project zomboid was found, but knox survivors workshop")) {
                out.gameState = CheckState.WARNING;
                out.gameDetail = "Project Zomboid was found, but full game verification is waiting on the Workshop mod.";
                out.modState = CheckState.ERROR;
                out.modDetail = message;
            } else if (lower.contains("knox survivors was found, but the project zomboid installation")) {
                out.gameState = CheckState.ERROR;
                out.gameDetail = message;
                out.modState = CheckState.WARNING;
                out.modDetail = "Knox Survivors was found, but full Workshop verification is waiting on the game install.";
            } else {
                out.gameState = CheckState.ERROR;
                out.gameDetail = message;
                out.modState = CheckState.ERROR;
                out.modDetail = "Steam/Workshop discovery did not find a complete Knox Survivors install.";
            }
            out.runtimeState = CheckState.WARNING;
            out.runtimeDetail = "Not checked because a complete Workshop install was not available.";
            out.memoryState = CheckState.WARNING;
            out.memoryDetail = "Not checked because a complete Project Zomboid install was not available.";
            publish(progress, CheckTarget.GAME, out.gameState, out.gameDetail);
            publish(progress, CheckTarget.MOD, out.modState, out.modDetail);
            publish(progress, CheckTarget.RUNTIME, out.runtimeState, out.runtimeDetail);
            publish(progress, CheckTarget.MEMORY, out.memoryState, out.memoryDetail);
            LauncherLog.writeException("verification locate failed", exception);
            return out;
        }
        out.installation = found;
        LauncherLog.write("verification paths game=" + found.gameDirectory()
            + " launcher=" + found.gameLauncher()
            + " workshop=" + found.workshopDirectory()
            + " mod=" + found.modDirectory()
            + " agent=" + found.agentJar());

        if (!Files.isDirectory(found.gameDirectory())) {
            out.gameState = CheckState.ERROR;
            out.gameDetail = "Project Zomboid installation folder is missing.";
        } else if (!Files.isRegularFile(found.gameLauncher())) {
            out.gameState = CheckState.ERROR;
            out.gameDetail = "Project Zomboid launcher is missing - verify the game through Steam.";
        } else if (!Files.isRegularFile(found.gameDirectory().resolve("projectzomboid.jar"))) {
            out.gameState = CheckState.ERROR;
            out.gameDetail = "Project Zomboid looks incomplete - verify the game through Steam.";
        } else if (found.platform() == Platform.WINDOWS
                && found.gameLauncher().getFileName().toString().equalsIgnoreCase("ProjectZomboid64.bat")
                && !Files.isRegularFile(found.gameDirectory().resolve("jre64/bin/java.exe"))) {
            out.gameState = CheckState.ERROR;
            out.gameDetail = "Project Zomboid's bundled Java runtime is missing - verify the game through Steam.";
        } else {
            out.gameState = CheckState.OK;
            String launcherName = found.gameLauncher().getFileName().toString();
            out.gameDetail = "Project Zomboid found on " + driveOf(found.gameDirectory())
                + " - " + launcherName + ".";
        }
        publish(progress, CheckTarget.GAME, out.gameState, out.gameDetail);

        String runtimeVersion = "";
        String workshopVersion = "";
        try {
            Path rootInfo = found.modDirectory().resolve("mod.info");
            Path build42Info = found.modDirectory().resolve("42/mod.info");
            if (!modInfoHasId(rootInfo) || !modInfoHasId(build42Info)) {
                out.modState = CheckState.ERROR;
                out.modDetail = "Mod ID KnoxSurvivors is missing from the Workshop package.";
            } else {
                Path buildInfo = found.modDirectory().resolve("42/knox-runtime.properties");
                if (!Files.isRegularFile(buildInfo)) {
                    out.modState = CheckState.ERROR;
                    out.modDetail = "Workshop package is missing knox-runtime.properties - let Steam finish updating.";
                } else {
                    java.util.Properties marker = new java.util.Properties();
                    try (InputStream input = Files.newInputStream(buildInfo)) {
                        marker.load(input);
                    }
                    runtimeVersion = marker.getProperty("runtimeVersion", "").trim();
                    workshopVersion = modInfoVersion(rootInfo);
                    String build42Version = modInfoVersion(build42Info);
                    if (workshopVersion.isEmpty()) workshopVersion = build42Version;
                    if (workshopVersion.isEmpty()) workshopVersion = runtimeVersion;
                    String runtimeType = marker.getProperty("runtime", "").trim();
                    String compatibility = marker.getProperty("launcherCompatibility", "").trim();
                    if (!"iso-player-agent-v1".equals(runtimeType)) {
                        out.modState = CheckState.ERROR;
                        out.modDetail = "Workshop runtime type is '" + runtimeType
                            + "' but this launcher expects iso-player-agent-v1.";
                    } else if (!"1".equals(compatibility)) {
                        out.modState = CheckState.ERROR;
                        out.modDetail = "Workshop launcher compatibility is '" + compatibility
                            + "' but this launcher expects 1.";
                    } else if (runtimeVersion.isEmpty()) {
                        out.modState = CheckState.ERROR;
                        out.modDetail = "Workshop runtime version is missing.";
                    } else if (!workshopVersion.isEmpty() && !workshopVersion.equals(runtimeVersion)) {
                        out.modState = CheckState.ERROR;
                        out.modDetail = "Workshop version " + workshopVersion + " does not match runtime "
                            + runtimeVersion + ". Let Steam finish updating.";
                    } else if (!build42Version.isEmpty() && !build42Version.equals(runtimeVersion)) {
                        out.modState = CheckState.ERROR;
                        out.modDetail = "Build 42 mod version " + build42Version + " does not match runtime "
                            + runtimeVersion + ". Let Steam finish updating.";
                    } else {
                        boolean developmentBuild = isDevelopmentVersion(workshopVersion)
                            || isDevelopmentVersion(runtimeVersion);
                        out.modState = developmentBuild ? CheckState.WARNING : CheckState.OK;
                        out.modDetail = "Workshop " + workshopVersion + " - runtime " + runtimeVersion
                            + " on " + driveOf(found.workshopDirectory())
                            + (developmentBuild ? " - development build." : ".");
                    }
                }
            }
        } catch (Exception exception) {
            out.modState = CheckState.ERROR;
            out.modDetail = "Workshop mod files could not be read: " + safeMessage(exception, "unknown error");
            LauncherLog.writeException("verification Workshop read failed", exception);
        }
        publish(progress, CheckTarget.MOD, out.modState, out.modDetail);

        try {
            Path jar = found.agentJar();
            if (jar == null || !Files.isRegularFile(jar)) {
                out.runtimeState = CheckState.ERROR;
                out.runtimeDetail = "Knox Java runtime JAR is missing.";
            } else {
                String problem = checkAgentJar(jar, runtimeVersion);
                if (problem != null) {
                    out.runtimeState = CheckState.ERROR;
                    out.runtimeDetail = problem;
                } else {
                    boolean developmentRuntime = isDevelopmentVersion(runtimeVersion);
                    out.runtimeState = developmentRuntime ? CheckState.WARNING : CheckState.OK;
                    out.runtimeDetail = "Agent " + (runtimeVersion.isEmpty() ? "version unknown" : runtimeVersion)
                        + " - manifest + checksum OK"
                        + (developmentRuntime ? " - development runtime." : ".");
                }
            }
        } catch (Exception exception) {
            out.runtimeState = CheckState.ERROR;
            out.runtimeDetail = "Runtime could not be inspected: " + safeMessage(exception, "unknown error");
            LauncherLog.writeException("verification runtime inspection failed", exception);
        }
        publish(progress, CheckTarget.RUNTIME, out.runtimeState, out.runtimeDetail);

        try {
            out.workshopCopies = SteamLocator.workshopCopies(found.steamDirectory(), Platform.current()).size();
        } catch (LauncherException unsupported) {
            out.workshopCopies = 0;
            LauncherLog.write("Workshop duplicate scan unavailable: " + unsupported.getMessage());
        }
        if (out.workshopCopies > 1 && out.modState != CheckState.ERROR) {
            out.modState = CheckState.WARNING;
            out.modDetail += " Found " + out.workshopCopies
                + " Workshop copies across Steam libraries; Steam may update a different copy.";
            publish(progress, CheckTarget.MOD, out.modState, out.modDetail);
        }

        describeMemory(out, found, savedJvmOptions);
        publish(progress, CheckTarget.MEMORY, out.memoryState, out.memoryDetail);

        try {
            validator.validate(found);
        } catch (LauncherException exception) {
            out.failure = exception.getMessage();
            LauncherLog.writeException("validator rejected installation", exception);
        }
        if (out.failure == null && !out.criticalChecksPassed()) {
            if (out.gameState == CheckState.ERROR) out.failure = out.gameDetail;
            else if (out.modState == CheckState.ERROR) out.failure = out.modDetail;
            else if (out.runtimeState == CheckState.ERROR) out.failure = out.runtimeDetail;
            else if (out.memoryState == CheckState.ERROR) out.failure = out.memoryDetail;
        }
        return out;
    }

    private static void publish(java.util.function.Consumer<CheckUpdate> progress,
            CheckTarget target, CheckState state, String detail) {
        if (progress != null) progress.accept(new CheckUpdate(target, state, detail));
        LauncherLog.write("check " + target.name().toLowerCase(java.util.Locale.ROOT)
            + "=" + state + " detail=" + detail);
    }

    private static String safeMessage(Throwable problem, String fallback) {
        if (problem == null) return fallback;
        String message = problem.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String driveOf(Path path) {
        try {
            Path root = path.toAbsolutePath().getRoot();
            String text = root != null ? root.toString() : "";
            if (text.endsWith("\\") || text.endsWith("/")) {
                text = text.substring(0, text.length() - 1);
            }
            return text.isEmpty() ? "this drive" : "drive " + text;
        } catch (Exception ignored) {
            return "this drive";
        }
    }

    private static void describeMemory(VerifyOutcome out, LauncherInstallation found, String savedJvmOptions) {
        MemoryProbe.Heap heap = MemoryProbe.gameHeap(found);
        long totalRam = MemoryProbe.totalPhysicalMemoryBytes();
        String ram = totalRam > 0 ? " - PC has " + MemoryProbe.friendlyBytes(totalRam) : "";
        String overrideXmx = "";
        try {
            for (String option : GameLauncher.parseJvmOptions(savedJvmOptions)) {
                if (option.regionMatches(true, 0, "-Xmx", 0, 4)) overrideXmx = option.substring(4);
            }
        } catch (LauncherException invalid) {
            out.memoryState = CheckState.ERROR;
            out.memoryDetail = "Saved memory override is invalid: " + invalid.getMessage();
            return;
        }
        if (!overrideXmx.isEmpty()) {
            long wanted = MemoryProbe.parseHeapBytes(overrideXmx);
            if (wanted <= 0) {
                out.memoryState = CheckState.ERROR;
                out.memoryDetail = "Launcher memory override could not be parsed: " + overrideXmx + ".";
            } else if (totalRam > 0 && wanted > totalRam) {
                out.memoryState = CheckState.ERROR;
                out.memoryDetail = "Memory setting wants " + MemoryProbe.friendlyBytes(wanted)
                    + " but this PC has " + MemoryProbe.friendlyBytes(totalRam) + ".";
            } else {
                out.memoryState = CheckState.OK;
                out.memoryDetail = "Game will use " + MemoryProbe.friendlyBytes(wanted)
                    + " (launcher override)" + ram + ".";
            }
            return;
        }
        if (heap.value().isEmpty()) {
            out.memoryState = CheckState.WARNING;
            out.memoryDetail = "Heap setting was not detected"
                + (heap.source().isEmpty() ? "" : " in " + heap.source())
                + " - Project Zomboid defaults will apply" + ram + ".";
            return;
        }
        long wanted = MemoryProbe.parseHeapBytes(heap.value());
        if (wanted <= 0) {
            out.memoryState = CheckState.WARNING;
            out.memoryDetail = "Detected memory value '" + heap.value() + "' could not be parsed; game settings remain unchanged" + ram + ".";
        } else if (totalRam > 0 && wanted > totalRam) {
            out.memoryState = CheckState.ERROR;
            out.memoryDetail = "Project Zomboid is configured for " + MemoryProbe.friendlyBytes(wanted)
                + " but this PC has " + MemoryProbe.friendlyBytes(totalRam) + ".";
        } else {
            out.memoryState = CheckState.OK;
            out.memoryDetail = "Game will use " + MemoryProbe.friendlyBytes(wanted)
                + " (from " + heap.source() + ")" + ram + ".";
        }
    }

    private static boolean modInfoHasId(Path file) {
        try {
            if (!Files.isRegularFile(file)) return false;
            String text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            return text.lines().map(String::trim).anyMatch("id=KnoxSurvivors"::equals);
        } catch (Exception exception) {
            return false;
        }
    }

    private static String modInfoVersion(Path file) {
        try {
            if (!Files.isRegularFile(file)) return "";
            for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.regionMatches(true, 0, "version=", 0, 8)) {
                    return trimmed.substring(8).trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static boolean isDevelopmentVersion(String version) {
        String value = version == null ? "" : version.toLowerCase(java.util.Locale.ROOT);
        return value.contains("dev") || value.contains("snapshot") || value.contains("alpha");
    }

    private static String checkAgentJar(Path jar, String runtimeVersion) {
        try (java.util.jar.JarFile archive = new java.util.jar.JarFile(jar.toFile())) {
            if (archive.getManifest() == null) return "Runtime JAR has no manifest.";
            java.util.jar.Attributes attributes = archive.getManifest().getMainAttributes();
            String premain = attributes.getValue("Premain-Class");
            if (!"com.knoxsurvivors.agent.KnoxAgent".equals(premain)) {
                return "Runtime Premain-Class is '" + String.valueOf(premain)
                    + "' instead of com.knoxsurvivors.agent.KnoxAgent.";
            }
            String actualVersion = attributes.getValue("Implementation-Version");
            if (!runtimeVersion.isEmpty() && !runtimeVersion.equals(actualVersion)) {
                return "Runtime version mismatch: Workshop expects " + runtimeVersion
                    + " but JAR reports " + String.valueOf(actualVersion) + ".";
            }
        } catch (Exception exception) {
            LauncherLog.writeException("runtime JAR open failed " + jar, exception);
            return "Runtime JAR could not be opened: " + safeMessage(exception, "unknown error") + ".";
        }
        Path checksum = Path.of(jar + ".sha256");
        if (!Files.isRegularFile(checksum)) return "Runtime checksum file is missing.";
        try {
            String expected = Files.readString(checksum, java.nio.charset.StandardCharsets.US_ASCII)
                .trim().split("\\s+", 2)[0].toLowerCase();
            if (!expected.matches("[0-9a-f]{64}")) {
                return "Runtime checksum file is invalid.";
            }
            String actual;
            try (InputStream input = Files.newInputStream(jar)) {
                actual = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            }
            if (!expected.equals(actual)) {
                return "Runtime checksum mismatch (expected " + expected.substring(0, 12)
                    + "..., got " + actual.substring(0, 12) + "...). Verify the Workshop item through Steam.";
            }
        } catch (Exception exception) {
            LauncherLog.writeException("runtime checksum verification failed " + jar, exception);
            return "Runtime checksum could not be verified: " + safeMessage(exception, "unknown error") + ".";
        }
        return null;
    }

    private void setVerifyRow(JLabel dot, JLabel detail, CheckState state, String text) {
        dot.setForeground(switch (state) {
            case OK -> GREEN;
            case WARNING, CHECKING -> WARNING;
            case ERROR -> ERROR;
        });
        detail.setText("<html><div style='width:300px'>" + escapeHtml(text) + "</div></html>");
        detail.setToolTipText(text);
    }

    private void applyCheckUpdate(CheckUpdate update) {
        switch (update.target()) {
            case GAME -> setVerifyRow(gameDot, gameDetail, update.state(), update.detail());
            case MOD -> setVerifyRow(modDot, modDetail, update.state(), update.detail());
            case RUNTIME -> setVerifyRow(runtimeDot, runtimeDetail, update.state(), update.detail());
            case MEMORY -> setVerifyRow(memoryDot, memoryDetail, update.state(), update.detail());
        }
    }

    private String detectedMemoryLine() {
        long total = MemoryProbe.totalPhysicalMemoryBytes();
        String pc = total > 0 ? " - this PC has " + MemoryProbe.friendlyBytes(total) : "";
        if (installation == null) return "Game memory: verify first" + pc + ".";
        MemoryProbe.Heap heap = MemoryProbe.gameHeap(installation);
        if (heap.value().isEmpty()) return "Game memory: game default applies" + pc + ".";
        return "Detected: " + heap.source() + " sets "
            + MemoryProbe.friendlyBytes(MemoryProbe.parseHeapBytes(heap.value())) + pc + ".";
    }

    private void refresh() {
        refresh(false);
    }

    private void refresh(boolean checkUpdater) {
        if (refreshRunning) {
            if (checkUpdater) checkLauncherUpdate();
            return;
        }
        refreshRunning = true;
        setStatus("Checking Steam and Workshop files...", MUTED, "Checking current files...");
        setVerifyRow(gameDot, gameDetail, CheckState.CHECKING, "Checking...");
        setVerifyRow(modDot, modDetail, CheckState.CHECKING, "Waiting for game check...");
        setVerifyRow(runtimeDot, runtimeDetail, CheckState.CHECKING, "Waiting for Workshop check...");
        setVerifyRow(memoryDot, memoryDetail, CheckState.CHECKING, "Waiting for game check...");
        workshopHelp.setVisible(false);
        play.setEnabled(false);
        play.setToolTipText("Checking the current Project Zomboid and Knox Survivors files.");
        settingsButton.setEnabled(true);
        if (checkUpdater) checkLauncherUpdate();
        final String savedJvmOptions = "";
        new SwingWorker<VerifyOutcome, CheckUpdate>() {
            @Override protected VerifyOutcome doInBackground() {
                return verifyAll(locator, validator, savedJvmOptions, update -> publish(update));
            }

            @Override protected void process(java.util.List<CheckUpdate> chunks) {
                for (CheckUpdate update : chunks) applyCheckUpdate(update);
            }

            @Override protected void done() {
                refreshRunning = false;
                try {
                    VerifyOutcome outcome = get();
                    installation = outcome.installation;
                    setVerifyRow(gameDot, gameDetail, outcome.gameState, outcome.gameDetail);
                    setVerifyRow(modDot, modDetail, outcome.modState, outcome.modDetail);
                    setVerifyRow(runtimeDot, runtimeDetail, outcome.runtimeState, outcome.runtimeDetail);
                    setVerifyRow(memoryDot, memoryDetail, outcome.memoryState, outcome.memoryDetail);
                    workshopHelp.setVisible(outcome.failure != null && isWorkshopFailure(outcome.failure));
                    if (outcome.failure == null && outcome.criticalChecksPassed()) {
                        var zombieBuddy = ZombieBuddyCompatibility.inspect(installation);
                        String optional = zombieBuddy.enabled() || zombieBuddy.state().equals("enabled-by-game-launcher")
                            ? "  -  ZombieBuddy detected" : "";
                        if (outcome.hasWarning()) {
                            setStatus("READY WITH WARNING  -  checks passed; review the amber item" + optional,
                                WARNING, "Ready to launch, but one non-blocking check needs attention.");
                            LauncherLog.write("verification ready with warning");
                        } else {
                            setStatus("READY  -  Workshop mod and Knox runtime verified" + optional, GREEN,
                                "Ready. Press PLAY.");
                            LauncherLog.write("verification ready");
                        }
                        play.setEnabled(true);
                        play.setToolTipText("Verified. Start Project Zomboid with Knox Survivors.");
                    } else {
                        String reason = outcome.failure != null ? outcome.failure : "A required system check failed.";
                        setStatus("NOT READY  -  " + reason, ERROR, reason);
                        play.setEnabled(false);
                        play.setToolTipText("Cannot launch yet: " + reason);
                        LauncherLog.write("verification failed: " + reason);
                    }
                } catch (Exception exception) {
                    installation = null;
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    String reason = safeMessage(cause, "Verification failed unexpectedly.");
                    setStatus("NOT READY  -  " + reason, ERROR, reason);
                    setVerifyRow(gameDot, gameDetail, CheckState.ERROR, "Verification stopped unexpectedly - see launcher.log.");
                    play.setEnabled(false);
                    play.setToolTipText("Verification failed. See launcher.log for details.");
                    LauncherLog.writeException("verification worker failed", cause);
                }
                installUpdate.setEnabled(true);
                settingsButton.setEnabled(true);
            }
        }.execute();
    }

    private void checkLauncherUpdate() {
        installUpdate.setEnabled(false);
        installUpdate.setToolTipText("Checking for a launcher update...");
        new SwingWorker<LauncherUpdater.Update, Void>() {
            @Override protected LauncherUpdater.Update doInBackground() throws Exception {
                return new LauncherUpdater().check();
            }
            @Override protected void done() {
                installUpdate.setEnabled(true);
                try {
                    availableUpdate = get();
                    if (availableUpdate != null) {
                        installUpdate.setToolTipText("Update available: " + availableUpdate.version() + " - click INSTALL / UPDATE.");
                        setUpdateStatusSuffix("  -  UPDATE AVAILABLE: " + availableUpdate.version());
                    } else {
                        installUpdate.setToolTipText("Launcher is up to date - click to re-check.");
                        setUpdateStatusSuffix("  -  LAUNCHER UP TO DATE");
                    }
                } catch (Exception exception) {
                    availableUpdate = null;
                    setUpdateStatusSuffix("");
                    installUpdate.setToolTipText("Update check failed - click to retry. This does not block the game.");
                    LauncherLog.writeException("launcher update check failed", exception);
                }
            }
        }.execute();
    }

    private static boolean isWorkshopFailure(String message) {
        String text = message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("workshop") || text.contains("mod.info") || text.contains("mod id")
            || text.contains("runtime") || text.contains("checksum") || text.contains("steam");
    }

    private void openWorkshopPage() {
        String steamUrl = "steam://url/CommunityFilePage/3749727604";
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(steamUrl));
                return;
            }
            throw new UnsupportedOperationException("Desktop browsing is not supported.");
        } catch (Exception exception) {
            LauncherLog.write("workshop page fallback: " + exception);
            JOptionPane.showMessageDialog(window,
                "Open this page in Steam or your browser to let Steam update Knox Survivors:\n"
                    + "https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604",
                "Knox Survivors", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateLauncher() {
        if (availableUpdate == null) { checkLauncherUpdate(); refresh(false); return; }
        installUpdate.setEnabled(false);
        settingsButton.setEnabled(false);
        setUpdateStatusSuffix("");
        setStatus("Downloading launcher update...", GREEN, "Updating...");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                LauncherUpdater updater = new LauncherUpdater();
                updater.install(availableUpdate);
                return updater.launchCachedIfNewer();
            }
            @Override protected void done() {
                try {
                    if (get()) window.dispose();
                    else throw new IllegalStateException("Verified update could not be restarted.");
                } catch (Exception exception) {
                    availableUpdate = null;
                    installUpdate.setEnabled(true);
                    settingsButton.setEnabled(true);
                    String reason = safeMessage(exception, "Launcher update failed.");
                    setStatus("UPDATE FAILED  -  " + reason, ERROR, reason);
                    LauncherLog.writeException("launcher update failed", exception);
                }
            }
        }.execute();
    }

    private void launch() {
        play.setEnabled(false);
        setUpdateStatusSuffix("");
        try {
            LauncherInstallation found = locator.locate();
            validator.validate(found);
            String custom = launchOptions.getText().trim();
            GameLauncher.parseLaunchOptions(custom);
            preferences.put(PREF_LAUNCH_OPTIONS, custom);
            setStatus("Launching Project Zomboid...", GREEN, "Starting the verified Project Zomboid launcher.");
            gameLauncher.launch(found, debugMode.isSelected(), custom, "");
            window.dispose();
        } catch (LauncherException exception) {
            String reason = safeMessage(exception, "Launch was blocked.");
            setStatus("LAUNCH FAILED  -  " + reason, ERROR, reason);
            LauncherLog.writeException("launch blocked", exception);
            JOptionPane.showMessageDialog(window,
                reason + "\n\nDiagnostic log:\n" + LauncherLog.path(),
                "Knox Survivors", JOptionPane.WARNING_MESSAGE);
            play.setEnabled(true);
        } catch (Exception exception) {
            String reason = safeMessage(exception,
                "Project Zomboid could not be launched. Verify Steam and the Workshop download, then try again.");
            setStatus("LAUNCH FAILED  -  " + reason, ERROR, reason);
            LauncherLog.writeException("launch failed", exception);
            JOptionPane.showMessageDialog(window,
                reason + "\n\nDiagnostic log:\n" + LauncherLog.path(),
                "Knox Survivors", JOptionPane.ERROR_MESSAGE);
            play.setEnabled(true);
        }
    }

    private void setStatus(String text, Color color, String tooltip) {
        baseStatus = text;
        baseStatusColor = color;
        baseStatusTooltip = tooltip == null ? "" : tooltip;
        renderStatus();
    }

    private void setUpdateStatusSuffix(String suffix) {
        updateStatusSuffix = suffix == null ? "" : suffix;
        renderStatus();
    }

    private void renderStatus() {
        String shown = baseStatus + updateStatusSuffix;
        status.setText("<html><div style='text-align:center;width:440px'>"
            + escapeHtml(shown) + "</div></html>");
        status.setForeground(baseStatusColor);
        status.setToolTipText(baseStatusTooltip + (updateStatusSuffix.isBlank() ? "" : updateStatusSuffix));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static BufferedImage loadImage(String[] resourceNames, String[] sidecars) {
        for (String name : resourceNames) {
            try (InputStream stream = Main.class.getResourceAsStream(name)) {
                if (stream != null) {
                    BufferedImage loaded = ImageIO.read(stream);
                    if (loaded != null) return loaded;
                }
            } catch (Exception ignored) { }
        }
        for (String name : sidecars) {
            try {
                Path path = Paths.get(name);
                if (Files.isRegularFile(path)) {
                    BufferedImage loaded = ImageIO.read(path.toFile());
                    if (loaded != null) return loaded;
                }
            } catch (Exception ignored) { }
        }
        try {
            String jarDir = null;
            try {
                jarDir = Paths.get(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent().toString();
            } catch (Exception ignored) { }
            if (jarDir != null) {
                for (String name : sidecars) {
                    try {
                        Path path = Paths.get(jarDir, name);
                        if (Files.isRegularFile(path)) {
                            BufferedImage loaded = ImageIO.read(path.toFile());
                            if (loaded != null) return loaded;
                        }
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static final class BorderedPanel extends JPanel {
        private static final Image BACKGROUND_IMAGE = loadImage(
            new String[]{"/background.png", "/background.jpg", "/background.jpeg"},
            new String[]{"background.png", "background.jpg", "background.jpeg",
                "Assets/background.png", "Assets/background.jpg"});

        BorderedPanel(java.awt.LayoutManager layout) {
            super(layout);
            setBackground(BACKGROUND);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (BACKGROUND_IMAGE != null) {
                // Cover-fit: fill window, center-crop overflow, never stretch.
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                int imageWidth = BACKGROUND_IMAGE.getWidth(null);
                int imageHeight = BACKGROUND_IMAGE.getHeight(null);
                if (imageWidth > 0 && imageHeight > 0 && panelWidth > 0 && panelHeight > 0) {
                    double scale = Math.max(
                        (double) panelWidth / imageWidth,
                        (double) panelHeight / imageHeight);
                    int drawWidth = (int) Math.ceil(imageWidth * scale);
                    int drawHeight = (int) Math.ceil(imageHeight * scale);
                    int drawX = (panelWidth - drawWidth) / 2;
                    int drawY = (panelHeight - drawHeight) / 2;
                    g.drawImage(BACKGROUND_IMAGE, drawX, drawY, drawWidth, drawHeight, null);
                    // Readability scrims: full soft dim + stronger right/bottom fade
                    // so the button column stays legible over busy art.
                    g.setColor(new Color(0, 0, 0, 90));
                    g.fillRect(0, 0, panelWidth, panelHeight);
                    g.setPaint(new GradientPaint(
                        panelWidth - 620, 0, new Color(0, 0, 0, 0),
                        panelWidth, 0, new Color(0, 0, 0, 170)));
                    g.fillRect(panelWidth - 620, 0, 620, panelHeight);
                }
            } else {
                g.setColor(BACKGROUND);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(new Color(105, 135, 35));
            g.setStroke(new BasicStroke(1));
            g.drawRect(16, 16, getWidth() - 33, getHeight() - 33);
            g.dispose();
        }
    }

    private static final class ImageButton extends JButton {
        private final BufferedImage image;

        ImageButton(String[] resourceNames, String[] sidecars) {
            BufferedImage loaded = loadImage(resourceNames, sidecars);
            // Button art ships on a baked black plate; key out only the outer
            // background connected to the image border. The dark button face
            // inside the neon frame stays opaque. Background art is untouched.
            this.image = loaded != null ? keyOutOuterBlack(loaded) : null;
            // Baked text lives in the artwork: never paint overlay text.
            super.setText("");
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public void setText(String text) {
            // Ignore: artwork already contains PLAY / INSTALL-UPDATE / SETTINGS.
            super.setText("");
        }

        private static BufferedImage keyOutOuterBlack(BufferedImage source) {
            int width = source.getWidth();
            int height = source.getHeight();
            BufferedImage argb = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[width * height];
            source.getRGB(0, 0, width, height, pixels, 0, width);
            boolean[] outer = new boolean[width * height];
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            for (int x = 0; x < width; x++) {
                queue.add(x);
                queue.add((height - 1) * width + x);
            }
            for (int y = 0; y < height; y++) {
                queue.add(y * width);
                queue.add(y * width + width - 1);
            }
            while (!queue.isEmpty()) {
                int index = queue.poll();
                if (index < 0 || index >= pixels.length || outer[index]) continue;
                outer[index] = true;
                int rgb = pixels[index];
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int luminance = (red + green + blue) / 3;
                int peak = Math.max(red, Math.max(green, blue));
                if (luminance > 22 && peak > 30) continue;
                int x = index % width;
                int y = index / width;
                if (x > 0) queue.add(index - 1);
                if (x < width - 1) queue.add(index + 1);
                if (y > 0) queue.add(index - width);
                if (y < height - 1) queue.add(index + width);
                // Only outer-connected dark pixels become transparent; the
                // flood stops at the neon frame so the button face is kept.
                pixels[index] = 0;
            }
            argb.setRGB(0, 0, width, height, pixels, 0, width);
            return argb;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int width = getWidth();
            int height = getHeight();
            if (image != null && width > 0 && height > 0) {
                // Fit the 3:1 artwork into the button without stretching.
                double scale = Math.min(
                    (double) width / image.getWidth(),
                    (double) height / image.getHeight());
                int drawWidth = (int) Math.round(image.getWidth() * scale);
                int drawHeight = (int) Math.round(image.getHeight() * scale);
                int drawX = (width - drawWidth) / 2;
                int drawY = (height - drawHeight) / 2;
                Composite saved = g.getComposite();
                if (!isEnabled()) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                }
                g.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
                g.setComposite(saved);
                if (getModel().isPressed() && isEnabled()) {
                    g.setColor(new Color(0, 0, 0, 70));
                    g.fillRect(0, 0, width, height);
                } else if (getModel().isRollover() && isEnabled()) {
                    g.setColor(new Color(255, 255, 255, 18));
                    g.fillRect(0, 0, width, height);
                }
            }
            g.dispose();
        }
    }
}
