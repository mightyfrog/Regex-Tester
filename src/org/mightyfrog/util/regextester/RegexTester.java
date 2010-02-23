package org.mightyfrog.util.regextester;

import java.awt.dnd.InvalidDnDOperationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 *
 * @author Shigehiro Soejima
 */
public class RegexTester extends JFrame implements DocumentListener {
    //
    private final UndoManager UNDO_MANAGER = new UndoManager();

    //
    private final JSplitPane SPLIT_PANE =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    //
    private final JLabel REGEX_LABEL =
        new JLabel(I18N.get("label.1"));
    private final TextArea REGEX_TA = new TextArea() {
            {
                setLineWrap(true);
                getDocument().addUndoableEditListener(new UndoableEditListener() {
                        /** */
                        @Override
                        public void undoableEditHappened(UndoableEditEvent evt) {
                            UNDO_MANAGER.addEdit(evt.getEdit());
                        }
                    });
                InputMap im = getInputMap();
                im.put(KeyStroke.getKeyStroke("ctrl pressed Z"),
                       new AbstractAction() {
                           /** */
                           @Override
                           public void actionPerformed(ActionEvent evt) {
                               try {
                                   UNDO_MANAGER.undo();
                               } catch (CannotUndoException e) {
                                   //
                               }
                           }
                       });
                im.put(KeyStroke.getKeyStroke("shift ctrl pressed Z"),
                       new AbstractAction() {
                           /** */
                           @Override
                           public void actionPerformed(ActionEvent evt) {
                               try {
                                   UNDO_MANAGER.redo();
                               } catch (CannotRedoException e) {
                                   //
                               }
                           }
                       });
                // regular expression is in one line so ENTER has no use
                // i.e. no line break in regex - remapped to evaluating regex
                im.put(KeyStroke.getKeyStroke("pressed ENTER"),
                       new AbstractAction() {
                           /** */
                           @Override
                           public void actionPerformed(ActionEvent evt) {
                               EVAL_BUTTON.doClick();
                           }
                       });
            }
        };
    //
    private final JButton EVAL_BUTTON = new JButton(I18N.get("label.0"));
    private final JButton CLEAR_BUTTON = new JButton(I18N.get("label.2"));

    //
    private final TextArea TA = new TextArea() {
            {
                TransferHandler tf = getTransferHandler();
                setTransferHandler(new TextTransferHandler(tf));
            }
        };

    //
    private final JTextField STATUS_FIELD =
        new JTextField(I18N.get("status.1", getCharset())) {
            {
                setEditable(false);
                setFocusable(false);
                setBorder(null);
            }
        };

    //
    private List<Integer> START_LIST = new ArrayList<Integer>();
    private List<Integer> END_LIST = new ArrayList<Integer>();

    //
    private JMenu fileMenu = null;
    private JMenuItem openMI = null;
    private JMenuItem exitMI = null;

    //
    private JMenu optionMenu = null;
    private JCheckBoxMenuItem evalAsTypeMI = null;
    private JCheckBoxMenuItem wordwrapMI = null;
    private JCheckBoxMenuItem evalOnlyMI = null;
    private JMenuItem charsetMI = null;
    private JMenu localeMenu = null;

    private JMenu helpMenu = null;
    private JMenuItem wikiMI = null;
    private JMenuItem aboutMI = null;

    //
    private JLabel CHARSET_LABEL = new JLabel(I18N.get("dialog.1"));
    private JComboBox CHARSET_CMBBOX = new JComboBox();
    private String selectedCharset = null;
    private boolean evalOnly = false;

