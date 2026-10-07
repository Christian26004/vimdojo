package vimdojo;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The system's own window for choosing a folder. On macOS that is the Finder's; elsewhere it is
 * Swing's chooser dressed in the system's look, since Windows' native one can't pick folders.
 */
final class FolderPicker {
    private static final String TITLE = "Choose where to keep your .vimdojo folder";

    private FolderPicker() {
    }

    /** Shows the chooser, starting in the given folder. Returns the folder picked, or null. */
    static Path choose(Component over, Path start) {
        Window window = over instanceof Window w ? w : SwingUtilities.getWindowAncestor(over);
        Path from = start != null && Files.isDirectory(start) ? start : null;
        return System.getProperty("os.name", "").toLowerCase().startsWith("mac")
                ? finder(window, from) : swing(window, from);
    }

    private static Path finder(Window window, Path start) {
        // This property turns the Finder's open window into one that picks folders.
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
        try {
            FileDialog dialog = new FileDialog(window instanceof Frame f ? f : null, TITLE,
                    FileDialog.LOAD);
            if (start != null) {
                dialog.setDirectory(start.toString());
            }
            dialog.setVisible(true);
            return dialog.getFile() == null ? null
                    : Path.of(dialog.getDirectory(), dialog.getFile());
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
        }
    }

    private static Path swing(Window window, Path start) {
        // The chooser takes its look when it is made, so the app's own look is put back after.
        LookAndFeel previous = UIManager.getLookAndFeel();
        JFileChooser chooser;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // The system look isn't available: the plain one still works.
        }
        try {
            chooser = new JFileChooser(start == null ? null : start.toFile());
        } finally {
            try {
                UIManager.setLookAndFeel(previous);
            } catch (Exception e) {
                // Nothing to restore.
            }
        }
        chooser.setDialogTitle(TITLE);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showDialog(window, "Select") == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }
}
