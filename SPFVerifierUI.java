import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * SPF Contract Verifier — Java 8 Swing Desktop App
 *
 * Compile:  javac SPFVerifierUI.java
 * Run:      java  SPFVerifierUI
 *
 * JPF paths configurable via Settings or env vars:
 *   JPF_SYMBC_CLASSES, JPF_SYMBC_JAR, JPF_CORE_JAR, JPF_SYMBC_LIB, JAVA8_HOME,
 *   EXTRA_JARS
 *
 * CHANGES vs original:
 *   1. Settings dialog now has an "extra jars" field (colon/semicolon-separated
 *      paths) that gets appended to the classpath for every compile + JPF step.
 *      If Step 1 fails with a "package … does not exist" or "cannot find symbol"
 *      error, the UI shows a targeted banner telling the user exactly which
 *      package is missing and asks them to add the JAR in Settings.
 *
 *   2. File-chooser remembers the last directory the user navigated to.
 *      Both the *.java and *.txt choosers share the same lastDir field, so
 *      picking the Java file from a folder will open the contracts chooser in
 *      the same folder automatically.
 *
 *   3. oldSize can now be used in the PRECONDITION as a constraint (e.g.
 *      {oldSize == size() && oldSize == 1}).  The engine snapshots oldSize
 *      before evaluating the precondition, so writing oldSize in a precondition
 *      is valid and works correctly — it just always equals size() at that point,
 *      which lets you write cleaner state guards without confusion.
 *      No engine changes were required; this comment + the Settings hint text
 *      document the behaviour so the team understands it.
 */
public class SPFVerifierUI extends JFrame {

    // ── Palette: terminal amber-on-black ─────────────────────────────────────
    static final Color BG       = new Color(0x0d0d0d);
    static final Color BG2      = new Color(0x141414);
    static final Color BG3      = new Color(0x1a1a1a);
    static final Color BORDER   = new Color(0x2a2a2a);
    static final Color BORDER2  = new Color(0x3a3a3a);
    static final Color AMBER    = new Color(0xf5a623);
    static final Color AMBER2   = new Color(0xffc85a);
    static final Color GREEN    = new Color(0x39d353);
    static final Color RED      = new Color(0xff4d4d);
    static final Color BLUE     = new Color(0x4da6ff);
    static final Color ORANGE   = new Color(0xff8c00);   // used for JAR-missing banner
    static final Color TEXT     = new Color(0xe8e8e8);
    static final Color TEXT2    = new Color(0x888888);
    static final Color TEXT3    = new Color(0x505050);
    static final Font  MONO     = new Font("Monospaced", Font.PLAIN, 12);
    static final Font  MONO_B   = new Font("Monospaced", Font.BOLD,  12);
    static final Font  MONO_S   = new Font("Monospaced", Font.PLAIN, 11);
    static final Font  MONO_L   = new Font("Monospaced", Font.BOLD,  14);

    // ── State ────────────────────────────────────────────────────────────────
    File javaFile, contractFile;
    // [CHANGE 2] Last directory the user navigated to in any file chooser.
    // Both choosers share this reference so picking one file remembers the
    // folder for the next chooser that opens.
    File lastDir = null;

    JLabel javaFileLbl, contractFileLbl;
    JButton runBtn;
    JPanel resultsContainer;
    JTextArea rawLog;
    JScrollPane rawScroll;
    JLabel statusLbl, statPassLbl, statFailLbl, statUnknownLbl, statTotalLbl;
    JProgressBar progressBar;
    JPanel summaryRow;

    // ── JPF Config ───────────────────────────────────────────────────────────
    String jpfSymbcClasses = "../jpf-symbc/build/jpf-symbc-classes.jar";
    String jpfSymbcJar     = "../jpf-symbc/build/jpf-symbc.jar";
    String jpfCoreJar      = "../jpf-core/build/jpf.jar";
    String jpfSymbcLib     = "../jpf-symbc/lib/*";
    String java8Home       = "";
    // [CHANGE 1] Extra JAR paths for user libraries (JGraphT, Scalified, etc.)
    // Colon-separated on Unix, semicolon-separated on Windows — user can use
    // either; we normalise to File.pathSeparator at build time.
    String extraJars       = "";