    /**
     *
     */
    public RegexTester() {
        setLocale(I18N.getLocale());
        setTitle(I18N.get("frame.title"));
        setIconImage(new ImageIcon(RegexTester.class.
                                   getResource("icon.png")).getImage());
        initCharsetComobBox();
        setJMenuBar(createMenuBar());
        getRootPane().setDefaultButton(EVAL_BUTTON);

        EVAL_BUTTON.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    evaluateOnEDT();
                }
            });
        CLEAR_BUTTON.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    removeHighlight();
                }
            });

        TA.getActionMap().put("scrollForward", new AbstractAction() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    if (!TA.hasFocus()) {
                        TA.requestFocus();
                    }
                    scrollHighlightToVisible(true);
                }
            });
        TA.getActionMap().put("scrollBackward", new AbstractAction() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    if (!TA.hasFocus()) {
                        TA.requestFocus();
                    }
                    scrollHighlightToVisible(false);
                }
            });
        KeyStroke f3 = KeyStroke.getKeyStroke("pressed F3");
        TA.getInputMap(JTextArea.WHEN_IN_FOCUSED_WINDOW).put(f3, "scrollForward");
        TA.getInputMap().put(f3, "scrollForward");
        KeyStroke shiftF3 = KeyStroke.getKeyStroke("shift pressed F3");
        TA.getInputMap(JTextArea.WHEN_IN_FOCUSED_WINDOW).put(shiftF3, "scrollBackward");
        TA.getInputMap().put(shiftF3, "scrollBackward");

        REGEX_LABEL.setLabelFor(REGEX_TA);
        REGEX_LABEL.setDisplayedMnemonic('E');
        REGEX_TA.getDocument().addDocumentListener(this);

        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        SPLIT_PANE.setDividerLocation(75);
        SPLIT_PANE.setTopComponent(createNorthPanel());
        SPLIT_PANE.setBottomComponent(createCenterPanel());
        add(SPLIT_PANE);
        add(createSouthPanel(), BorderLayout.SOUTH);

        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    /** */
    @Override
    public void changedUpdate(DocumentEvent evt) {
        // won't happen
    }

    /** */
    @Override
    public void insertUpdate(DocumentEvent evt) {
        if (this.evalAsTypeMI.isSelected()) {
            evaluateOnEDT();
        }
    }

    /** */
    @Override
    public void removeUpdate(DocumentEvent evt) {
        if (this.evalAsTypeMI.isSelected()) {
            evaluateOnEDT();
        }
    }

    /**
     *
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // ClassNotFoundException, InstantiationException,
            // IllegalAccessException
            // javax.swing.UnsupportedLookAndFeelException
        }

        EventQueue.invokeLater(new Runnable() {
                /** */
                @Override
                public void run() {
                    new RegexTester();
                }
            });
    }

    //
    //
    //

    /**
     * Invokes evaluate() on an event dispatching thread.
     *
     */
    void evaluateOnEDT() {
        EventQueue.invokeLater(new Runnable() {
                /** */
                @Override
                public void run() {
                    evaluate();
                }
            });
    }

    /**
     * Evaluates each line as one string.
     *
     */
    void evaluate() {
        START_LIST.clear();
        END_LIST.clear();

        updateStatusBarCharset();

        if (REGEX_TA.getText().isEmpty()) {
            removeHighlight();
            return;
        }

        removeHighlight();

        long startTime = System.currentTimeMillis();
        Pattern pattern = null;
        try {
            toggleCursor(true);
            pattern = Pattern.compile(REGEX_TA.getText());
            int matchCount = 0;
            int offset = 0;
            String[] lines = TA.getText().split("\n");
            for (String line : lines) {
                Matcher m = pattern.matcher(line);
                while (m.find()) {
                    int start = m.start() + offset;
                    int end = m.end() + offset;;
                    if (start != end) {
                        matchCount++;
                        if (!isEvalOnly()) {
                            END_LIST.add(end - start);
                            START_LIST.add(start);
                            highlight(start, end);
                        }
                    }
                }
                offset += line.length() + 1;
            }
            STATUS_FIELD.setText(I18N.get("status.0", matchCount,
                                          System.currentTimeMillis() - startTime));
        } catch (PatternSyntaxException e) {
            removeHighlight();
            setErrorStatus(e.getMessage());
        } catch (Throwable t) {
            System.exit(-1);
        } finally {
            toggleCursor(false);
        }
    }

    /**
     *
     * @param msg
     */
    void setErrorStatus(String msg) {
        STATUS_FIELD.setForeground(Color.RED);
        STATUS_FIELD.setText(msg);
        STATUS_FIELD.setCaretPosition(0);
    }

    /**
     *
     * @param start
     * @param end
     */
    void highlight(int start, int end) {
        try {
            Highlighter highlighter = TA.getHighlighter();
            DefaultHighlighter.DefaultHighlightPainter hp =
                new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
            highlighter.addHighlight(start, end, hp);
        } catch(BadLocationException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    void removeHighlight() {
        Highlighter highlighter = TA.getHighlighter();
        Highlighter.Highlight[] highlighters = highlighter.getHighlights();
        for (Highlighter.Highlight h : highlighters) {
            if (h.getPainter() instanceof
                DefaultHighlighter.DefaultHighlightPainter) {
                highlighter.removeHighlight(h);
            }
        }
    }

    /**
     *
     */
    void scrollHighlightToVisible(boolean forward) {
        if (START_LIST.size() == 0) {
            return;
        }
        try { // TODO: rewrite me
            int offset = 0; // match start
            int caretPos = 0; // match end
            if (forward) {
                offset = START_LIST.remove(0);
                START_LIST.add(offset);
                caretPos = END_LIST.remove(0);
                END_LIST.add(caretPos);
                if (caretPos == TA.getCaretPosition()) {
                    offset = START_LIST.remove(0);
                    START_LIST.add(offset);
                    caretPos = END_LIST.remove(0);
                    END_LIST.add(caretPos);
                }
            } else {
                int index = START_LIST.size() - 1;
                offset = START_LIST.remove(index);
                START_LIST.add(0, offset);
                caretPos = END_LIST.remove(index);
                END_LIST.add(0, caretPos);
                if (caretPos == TA.getCaretPosition()) {
                    offset = START_LIST.remove(index);
                    START_LIST.add(0, offset);
                    caretPos = END_LIST.remove(index);
                    END_LIST.add(0, caretPos);
                }
            }

            TA.scrollRectToVisible(TA.modelToView(offset));
            TA.setCaretPosition(offset + caretPos);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param waiting
     */
    void toggleCursor(boolean waiting) {
        Component gp = getGlassPane();
        if (waiting) {
            gp.addMouseListener(new MouseAdapter() {
                    /** */
                    @Override
                    public void mousePressed(MouseEvent evt) {
                        evt.consume();
                    }
                });
            gp.setVisible(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } else {
            MouseListener[] ml = gp.getMouseListeners();
            for (MouseListener l : ml) {
                gp.removeMouseListener(l);
            }
            gp.setVisible(false);
            setCursor(null);
        }
    }

    //
    //
    //

    /**
     *
     */
    private void initCharsetComobBox() {
        SortedMap<String, Charset> map = Charset.availableCharsets();
        for (String key : map.keySet()) {
            CHARSET_CMBBOX.addItem(key);
        }
    }

    /**
     *
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createFileMenu());
        menuBar.add(createOptionMenu());
        menuBar.add(createHelpMenu());

        return menuBar;
    }

    /**
     *
     * @param evalOnly
     */
    private void setEvalOnly(boolean evalOnly) {
        this.evalOnly = evalOnly;
    }

    /**
     *
     */
    private boolean isEvalOnly() {
        return this.evalOnly;
    }

    /**
     *
     */
    private JMenu createFileMenu() {
        this.fileMenu = new JMenu(I18N.get("menu.2"));
        this.openMI = new JMenuItem(I18N.get("menuitem.7"));
        this.openMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    JFileChooser fc = new JFileChooser(new File("."));
                    fc.showOpenDialog(RegexTester.this);
                    File f = fc.getSelectedFile();
                    if (f != null && f.isFile()) {
                        try {
                            load(new BufferedReader(new FileReader(f)));
                        } catch (IOException e) {
                        }
                    }
                }
            });
        this.exitMI = new JMenuItem(I18N.get("menuitem.8"));
        this.exitMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    System.exit(0);
                }
            });

        this.fileMenu.add(this.openMI);
        this.fileMenu.addSeparator();
        this.fileMenu.add(this.exitMI);

        return this.fileMenu;
    }

    /**
     *
     */
    private JMenu createOptionMenu() {
        this.optionMenu = new JMenu(I18N.get("menu.0"));
        this.evalAsTypeMI = new JCheckBoxMenuItem(I18N.get("menuitem.0"));
        this.wordwrapMI = new JCheckBoxMenuItem(I18N.get("menuitem.1"));
        this.charsetMI = new JMenuItem(I18N.get("menuitem.5"));
        this.evalOnlyMI = new JCheckBoxMenuItem(I18N.get("menuitem.6"));
        this.wordwrapMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    TA.setLineWrap(RegexTester.this.wordwrapMI.isSelected());
                }
            });
        this.charsetMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    CHARSET_CMBBOX.setSelectedItem("" + getCharset());
                    //JOptionPane.showMessageDialog(RegexTester.this,
                    //                              CHARSET_CMBBOX,
                    //                              "Select Charset",
                    //                              JOptionPane.QUESTION_MESSAGE);
                    showCharsetDialog();
                    RegexTester.this.selectedCharset =
                        (String) CHARSET_CMBBOX.getSelectedItem();
                    updateStatusBarCharset();
                }
            });
        this.evalOnlyMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    setEvalOnly(RegexTester.this.evalOnlyMI.isSelected());
                }
            });
        this.optionMenu.add(this.evalAsTypeMI);
        this.optionMenu.add(this.wordwrapMI);
        this.optionMenu.add(this.evalOnlyMI);
        this.optionMenu.addSeparator();
        this.optionMenu.add(this.charsetMI);

        this.optionMenu.setMnemonic('O');
        this.evalAsTypeMI.setMnemonic('E');
        this.wordwrapMI.setMnemonic('W');

        return this.optionMenu;
    }

    /**
     *
     */
    private JMenu createHelpMenu() {
        this.helpMenu = new JMenu(I18N.get("menu.1"));
        this.wikiMI = new JMenuItem(I18N.get("menuitem.3"));
        this.aboutMI = new JMenuItem(I18N.get("menuitem.2"));

        this.helpMenu.add(this.wikiMI);
        this.helpMenu.addSeparator();
        this.helpMenu.add(this.aboutMI);


        this.wikiMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    try {
                        URL url = new URL(I18N.get("wiki.url"));
                        Desktop.getDesktop().browse(url.toURI());
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        // MalformedURLException
                        e.printStackTrace();
                    }
                }
            });

        this.aboutMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    showAboutDialog();
                }
            });

        return helpMenu;
    }

    /**
     *
     */
    private JPanel createNorthPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 3, 3 ,3);
        gbc.gridwidth = 3;
        gbc.weightx = 0.0;

        panel.add(REGEX_LABEL, gbc);
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridheight = 3;
        panel.add(new JScrollPane(REGEX_TA), gbc);
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridheight = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(EVAL_BUTTON, gbc);
        panel.add(CLEAR_BUTTON, gbc);
        panel.add(Box.createVerticalBox(), gbc);

        return panel;
    }

    /**
     *
     */
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(new JScrollPane(TA));

        return panel;
    }

    /**
     *
     */
    private JPanel createSouthPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEtchedBorder());

        panel.add(STATUS_FIELD);

        return panel;
    }

    /**
     *
     */
    private void showAboutDialog() {
        String version = I18N.get("dialog.0",
                                  "Shigehiro Soejima",
                                  "mightyfrog.gc@gmail.com",
                                  "@TIMESTAMP@");
        JOptionPane.showMessageDialog(this, version);
    }

    /**
     *
     * @param in
     * @throws java.io.IOException
     */
    private void load(Reader in) throws IOException {
        StringWriter out = null;
        try {
            out = new StringWriter();
            int n = 0;
            char[] cbuf = new char[1024];
            while ((n = in.read(cbuf)) != -1) {
                out.write(cbuf, 0, n);
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
        try {
            TA.setText(out.toString());
            TA.setCaretPosition(0);
            updateStatusBarCharset();
        } catch (Throwable t) {
            setErrorStatus(I18N.get("status.2"));
        }
    }

    /**
     *
     */
    private void updateStatusBarCharset() {
        updateStatusBarCharset(UIManager.getColor("Label.foreground"));
    }

    /**
     *
     * @param color
     */
    private void updateStatusBarCharset(Color color) {
        STATUS_FIELD.setForeground(color);
        STATUS_FIELD.setText(I18N.get("status.1", getCharset()));
    }

    //
    //
    //

    /**
     *
     */
    private Charset getCharset() {
        Charset cs = null;
        if (this.selectedCharset == null) {
            cs = Charset.defaultCharset();
        } else {
            cs = Charset.availableCharsets().get(this.selectedCharset);
        }

        return cs;
    }


    /**
     *
     */
    private String showCharsetDialog() {
        int option =
            JOptionPane.showOptionDialog(this,
                                         createLocalePanel(),
                                         I18N.get("dialog.title.0"),
                                         JOptionPane.OK_CANCEL_OPTION,
                                         JOptionPane.QUESTION_MESSAGE,
                                         null, null, null);
        if (option == JOptionPane.OK_OPTION) {
            return (String) CHARSET_CMBBOX.getSelectedItem();
        }

        return null;
    }

    /**
     *
     */
    private JPanel createLocalePanel() {
        JPanel panel = new JPanel() {
                /** */
                @Override
                public void addNotify() {
                    super.addNotify();

                    new Thread(new Runnable() { // hack
                            /** */
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                }
                                CHARSET_CMBBOX.requestFocusInWindow();
                            }
                        }).start();
                }
            };
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        panel.add(CHARSET_LABEL, gbc);
        panel.add(CHARSET_CMBBOX, gbc);

        return panel;
    }

    /**
     *
     */
    private class TextTransferHandler extends TransferHandler {
        //
        private TransferHandler defaultHandler = null;

        /**
         *
         */
        public TextTransferHandler(TransferHandler defaultHandler) {
            this.defaultHandler = defaultHandler;
        }

        /** */
        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            Transferable t = support.getTransferable();
            try {
                List<?> list = null;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    list = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                } else {
                    String data =
                        (String) t.getTransferData(createURIListFlavor());
                    list = textURIListToFileList(data);
                }
                for (Object obj : list) {
                    File file = (File) obj;
                    if (file.isFile()) {
                        return true;
                    }
                }
                return false;
            } catch (InvalidDnDOperationException e) {
                // ignore
            } catch (UnsupportedFlavorException e) {
                return false;
            } catch (IOException e) {
                return false;
            }

            return super.canImport(support);
        }

        /** */
        @Override
        public boolean canImport(JComponent comp, DataFlavor[] flavors) {
            return true;
        }

        /** */
        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip,
                                      int action) {
            this.defaultHandler.exportToClipboard(comp, clip, action);
        }

        /** */
        @Override
        public boolean importData(JComponent comp, Transferable t) {
            try {
                List<?> list = null;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    list = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                } else {
                    String data =
                        (String) t.getTransferData(createURIListFlavor());
                    list = textURIListToFileList(data);
                }
                for (int i = 0; i < list.size(); i++) {
                    File file = (File) list.get(i);
                    load(new BufferedReader(new FileReader(file)));
                }
            } catch (UnsupportedFlavorException e) {
                this.defaultHandler.importData(comp, t);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }

    /**
     *
     */
    private static DataFlavor createURIListFlavor() {
        DataFlavor df = null;
        try {
            df = new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            // shouldn't happen
        }

        return df;
    }

    /**
     *
     * @param uriList
     */
    private static List<File> textURIListToFileList(String uriList) {
        List<File> list = new ArrayList<File>(1);
        StringTokenizer st = new StringTokenizer(uriList, "\r\n");
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s.startsWith("#")) { // the line is a comment (as per the RFC 2483)
                continue;
            }
            try {
                URI uri = new URI(s);
                File file = new File(uri);
                if (file.length() != 0) {
                    list.add(file);
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        return list;
    }
}
