package cc.zolsi.boot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ZolsiApp {

    private static final Color BG = new Color(0x15, 0x14, 0x17);
    private static final Color LIGHT_BLUE = new Color(0x8F, 0xB8, 0xDE);
    private static final Color GREEN = new Color(0x74, 0xC6, 0x8C);
    private static final Color GREY = new Color(0x9A, 0x9A, 0x9E);
    private static final Color DIM = new Color(0x54, 0x52, 0x58);

    private static final String[] BANNER = {
        "######   ####   ##       #####  ######",
        "    ##  ##  ##  ##      ##        ##  ",
        "   ##   ##  ##  ##       ####     ##  ",
        "  ##    ##  ##  ##          ##    ##  ",
        " ##     ##  ##  ##          ##    ##  ",
        "######   ####   ######  #####   ######"
    };

    private static final String[] SPINNER = {"|", "/", "-", "\\"};

    private static JFrame frame;
    private static CardLayout cards;
    private static JPanel root;
    private static HookPanel hookPanel;
    private static volatile String selfJar;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    launch();
                } catch (Throwable t) {
                    crash(t);
                }
            }
        });
    }

    private static void launch() {
        frame = new JFrame("zolsi.cc");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cards = new CardLayout();
        root = new JPanel(cards);
        hookPanel = new HookPanel();
        root.add(hookPanel, "hook");
        frame.setContentPane(root);
        frame.setSize(940, 520);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        selfJar = selfPath();
        cards.show(root, "hook");
        hookPanel.begin(selfJar);
    }

    private static String selfPath() {
        try {
            return new File(
                ZolsiApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void crash(Throwable t) {
        try {
            JOptionPane.showMessageDialog(null, "Zolsi-Mod failed to start:\n" + t,
                "Zolsi-Mod", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
        }
    }

    private static Font pick(int style, int size) {
        Font f = new Font("Consolas", style, size);
        if (!f.getFamily().equalsIgnoreCase("Consolas")) {
            return new Font(Font.MONOSPACED, style, size);
        }
        return f;
    }

    private static void paintBanner(Graphics2D g2, int width, int top) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font banner = pick(Font.BOLD, 15);
        g2.setFont(banner);
        FontMetrics bm = g2.getFontMetrics();
        int lineH = bm.getHeight();
        int y = top;
        g2.setColor(LIGHT_BLUE);
        for (int i = 0; i < BANNER.length; i++) {
            int x = (width - bm.stringWidth(BANNER[i])) / 2;
            g2.drawString(BANNER[i], x, y);
            y += lineH;
        }
        Font small = pick(Font.PLAIN, 14);
        g2.setFont(small);
        FontMetrics sm = g2.getFontMetrics();
        String tag = "utility client   //   zolsi.cc";
        g2.setColor(DIM);
        g2.drawString(tag, (width - sm.stringWidth(tag)) / 2, y + 14);
    }

    private static final class Step {
        final String label;
        final boolean ok;

        Step(String label, boolean ok) {
            this.label = label;
            this.ok = ok;
        }
    }

    private static final class HookPanel extends JPanel {

        private final List<Step> done = new ArrayList<Step>();
        private volatile String current;
        private volatile String finalMessage;
        private volatile String subMessage;
        private volatile Color finalColor = GREY;
        private int spinnerFrame;

        HookPanel() {
            setBackground(BG);
            Timer timer = new Timer(90, e -> {
                spinnerFrame++;
                repaint();
            });
            timer.start();
        }

        void begin(final String self) {
            done.clear();
            finalMessage = null;
            subMessage = null;
            current = null;
            Thread worker = new Thread(new Runnable() {
                public void run() {
                    sequence(self);
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        private void sequence(String self) {
            setCurrent("Scanning for active Minecraft sessions");
            sleep(650);
            ZolsiHook.Target target = ZolsiHook.findTarget();
            if (target == null) {
                complete("Scanning for active Minecraft sessions", false);
                finish("No running Minecraft (Fabric) session was found. Start the game first.", LIGHT_BLUE);
                return;
            }
            complete("Scanning for active Minecraft sessions", true);

            setCurrent("Attaching to the Minecraft JVM");
            sleep(450);
            boolean ok = ZolsiHook.attachAndLoad(target, self);
            complete("Attaching to the Minecraft JVM", ok);
            if (!ok) {
                finish("Injection failed: " + (ZolsiHook.lastError == null ? "unknown error" : ZolsiHook.lastError), LIGHT_BLUE);
                return;
            }

            setCurrent("Installing render hook");
            sleep(550);
            complete("Installing render hook", true);

            finish("Zolsi-Mod is hooked. Press INSERT in game to open the menu.", GREEN);
            startAutoClose();
        }

        private void startAutoClose() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    final int[] remaining = {5};
                    subMessage = "This window closes in " + remaining[0] + " ...";
                    repaint();
                    Timer timer = new Timer(1000, null);
                    timer.addActionListener(e -> {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            timer.stop();
                            System.exit(0);
                        } else {
                            subMessage = "This window closes in " + remaining[0] + " ...";
                            repaint();
                        }
                    });
                    timer.setInitialDelay(1000);
                    timer.start();
                }
            });
        }

        private void setCurrent(final String label) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    current = label;
                    repaint();
                }
            });
        }

        private void complete(final String label, final boolean ok) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    done.add(new Step(label, ok));
                    current = null;
                    repaint();
                }
            });
        }

        private void finish(final String message, final Color color) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    finalMessage = message;
                    finalColor = color;
                    current = null;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            paintBanner(g2, w, 70);

            Font small = pick(Font.PLAIN, 14);
            g2.setFont(small);
            FontMetrics sm = g2.getFontMetrics();

            int sy = 70 + pick(Font.BOLD, 15).getSize() * 6 + 84;
            for (int i = 0; i < done.size(); i++) {
                sy = drawStep(g2, sm, sy, done.get(i));
            }
            if (current != null) {
                String spin = SPINNER[spinnerFrame % SPINNER.length];
                String text = current + " ...";
                int total = 42 + sm.stringWidth(text);
                int x = (w - total) / 2;
                g2.setColor(LIGHT_BLUE);
                g2.drawString("[" + spin + "]", x, sy);
                g2.setColor(GREY);
                g2.drawString(text, x + 42, sy);
                sy += sm.getHeight() + 6;
            }

            if (finalMessage != null) {
                Font f = pick(Font.PLAIN, 15);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(finalColor);
                g2.drawString(finalMessage, (w - fm.stringWidth(finalMessage)) / 2, getHeight() - 60);
            }

            if (subMessage != null) {
                g2.setFont(small);
                g2.setColor(DIM);
                g2.drawString(subMessage, (w - sm.stringWidth(subMessage)) / 2, getHeight() - 34);
            }
        }

        private int drawStep(Graphics2D g2, FontMetrics fm, int y, Step step) {
            int total = 42 + fm.stringWidth(step.label);
            int x = (getWidth() - total) / 2;
            g2.setColor(step.ok ? GREEN : LIGHT_BLUE);
            g2.drawString(step.ok ? "[ok]" : "[--]", x, y);
            g2.setColor(GREY);
            g2.drawString(step.label, x + 42, y);
            return y + fm.getHeight() + 6;
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private ZolsiApp() {
    }
}