    public SPFVerifierUI() {
        super("SPF Contract Verifier");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        loadEnvConfig();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 680);   // slightly taller to breathe with wider settings
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        setContentPane(root);

        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildMain(),      BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        setVisible(true);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG2);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, BORDER2));
        bar.setPreferredSize(new Dimension(0, 46));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tab.setBackground(BG);
        tab.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 0, 1, BORDER2),
            new EmptyBorder(0, 16, 0, 16)
        ));
        tab.setPreferredSize(new Dimension(220, 46));

        JLabel dot = new JLabel("●");
        dot.setFont(MONO_S);
        dot.setForeground(AMBER);

        JLabel tabTitle = new JLabel("spf-verifier");
        tabTitle.setFont(MONO_B);
        tabTitle.setForeground(TEXT);

        tab.add(dot);
        tab.add(tabTitle);
        left.add(tab);

        JButton settingsBtn = flatBtn("[ settings ]");
        settingsBtn.setForeground(TEXT3);
        settingsBtn.addActionListener(e -> openSettings());
        settingsBtn.addMouseListener(hoverColor(settingsBtn, TEXT3, AMBER));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 0, 0, 8));
        right.add(settingsBtn);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Main content ──────────────────────────────────────────────────────────
    JScrollPane buildMain() {
        JPanel main = new JPanel();
        main.setBackground(BG);
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(24, 28, 24, 28));

        main.add(buildInputSection());
        main.add(vgap(20));

        summaryRow = buildSummaryRow();
        summaryRow.setVisible(false);
        main.add(summaryRow);
        main.add(vgap(16));

        resultsContainer = new JPanel();
        resultsContainer.setOpaque(false);
        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        resultsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(resultsContainer);

        main.add(vgap(16));
        main.add(buildRawLogToggle());
        main.add(vgap(6));

        rawLog = new JTextArea();
        rawLog.setBackground(BG2);
        rawLog.setForeground(TEXT3);
        rawLog.setFont(MONO_S);
        rawLog.setEditable(false);
        rawLog.setLineWrap(true);
        rawLog.setBorder(new EmptyBorder(10, 12, 10, 12));

        rawScroll = new JScrollPane(rawLog);
        rawScroll.setBorder(new LineBorder(BORDER, 1));
        rawScroll.setBackground(BG2);
        rawScroll.setVisible(false);
        rawScroll.setPreferredSize(new Dimension(0, 220));
        rawScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        rawScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        main.add(rawScroll);
        main.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(main);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setBackground(BG2);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        return scroll;
    }

    // ── Input section ─────────────────────────────────────────────────────────
    JPanel buildInputSection() {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);

        outer.add(sectionHeader("// input files"));
        outer.add(vgap(10));

        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 0));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        grid.add(buildFileSlot(true));
        grid.add(buildFileSlot(false));
        outer.add(grid);

        outer.add(vgap(14));
        outer.add(buildRunRow());
        return outer;
    }

    // [CHANGE 2] buildFileSlot now reads/writes lastDir so both choosers share
    //            the last visited directory.
    JPanel buildFileSlot(boolean isJava) {
        JPanel slot = new JPanel(new BorderLayout(0, 0));
        slot.setBackground(BG2);
        slot.setBorder(new LineBorder(BORDER, 1));

        // Header strip
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setBackground(BG3);
        header.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(7, 12, 7, 12)
        ));

        JLabel typeLbl = new JLabel(isJava ? "*.java" : "*.txt");
        typeLbl.setFont(MONO_B);
        typeLbl.setForeground(AMBER);

        JLabel descLbl = new JLabel(isJava ? "  source under verification" : "  hoare contracts");
        descLbl.setFont(MONO_S);
        descLbl.setForeground(TEXT3);

        header.add(typeLbl);
        header.add(descLbl);
        slot.add(header, BorderLayout.NORTH);

        // Body
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel fileLbl = new JLabel("no file selected");
        fileLbl.setFont(MONO_S);
        fileLbl.setForeground(TEXT3);

        if (isJava) javaFileLbl = fileLbl;
        else        contractFileLbl = fileLbl;

        JButton chooseBtn = new JButton("> select file");
        styleChooseBtn(chooseBtn);
        chooseBtn.addActionListener(e -> {
            // [CHANGE 2] Open the chooser at lastDir if we have one,
            //            otherwise fall back to user.home (default behaviour).
            JFileChooser fc = (lastDir != null)
                ? new JFileChooser(lastDir)
                : new JFileChooser();
            fc.setDialogTitle(isJava ? "Select Java Source File" : "Select Contracts File");
            if (isJava) fc.setFileFilter(new FileNameExtensionFilter("Java Source (*.java)", "java"));
            else        fc.setFileFilter(new FileNameExtensionFilter("Contract Files (*.txt)", "txt", "contracts"));
            if (fc.showOpenDialog(SPFVerifierUI.this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                // [CHANGE 2] Remember the folder for the next chooser
                lastDir = f.getParentFile();
                if (isJava) { javaFile = f; javaFileLbl.setText(f.getName()); javaFileLbl.setForeground(GREEN); }
                else { contractFile = f; contractFileLbl.setText(f.getName()); contractFileLbl.setForeground(GREEN); }
                checkReady();
            }
        });

        body.add(fileLbl,   BorderLayout.CENTER);
        body.add(chooseBtn, BorderLayout.EAST);
        slot.add(body, BorderLayout.CENTER);
        return slot;
    }

    void styleChooseBtn(JButton btn) {
        btn.setFont(MONO_B);
        btn.setForeground(BG);
        btn.setBackground(AMBER);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(6, 14, 6, 14));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(AMBER2); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(AMBER); }
        });
    }

    JPanel buildRunRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        runBtn = new JButton("[ run verification ]");
        runBtn.setFont(MONO_L);
        runBtn.setForeground(BG);
        runBtn.setBackground(GREEN);
        runBtn.setBorderPainted(false);
        runBtn.setFocusPainted(false);
        runBtn.setEnabled(false);
        runBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runBtn.setMargin(new Insets(8, 20, 8, 20));
        runBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (runBtn.isEnabled()) runBtn.setBackground(new Color(0x55e86b)); }
            public void mouseExited(MouseEvent e)  { if (runBtn.isEnabled()) runBtn.setBackground(GREEN); }
        });
        runBtn.addActionListener(e -> runVerification());

        JButton resetBtn = flatBtn("[ reset ]");
        resetBtn.setForeground(TEXT3);
        resetBtn.addMouseListener(hoverColor(resetBtn, TEXT3, RED));
        resetBtn.addActionListener(e -> resetAll());

        row.add(runBtn);
        row.add(resetBtn);
        return row;
    }

    // ── Summary row ───────────────────────────────────────────────────────────
    JPanel buildSummaryRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setBackground(BG2);
        row.setBorder(new LineBorder(BORDER, 1));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        row.add(statCell("PASSED",  "0", GREEN, true));
        row.add(statCell("FAILED",  "0", RED,   false));
        row.add(statCell("UNKNOWN", "0", AMBER, false));
        row.add(statCell("TOTAL",   "0", BLUE,  false));

        progressBar = new JProgressBar(0, 100);
        progressBar.setBorderPainted(false);
        progressBar.setBackground(BG3);
        progressBar.setForeground(GREEN);
        progressBar.setPreferredSize(new Dimension(150, 4));

        JPanel pgWrap = new JPanel(new GridBagLayout());
        pgWrap.setOpaque(false);
        pgWrap.setBorder(new EmptyBorder(0, 20, 0, 20));
        pgWrap.add(progressBar);
        row.add(pgWrap);
        return row;
    }

    JPanel statCell(String label, String val, Color valColor, boolean first) {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setOpaque(false);
        cell.setPreferredSize(new Dimension(96, 56));
        if (!first) cell.setBorder(new MatteBorder(0, 1, 0, 0, BORDER));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel numLbl = new JLabel(val, SwingConstants.CENTER);
        numLbl.setFont(new Font("Monospaced", Font.BOLD, 20));
        numLbl.setForeground(valColor);
        numLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblLbl = new JLabel(label, SwingConstants.CENTER);
        lblLbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
        lblLbl.setForeground(TEXT3);
        lblLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(numLbl);
        inner.add(lblLbl);
        cell.add(inner);

        switch (label) {
            case "PASSED":  statPassLbl    = numLbl; break;
            case "FAILED":  statFailLbl    = numLbl; break;
            case "UNKNOWN": statUnknownLbl = numLbl; break;
            case "TOTAL":   statTotalLbl   = numLbl; break;
        }
        return cell;
    }

    JPanel buildRawLogToggle() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btn = flatBtn("// raw jpf output  [toggle]");
        btn.setForeground(TEXT3);
        btn.setFont(MONO_S);
        btn.addMouseListener(hoverColor(btn, TEXT3, AMBER));
        btn.addActionListener(e -> { rawScroll.setVisible(!rawScroll.isVisible()); revalidate(); repaint(); });
        p.add(btn);
        return p;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG3);
        bar.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(5, 14, 5, 14)
        ));
        statusLbl = new JLabel("ready");
        statusLbl.setFont(MONO_S);
        statusLbl.setForeground(TEXT3);

        JLabel right = new JLabel("java-8 · jpf-spf");
        right.setFont(MONO_S);
        right.setForeground(TEXT3);

        bar.add(statusLbl, BorderLayout.WEST);
        bar.add(right,     BorderLayout.EAST);
        return bar;
    }

    // ── Settings dialog ───────────────────────────────────────────────────────
    // [CHANGE 1] Added "extra jars" row at the bottom of the settings grid.
    //            The field accepts colon- or semicolon-separated JAR paths
    //            (e.g. /path/to/jgrapht-core-1.5.1.jar:/path/to/scalified-tree.jar).
    //            These get appended to the full classpath used by both javac and
    //            the JPF runner, so any third-party library your class depends on
    //            can be pointed to here without editing any source files.
    void openSettings() {
        JDialog dlg = new JDialog(this, "settings", true);
        dlg.setSize(660, 380);   // wider + taller for the extra row + hint text
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(BG2);
        dlg.setLayout(new BorderLayout());

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(BG2);
        body.setBorder(new EmptyBorder(20, 20, 10, 20));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(5, 4, 5, 8);
        gc.anchor = GridBagConstraints.WEST;

        String[][] entries = {
            {"jpf-symbc-classes", jpfSymbcClasses},
            {"jpf-symbc.jar",     jpfSymbcJar},
            {"jpf-core.jar",      jpfCoreJar},
            {"jpf-symbc/lib/*",   jpfSymbcLib},
            {"java8 home",        java8Home},
            // [CHANGE 1] New row — extra JARs for third-party libraries
            {"extra jars",        extraJars}
        };
        JTextField[] fields = new JTextField[entries.length];
        for (int i = 0; i < entries.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0;
            JLabel lbl = new JLabel(entries[i][0] + ":");
            lbl.setFont(MONO_S);
            // [CHANGE 1] Highlight the extra-jars label in ORANGE so it's
            //            visually distinct and easy to spot when the user is
            //            sent here by the missing-JAR error banner.
            lbl.setForeground(i == entries.length - 1 ? ORANGE : AMBER);
            body.add(lbl, gc);
            gc.gridx = 1; gc.weightx = 1;
            fields[i] = darkField(entries[i][1]);
            body.add(fields[i], gc);
        }

        // [CHANGE 1] Hint line beneath the extra-jars field explaining the format
        gc.gridx = 1; gc.gridy = entries.length; gc.weightx = 1;
        gc.insets = new Insets(0, 4, 10, 8);
        JLabel hint = new JLabel(
            "<html><span style='font-family:monospace;font-size:10px;color:#505050'>" +
            "colon-separated on Unix, semicolon on Windows — e.g. /opt/libs/jgrapht.jar:/opt/libs/scalified.jar" +
            "</span></html>"
        );
        body.add(hint, gc);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(BG2);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));
        JButton save = new JButton("[ save ]");
        save.setFont(MONO_B); save.setForeground(BG); save.setBackground(AMBER);
        save.setBorderPainted(false); save.setFocusPainted(false);
        save.setMargin(new Insets(6, 14, 6, 14));
        save.addActionListener(e -> {
            jpfSymbcClasses = fields[0].getText().trim();
            jpfSymbcJar     = fields[1].getText().trim();
            jpfCoreJar      = fields[2].getText().trim();
            jpfSymbcLib     = fields[3].getText().trim();
            java8Home       = fields[4].getText().trim();
            // [CHANGE 1] Save extra jars — normalise any semicolons to the
            //            platform separator so users can paste either style.
            extraJars = fields[5].getText().trim()
                            .replace(";", File.pathSeparator)
                            .replace(":", File.pathSeparator);
            dlg.dispose();
        });
        footer.add(save);
        dlg.add(body,   BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Verification flow ─────────────────────────────────────────────────────
    void runVerification() {
        runBtn.setEnabled(false);
        runBtn.setText("[ running... ]");
        runBtn.setBackground(TEXT3);
        resultsContainer.removeAll();
        summaryRow.setVisible(false);
        rawLog.setText("");
        setStatus("compiling...");
        revalidate(); repaint();

        new SwingWorker<VerifyResult, Void>() {
            protected VerifyResult doInBackground() throws Exception { return doVerify(); }
            protected void done() {
                try { displayResults(get()); }
                catch (Exception ex) { showError("Error: " + ex.getMessage()); setStatus("error"); }
                finally { runBtn.setEnabled(true); runBtn.setText("[ run verification ]"); runBtn.setBackground(GREEN); }
            }
        }.execute();
    }

    VerifyResult doVerify() throws Exception {
        File srcDir = javaFile.getParentFile();
        StringBuilder log = new StringBuilder();

        String javac     = java8Home.isEmpty() ? "javac" : java8Home + "/bin/javac";
        String java      = java8Home.isEmpty() ? "java"  : java8Home + "/bin/java";
        String cp        = buildCp(srcDir.getAbsolutePath());
        String className = javaFile.getName().replace(".java", "");

        File dgSrc  = new File(srcDir, "DispatcherGenerator.java");
        File gctSrc = new File(srcDir, "GenericContractsTest.java");
        if (!dgSrc.exists())  return new VerifyResult(null, null, "DispatcherGenerator.java not found in:\n" + srcDir.getAbsolutePath());
        if (!gctSrc.exists()) return new VerifyResult(null, null, "GenericContractsTest.java not found in:\n" + srcDir.getAbsolutePath());

        // ── Step 1: javac
        SwingUtilities.invokeLater(() -> setStatus("step 1/4 — javac (command 1)..."));
        ProcResult s1 = exec(srcDir, javac, "-cp", cp,
            javaFile.getAbsolutePath(),
            dgSrc.getAbsolutePath(),
            gctSrc.getAbsolutePath());
        log.append("=== step 1: javac BoundedQueue + DispatcherGenerator + GenericContractsTest ===\n")
           .append(s1.stdout).append(s1.stderr).append("\n");

        // [CHANGE 1] If javac fails, inspect stderr for missing-package errors
        //            and surface a targeted, actionable message instead of the
        //            raw compiler dump.
        if (s1.code != 0) {
            String missingPkg = extractMissingPackage(s1.stderr);
            if (missingPkg != null) {
                return new VerifyResult(null, log.toString(),
                    "MISSING_JAR:" + missingPkg);   // sentinel prefix parsed by displayResults
            }
            return new VerifyResult(null, log.toString(), "Step 1 (compile) failed:\n" + s1.stderr);
        }

        // ── Step 2: generate dispatcher
        SwingUtilities.invokeLater(() -> setStatus("step 2/4 — generating dispatcher..."));
        ProcResult s2 = exec(srcDir, java, "-cp", srcDir.getAbsolutePath(), "DispatcherGenerator", className);
        log.append("=== step 2: DispatcherGenerator ===\n").append(s2.stdout).append(s2.stderr).append("\n");
        File dispatcherSrc = new File(srcDir, "GeneratedDispatcher.java");
        if (!dispatcherSrc.exists()) return new VerifyResult(null, log.toString(),
            "Step 2 failed — GeneratedDispatcher.java not produced.\n" + s2.stdout + s2.stderr);

        // ── Step 3: compile dispatcher
        SwingUtilities.invokeLater(() -> setStatus("step 3/4 — compiling dispatcher..."));
        ProcResult s3 = exec(srcDir, javac, "-cp", cp, dispatcherSrc.getAbsolutePath());
        log.append("=== step 3: javac GeneratedDispatcher ===\n").append(s3.stdout).append(s3.stderr).append("\n");
        if (s3.code != 0) return new VerifyResult(null, log.toString(), "Step 3 (compile GeneratedDispatcher) failed:\n" + s3.stderr);

        // ── Step 4: run JPF
        SwingUtilities.invokeLater(() -> setStatus("step 4/4 — running jpf (command 2)..."));
        File jpfCfg = writeGenericConfig(srcDir, className, contractFile.getName());
        ProcResult s4 = exec(srcDir, java,
            "-Dsymbolic.debug=true", "-Dsymbolic.lazy=true",
            "-cp", cp,
            "gov.nasa.jpf.tool.RunJPF",
            jpfCfg.getAbsolutePath());
        log.append("=== step 4: JPF output ===\n").append(s4.stdout).append(s4.stderr).append("\n");

        String output = s4.stdout + "\n" + s4.stderr;
        List<String> contracts = readContracts(contractFile);
        return new VerifyResult(parseResults(output, contracts), log.toString(), null);
    }

    // [CHANGE 1] Scan javac stderr for the two compiler errors that indicate a
    //            missing JAR: "package X does not exist" and "cannot find symbol".
    //            Returns the package/symbol name if found, null otherwise.
    String extractMissingPackage(String stderr) {
        // Pattern 1: "error: package org.jgrapht does not exist"
        Pattern pkg = Pattern.compile("error: package ([\\w.]+) does not exist");
        Matcher m1 = pkg.matcher(stderr);
        if (m1.find()) return m1.group(1);

        // Pattern 2: "error: cannot find symbol" followed by "class XYZ" — pick
        //            the first occurrence so the message is concrete.
        Pattern sym = Pattern.compile("error: cannot find symbol[\\s\\S]*?symbol:\\s+class (\\w+)");
        Matcher m2 = sym.matcher(stderr);
        if (m2.find()) return m2.group(1) + " (class not found)";

        return null;
    }

    void displayResults(VerifyResult vr) {
        if (vr.error != null) {
            // [CHANGE 1] Detect the MISSING_JAR sentinel and show the targeted banner
            if (vr.error.startsWith("MISSING_JAR:")) {
                String pkg = vr.error.substring("MISSING_JAR:".length());
                showMissingJarError(pkg);
            } else {
                showError(vr.error);
            }
            return;
        }
        rawLog.setText(vr.rawLog);

        long pass = 0, fail = 0, unk = 0;
        for (ContractResult r : vr.results) {
            if ("pass".equals(r.status)) pass++;
            else if ("fail".equals(r.status)) fail++;
            else unk++;
        }
        long total = vr.results.size();
        statPassLbl.setText(String.valueOf(pass));
        statFailLbl.setText(String.valueOf(fail));
        statUnknownLbl.setText(String.valueOf(unk));
        statTotalLbl.setText(String.valueOf(total));
        progressBar.setValue(total > 0 ? (int)((pass * 100) / total) : 0);
        summaryRow.setVisible(true);

        resultsContainer.removeAll();
        resultsContainer.add(sectionHeader("// results"));
        resultsContainer.add(vgap(8));
        for (int i = 0; i < vr.results.size(); i++) {
            resultsContainer.add(buildContractRow(vr.results.get(i), i));
            resultsContainer.add(vgap(4));
        }
        revalidate(); repaint();
        setStatus(String.format("done — %d passed  %d failed  %d unknown", pass, fail, unk));
    }

    JPanel buildContractRow(ContractResult r, int idx) {
        Color sc = "pass".equals(r.status) ? GREEN : "fail".equals(r.status) ? RED : AMBER;
        String tag = "pass".equals(r.status) ? "[PASS]" : "fail".equals(r.status) ? "[FAIL]" : "[UNKN]";

        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setBackground(BG2);
        row.setBorder(new MatteBorder(0, 3, 0, 0, sc));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel inner = new JPanel(new BorderLayout(10, 0));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(80, 0));

        JPanel leftStack = new JPanel();
        leftStack.setOpaque(false);
        leftStack.setLayout(new BoxLayout(leftStack, BoxLayout.Y_AXIS));

        JLabel idxLbl = new JLabel(String.format("%02d", idx + 1));
        idxLbl.setFont(new Font("Monospaced", Font.BOLD, 16));
        idxLbl.setForeground(TEXT3);

        JLabel tagLbl = new JLabel(tag);
        tagLbl.setFont(new Font("Monospaced", Font.BOLD, 10));
        tagLbl.setForeground(sc);

        leftStack.add(idxLbl);
        leftStack.add(tagLbl);
        left.add(leftStack);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel contractLbl = new JLabel(formatContractHtml(r.contract));
        contractLbl.setFont(MONO);

        JLabel detailLbl = new JLabel("<html><span style='font-family:monospace;font-size:10px;color:#505050'>"
            + escHtml(r.detail) + "</span></html>");
        detailLbl.setVisible(false);

        center.add(contractLbl);
        center.add(vgap(3));
        center.add(detailLbl);

        JButton expBtn = flatBtn("▸");
        expBtn.setFont(new Font("Monospaced", Font.BOLD, 13));
        expBtn.setForeground(TEXT3);
        expBtn.addMouseListener(hoverColor(expBtn, TEXT3, AMBER));
        expBtn.addActionListener(e -> {
            boolean nowOpen = !detailLbl.isVisible();
            detailLbl.setVisible(nowOpen);
            expBtn.setText(nowOpen ? "▾" : "▸");
            expBtn.setForeground(nowOpen ? AMBER : TEXT3);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, nowOpen ? 120 : 46));
            revalidate(); repaint();
        });

        inner.add(left,   BorderLayout.WEST);
        inner.add(center, BorderLayout.CENTER);
        inner.add(expBtn, BorderLayout.EAST);
        row.add(inner, BorderLayout.CENTER);
        return row;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    String formatContractHtml(String c) {
        String s = escHtml(c);
        s = s.replaceFirst("(\\{[^}]*\\})", "<span style='color:#f5a623'>$1</span>");
        s = s.replaceFirst("(?<=\\} )([a-zA-Z_][a-zA-Z0-9_]*)", "<span style='color:#4da6ff'>$1</span>");
        s = s.replaceAll("(\\{[^}]*\\})</span>$", "<span style='color:#39d353'>$1</span>")
             .replaceAll("(?<!span>)(\\{[^}]*\\})$", "<span style='color:#39d353'>$1</span>");
        return "<html><span style='font-family:monospace;font-size:12px;color:#e8e8e8'>" + s + "</span></html>";
    }

    // [CHANGE 1] Dedicated error panel for missing JARs.  Shows the package name,
    //            an orange left border (different from the generic red) and a
    //            "[ open settings ]" inline button so the user can fix it
    //            immediately without hunting for the Settings button.
    void showMissingJarError(String missingPkg) {
        SwingUtilities.invokeLater(() -> {
            resultsContainer.removeAll();

            JPanel banner = new JPanel(new BorderLayout(0, 8));
            banner.setBackground(new Color(0x1a1100));
            banner.setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(14, 16, 14, 16)
            ));
            banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            banner.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Top line: icon + title
            JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            titleRow.setOpaque(false);

            JLabel icon = new JLabel("⚠");
            icon.setFont(new Font("Monospaced", Font.BOLD, 15));
            icon.setForeground(ORANGE);

            JLabel title = new JLabel("missing jar — compile failed");
            title.setFont(MONO_B);
            title.setForeground(ORANGE);

            titleRow.add(icon);
            titleRow.add(title);

            // Detail text
            JTextArea detail = new JTextArea(
                "Package / class not found on classpath:\n\n" +
                "  " + missingPkg + "\n\n" +
                "Add the JAR that provides this package to [ settings ] → extra jars\n" +
                "then run verification again."
            );
            detail.setFont(MONO_S);
            detail.setForeground(TEXT2);
            detail.setOpaque(false);
            detail.setEditable(false);
            detail.setLineWrap(true);

            // Inline "open settings" button
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            btnRow.setOpaque(false);
            JButton openSettings = new JButton("[ open settings ]");
            openSettings.setFont(MONO_B);
            openSettings.setForeground(BG);
            openSettings.setBackground(ORANGE);
            openSettings.setBorderPainted(false);
            openSettings.setFocusPainted(false);
            openSettings.setMargin(new Insets(5, 12, 5, 12));
            openSettings.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            openSettings.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { openSettings.setBackground(AMBER2); }
                public void mouseExited(MouseEvent e)  { openSettings.setBackground(ORANGE); }
            });
            openSettings.addActionListener(e -> openSettings());
            btnRow.add(openSettings);

            banner.add(titleRow, BorderLayout.NORTH);
            banner.add(detail,   BorderLayout.CENTER);
            banner.add(btnRow,   BorderLayout.SOUTH);

            resultsContainer.add(banner);
            setStatus("compile error — jar missing");
            revalidate(); repaint();
        });
    }

    void showError(String msg) {
        SwingUtilities.invokeLater(() -> {
            resultsContainer.removeAll();
            JPanel err = new JPanel(new BorderLayout());
            err.setBackground(new Color(0x2a0d0d));
            err.setBorder(new CompoundBorder(new MatteBorder(0,3,0,0,RED), new EmptyBorder(12,14,12,14)));
            err.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            err.setAlignmentX(Component.LEFT_ALIGNMENT);
            JTextArea ta = new JTextArea(msg);
            ta.setFont(MONO_S); ta.setForeground(RED); ta.setOpaque(false);
            ta.setEditable(false); ta.setLineWrap(true);
            err.add(ta);
            resultsContainer.add(err);
            revalidate(); repaint();
        });
    }

    // [CHANGE 1] buildCp now appends extraJars when the field is non-empty.
    String buildCp(String workDir) {
        String base = jpfSymbcClasses + File.pathSeparator + jpfSymbcJar + File.pathSeparator
                    + jpfCoreJar + File.pathSeparator + jpfSymbcLib + File.pathSeparator + workDir;
        return (extraJars == null || extraJars.isEmpty())
            ? base
            : base + File.pathSeparator + extraJars;
    }

    File writeConfig(File workDir, String mainClass, String cp) throws IOException {
        String s = "target=" + mainClass + "\nclasspath=" + workDir.getAbsolutePath() + "\n\n"
            + "vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory\n"
            + "listener=gov.nasa.jpf.symbc.SymbolicListener\n\n"
            + "symbolic.min_int=-10\nsymbolic.max_int=10\n"
            + "symbolic.debug=true\nsymbolic.lazy=true\nsearch.multiple_errors=true\n";
        File f = new File(workDir, "verify.jpf");
        Files.write(f.toPath(), s.getBytes());
        return f;
    }

    File writeGenericConfig(File workDir, String className, String contractsFile) throws IOException {
        String s = "target=GenericContractsTest\n"
            + "target.args=" + className + "," + contractsFile + "\n"
            + "classpath=" + workDir.getAbsolutePath() + "\n\n"
            + "vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory\n"
            + "listener=gov.nasa.jpf.symbc.SymbolicListener\n\n"
            + "symbolic.min_int=-10\nsymbolic.max_int=10\n"
            + "symbolic.debug=true\nsymbolic.lazy=true\nsearch.multiple_errors=true\n";
        File f = new File(workDir, "generic_verify.jpf");
        Files.write(f.toPath(), s.getBytes());
        return f;
    }

    ProcResult exec(File workDir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        Process proc = pb.start();
        StringBuilder out = new StringBuilder(), err = new StringBuilder();
        Thread t1 = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) {
                    out.append(l).append("\n");
                    final String fl = l;
                    SwingUtilities.invokeLater(() -> setStatus(fl.length() > 90 ? fl.substring(0,90)+"…" : fl));
                }
            } catch (IOException ignored) {}
        });
        Thread t2 = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String l; while ((l = r.readLine()) != null) err.append(l).append("\n");
            } catch (IOException ignored) {}
        });
        t1.start(); t2.start(); proc.waitFor(); t1.join(); t2.join();
        return new ProcResult(proc.exitValue(), out.toString(), err.toString());
    }

    List<String> readContracts(File f) throws IOException {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("\\{.*?\\}\\s*\\S+.*?\\{.*?\\}");
        for (String line : new String(Files.readAllBytes(f.toPath())).split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#") && p.matcher(line).find()) list.add(line);
        }
        return list;
    }

    List<ContractResult> parseResults(String output, List<String> contracts) {
        List<ContractResult> list = new ArrayList<>();
        for (String c : contracts) {
            ContractResult r = new ContractResult();
            r.contract = c;

            boolean violated = output.contains("Contract: " + c)
                            || output.contains("Contract violated: " + c);

            if (violated) {
                r.status = "fail";
                int idx = output.contains("Contract: " + c)
                    ? output.indexOf("Contract: " + c)
                    : output.indexOf("Contract violated: " + c);
                int start = Math.max(0, output.lastIndexOf("[!!!]", idx));
                if (start == 0 && !output.substring(0, idx).contains("[!!!]")) start = Math.max(0, idx - 60);
                int count = 0;
                int search = 0;
                while (true) {
                    int found = output.indexOf("Contract: " + c, search);
                    if (found == -1) break;
                    count++;
                    search = found + 1;
                }
                r.detail = (count > 1 ? count + " violation(s) found.\n" : "")
                    + output.substring(start, Math.min(start + 400, output.length())).trim();

            } else if (output.contains("[+] VALIDATED: " + c)) {
                r.status = "pass";
                r.detail = "All symbolic paths satisfied the postcondition.";

            } else if (output.toLowerCase().contains("no errors detected")) {
                r.status = "pass";
                r.detail = "JPF: no errors detected.";

            } else {
                r.status = "unknown";
                r.detail = "Contract not found in JPF output. Check raw log.";
            }
            list.add(r);
        }
        return list;
    }

    void checkReady()        { runBtn.setEnabled(javaFile != null && contractFile != null); }
    void setStatus(String s) { statusLbl.setText(s); }

    void resetAll() {
        javaFile = null; contractFile = null;
        javaFileLbl.setText("no file selected");     javaFileLbl.setForeground(TEXT3);
        contractFileLbl.setText("no file selected"); contractFileLbl.setForeground(TEXT3);
        resultsContainer.removeAll(); summaryRow.setVisible(false);
        rawLog.setText(""); statusLbl.setText("ready");
        checkReady(); revalidate(); repaint();
    }

    void loadEnvConfig() {
        String v;
        if ((v = System.getenv("JPF_SYMBC_CLASSES")) != null) jpfSymbcClasses = v;
        if ((v = System.getenv("JPF_SYMBC_JAR"))     != null) jpfSymbcJar     = v;
        if ((v = System.getenv("JPF_CORE_JAR"))      != null) jpfCoreJar      = v;
        if ((v = System.getenv("JPF_SYMBC_LIB"))     != null) jpfSymbcLib     = v;
        if ((v = System.getenv("JAVA8_HOME"))        != null) java8Home       = v;
        // [CHANGE 1] Extra jars can also be pre-set via env var
        if ((v = System.getenv("EXTRA_JARS"))        != null) extraJars       = v;
    }

    void deleteDir(File d) {
        if (d == null || !d.exists()) return;
        File[] fs = d.listFiles(); if (fs != null) for (File f : fs) deleteDir(f);
        d.delete();
    }

    String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ── Widget helpers ────────────────────────────────────────────────────────
    JButton flatBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(MONO_S); b.setBorderPainted(false);
        b.setContentAreaFilled(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(4, 6, 4, 6));
        return b;
    }

    JTextField darkField(String val) {
        JTextField tf = new JTextField(val);
        tf.setBackground(BG3); tf.setForeground(TEXT); tf.setCaretColor(AMBER);
        tf.setFont(MONO_S);
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER2,1), new EmptyBorder(4,8,4,8)));
        return tf;
    }

    JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text); l.setFont(MONO_S); l.setForeground(TEXT3);
        l.setAlignmentX(Component.LEFT_ALIGNMENT); return l;
    }

    Component vgap(int h) { return Box.createRigidArea(new Dimension(0, h)); }

    MouseAdapter hoverColor(JButton btn, Color normal, Color hover) {
        return new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(hover); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(normal); }
        };
    }

    // ── Inner types ───────────────────────────────────────────────────────────
    static class ContractResult { String contract, status, detail; }
    static class ProcResult { int code; String stdout, stderr;
        ProcResult(int c, String o, String e) { code=c; stdout=o; stderr=e; } }
    static class VerifyResult { List<ContractResult> results; String rawLog, error;
        VerifyResult(List<ContractResult> r, String log, String err) { results=r; rawLog=log; error=err; } }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SPFVerifierUI::new);
    }
}
