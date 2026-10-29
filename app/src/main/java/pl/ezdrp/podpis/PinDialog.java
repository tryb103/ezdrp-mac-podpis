package pl.ezdrp.podpis;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/** Okno z prośbą o PIN do karty (odpowiednik okna PIN w NASK Desk). */
public final class PinDialog {

    private PinDialog() {}

    /** @return PIN albo null, gdy użytkownik anulował. */
    public static char[] ask(String title, String message, String detail) {
        AtomicReference<char[]> result = new AtomicReference<>();
        Runnable r = () -> {
            JPasswordField pf = new JPasswordField(12);
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            JLabel l = new JLabel("<html><b>" + esc(message) + "</b><br>" + (detail == null ? "" : esc(detail)) + "</html>");
            panel.add(l, BorderLayout.NORTH);
            panel.add(pf, BorderLayout.CENTER);
            JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog dlg = pane.createDialog(null, title);
            dlg.setAlwaysOnTop(true);
            dlg.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowGainedFocus(java.awt.event.WindowEvent e) { pf.requestFocusInWindow(); }
            });
            pf.addActionListener(e -> { pane.setValue(JOptionPane.OK_OPTION); dlg.dispose(); });
            dlg.setVisible(true);
            Object v = pane.getValue();
            if (v instanceof Integer i && i == JOptionPane.OK_OPTION) result.set(pf.getPassword());
            dlg.dispose();
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeAndWait(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get();
    }

    public static void info(String title, String message) {
        show(title, message, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(String title, String message) {
        show(title, message, JOptionPane.ERROR_MESSAGE);
    }

    private static void show(String title, String message, int type) {
        Runnable r = () -> {
            JOptionPane pane = new JOptionPane("<html><body style='width:360px'>" + esc(message) + "</body></html>", type);
            JDialog dlg = pane.createDialog(null, title);
            dlg.setAlwaysOnTop(true);
            dlg.setVisible(true);
            dlg.dispose();
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeAndWait(r);
        } catch (Exception ignored) {}
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
