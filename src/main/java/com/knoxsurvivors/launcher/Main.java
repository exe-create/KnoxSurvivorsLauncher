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
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public final class Main {
    private static final Color BACKGROUND = new Color(7, 8, 10);
    private static final Color GREEN = new Color(188, 255, 0);
    private static final Color PURPLE = new Color(165, 65, 255);
    private static final Color MUTED = new Color(184, 188, 194);
    private final SteamLocator locator = new SteamLocator();
    private final InstallationValidator validator = new InstallationValidator();
    private final GameLauncher gameLauncher = new GameLauncher();
    private final JFrame window = new JFrame("Knox Survivors");
    private final JLabel status = new JLabel("Checking Steam and Workshop files…", SwingConstants.CENTER);
    private final JButton launch = new LaunchButton();
    private LauncherInstallation installation;

    public static void main(String[] arguments) {
        LauncherLog.write("start version=0.2.0 os=" + System.getProperty("os.name")
            + " java=" + System.getProperty("java.version"));
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
        refresh();
    }

    private JPanel content() {
        JPanel panel = new BorderedPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(720, 400));
        panel.setBorder(BorderFactory.createEmptyBorder(34, 54, 34, 54));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel title = label("KNOX SURVIVORS", 34, Font.BOLD, GREEN);
        c.gridy = 0;
        c.insets = new Insets(8, 0, 0, 0);
        panel.add(title, c);
        JLabel subtitle = label("PROJECT ZOMBOID 42.20", 13, Font.PLAIN, new Color(199, 170, 255));
        c.gridy = 1;
        c.insets = new Insets(2, 0, 34, 0);
        panel.add(subtitle, c);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        status.setForeground(MUTED);
        c.gridy = 2;
        c.insets = new Insets(0, 12, 28, 12);
        panel.add(status, c);
        launch.setText("PLAY KNOX SURVIVORS");
        launch.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        launch.setForeground(new Color(5, 6, 8));
        launch.setPreferredSize(new Dimension(560, 82));
        launch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        launch.setEnabled(false);
        launch.addActionListener(event -> launch());
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(launch, c);
        return panel;
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        label.setForeground(color);
        return label;
    }

    private void refresh() {
        setStatus("Checking Steam and Workshop files…", MUTED);
        launch.setEnabled(false);
        new SwingWorker<LauncherInstallation, Void>() {
            @Override protected LauncherInstallation doInBackground() throws Exception {
                LauncherInstallation found = locator.locate();
                validator.validate(found);
                return found;
            }

            @Override protected void done() {
                try {
                    installation = get();
                    setStatus("READY  •  Workshop mod and Knox runtime verified", GREEN);
                    LauncherLog.write("verification ready");
                    launch.setEnabled(true);
                } catch (Exception exception) {
                    installation = null;
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    setStatus("NOT READY  •  " + cause.getMessage(), new Color(235, 105, 135));
                    LauncherLog.write("verification failed: " + cause);
                    launch.setEnabled(true);
                }
            }
        }.execute();
    }

    private void launch() {
        launch.setEnabled(false);
        try {
            LauncherInstallation found = locator.locate();
            validator.validate(found);
            setStatus("Launching Project Zomboid…", GREEN);
            gameLauncher.launch(found);
            window.dispose();
        } catch (LauncherException exception) {
            setStatus("NOT READY  •  " + exception.getMessage(), new Color(235, 105, 135));
            LauncherLog.write("launch blocked: " + exception);
            JOptionPane.showMessageDialog(window, exception.getMessage(),
                "Knox Survivors", JOptionPane.WARNING_MESSAGE);
            launch.setEnabled(true);
        } catch (Exception exception) {
            String message = "Project Zomboid could not be launched. Verify Steam and the Workshop download, then try again.";
            setStatus("NOT READY  •  " + message, new Color(235, 105, 135));
            LauncherLog.write("launch failed: " + exception);
            JOptionPane.showMessageDialog(window,
                message + "\n\nSupport log: " + LauncherLog.path(),
                "Knox Survivors", JOptionPane.ERROR_MESSAGE);
            launch.setEnabled(true);
        }
    }

    private void setStatus(String text, Color color) {
        status.setText("<html><div style='text-align:center;width:540px'>"
            + escapeHtml(text) + "</div></html>");
        status.setForeground(color);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class BorderedPanel extends JPanel {
        BorderedPanel(GridBagLayout layout) {
            super(layout);
            setBackground(BACKGROUND);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(105, 135, 35));
            g.setStroke(new BasicStroke(1));
            g.drawRect(16, 16, getWidth() - 33, getHeight() - 33);
            g.dispose();
        }
    }

    private static final class LaunchButton extends JButton {
        LaunchButton() {
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color left = isEnabled() ? GREEN : new Color(82, 94, 56);
            Color right = isEnabled() ? PURPLE : new Color(78, 58, 88);
            g.setPaint(new GradientPaint(0, 0, left, getWidth(), 0, right));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(220, 215, 255, 115));
            g.setStroke(new BasicStroke(2));
            g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
