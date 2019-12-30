package filemanager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Container;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.EventQueue;
import java.awt.Insets;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.table.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.SwingWorker.*;

import javax.imageio.ImageIO;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

import java.net.URL;

class FileTableModel extends AbstractTableModel {

    private File[] files;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private String[] columns = { "Icon", "File", "Path/Name", "Size", "Last Modified", "R", "W", "E", "D", "F", };

    FileTableModel() {
        this(new File[0]);
    }

    FileTableModel(File[] files) {
        this.files = files;
    }

    public Object getValueAt(int row, int column) {
        File file = files[row];
        switch (column) {
        case 0:
            return fileSystemView.getSystemIcon(file);
        case 1:
            return fileSystemView.getSystemDisplayName(file);
        case 2:
            return file.getPath();
        case 3:
            return file.length();
        case 4:
            return file.lastModified();
        case 5:
            return file.canRead();
        case 6:
            return file.canWrite();
        case 7:
            return file.canExecute();
        case 8:
            return file.isDirectory();
        case 9:
            return file.isFile();
        default:
            System.err.println("Error");
        }
        return "";
    }

    public int getColumnCount() {
        return columns.length;
    }

    public Class<?> getColumnClass(int column) {
        switch (column) {
        case 0:
            return ImageIcon.class;
        case 3:
            return Long.class;
        case 4:
            return Date.class;
        case 5:
        case 6:
        case 7:
        case 8:
        case 9:
            return Boolean.class;
        }
        return String.class;
    }

    public String getColumnName(int column) {
        return columns[column];
    }

    public int getRowCount() {
        return files.length;
    }

    public File getFile(int row) {
        return files[row];
    }

    public void setFiles(File[] files) {
        this.files = files;
        fireTableDataChanged();
    }
}

class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private FileSystemView fileSystemView;

    private JLabel label;

    FileTreeCellRenderer() {
        label = new JLabel();
        label.setOpaque(true);
        fileSystemView = FileSystemView.getFileSystemView();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        File file = (File) node.getUserObject();
        label.setIcon(fileSystemView.getSystemIcon(file));
        label.setText(fileSystemView.getSystemDisplayName(file));
        label.setToolTipText(file.getPath());

        if (selected) {
            label.setBackground(backgroundSelectionColor);
            label.setForeground(textSelectionColor);
        } else {
            label.setBackground(backgroundNonSelectionColor);
            label.setForeground(textNonSelectionColor);
        }

        return label;
    }
}

public class FileManager {
    public static final String APP_TITLE = "File Manager";
    private Desktop desktop;
    private FileSystemView fileSystemView;

    private File currentFile;
    private File copyFileCtn;

    private JPanel gui;
    private JTree tree;
    private DefaultTreeModel treeModel;

    private JTable table;
    private JProgressBar progressBar;
    private FileTableModel fileTableModel;
    private ListSelectionListener listSelectionListener;
    private boolean cellSizesSet = false;
    private int rowIconPadding = 6;

    private JButton openFile;
    private JButton deleteFile;
    private JButton newFile;
    private JButton copyFile;
    private JButton pasteFile;
    private JButton zipFile;
    private JButton unzipFile;
    private JButton cancelBtn;

    private JLabel fileName;
    private JTextField path;
    private JLabel date;
    private JLabel size;
    private JCheckBox readable;
    private JCheckBox writable;
    private JCheckBox executable;
    private JRadioButton isDirectory;
    private JRadioButton isFile;

    private JPanel newFilePanel;
    private JRadioButton newTypeFile;
    private JTextField name;

    public Container getGui() {
        if (gui == null) {
            gui = new JPanel(new BorderLayout(3, 3));
            gui.setBorder(new EmptyBorder(5, 5, 5, 5));

            fileSystemView = FileSystemView.getFileSystemView();
            desktop = Desktop.getDesktop();

            JPanel detailView = new JPanel(new BorderLayout(3, 3));

            table = new JTable();
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setAutoCreateRowSorter(true);
            table.setShowVerticalLines(false);

            listSelectionListener = new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent lse) {
                    int row = table.getSelectionModel().getLeadSelectionIndex();
                    setFileDetails(((FileTableModel) table.getModel()).getFile(row));
                }
            };
            table.getSelectionModel().addListSelectionListener(listSelectionListener);
            JScrollPane tableScroll = new JScrollPane(table);
            Dimension d = tableScroll.getPreferredSize();
            tableScroll.setPreferredSize(new Dimension((int) d.getWidth(), (int) d.getHeight() / 2));
            detailView.add(tableScroll, BorderLayout.CENTER);

            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            treeModel = new DefaultTreeModel(root);

            TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
                public void valueChanged(TreeSelectionEvent tse) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tse.getPath().getLastPathComponent();
                    showChildren(node);
                    setFileDetails((File) node.getUserObject());
                }
            };

            File[] roots = fileSystemView.getRoots();
            for (File fileSystemRoot : roots) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(fileSystemRoot);
                root.add(node);

                File[] files = fileSystemView.getFiles(fileSystemRoot, true);
                for (File file : files) {
                    if (file.isDirectory()) {
                        node.add(new DefaultMutableTreeNode(file));
                    }
                }
            }

            tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.addTreeSelectionListener(treeSelectionListener);
            tree.setCellRenderer(new FileTreeCellRenderer());
            tree.expandRow(0);
            JScrollPane treeScroll = new JScrollPane(tree);

            tree.setVisibleRowCount(15);

            Dimension preferredSize = treeScroll.getPreferredSize();
            Dimension widePreferred = new Dimension(200, (int) preferredSize.getHeight());
            treeScroll.setPreferredSize(widePreferred);

            JPanel fileMainDetails = new JPanel(new BorderLayout(4, 2));
            fileMainDetails.setBorder(new EmptyBorder(0, 6, 0, 6));

            JPanel fileDetailsLabels = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsLabels, BorderLayout.WEST);

            JPanel fileDetailsValues = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsValues, BorderLayout.CENTER);

            fileDetailsLabels.add(new JLabel("File", JLabel.TRAILING));
            fileName = new JLabel();
            fileDetailsValues.add(fileName);
            fileDetailsLabels.add(new JLabel("Path/name", JLabel.TRAILING));
            path = new JTextField(5);
            path.setEditable(false);
            fileDetailsValues.add(path);
            fileDetailsLabels.add(new JLabel("Last Modified", JLabel.TRAILING));
            date = new JLabel();
            fileDetailsValues.add(date);
            fileDetailsLabels.add(new JLabel("File size", JLabel.TRAILING));
            size = new JLabel();
            fileDetailsValues.add(size);
            fileDetailsLabels.add(new JLabel("Type", JLabel.TRAILING));

            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
            isDirectory = new JRadioButton("Directory");
            isDirectory.setEnabled(false);
            flags.add(isDirectory);

            isFile = new JRadioButton("File");
            isFile.setEnabled(false);
            flags.add(isFile);
            fileDetailsValues.add(flags);

            int count = fileDetailsLabels.getComponentCount();
            for (int i = 0; i < count; i++) {
                fileDetailsLabels.getComponent(i).setEnabled(false);
            }

            JToolBar toolBar = new JToolBar();
            toolBar.setFloatable(false);

            openFile = new JButton("Open");
            openFile.setMnemonic('o');

            openFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    try {
                        desktop.open(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                }
            });
            toolBar.add(openFile);

            openFile.setEnabled(desktop.isSupported(Desktop.Action.OPEN));

            toolBar.addSeparator();

            newFile = new JButton("New");
            newFile.setMnemonic('n');
            newFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    newFile();
                }
            });
            toolBar.add(newFile);

            copyFile = new JButton("Copy");
            copyFile.setMnemonic('c');
            copyFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    if (currentFile != null) {
                        if (!currentFile.isDirectory())
                            copyFileCtn = currentFile;
                        else
                            showErrorMessage("Can not copy directory. Please choose file to copy", "Choose a file.");
                    } else
                        showErrorMessage("Choose file to copy.", "Didn't choose a file.");
                }
            });
            toolBar.add(copyFile);

            pasteFile = new JButton("Paste");
            pasteFile.setMnemonic('p');
            pasteFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    if (copyFileCtn != null)
                        pasteFile();
                    else
                        showErrorMessage("Choose file to paste.", "Didn't choose a file.");
                }
            });
            toolBar.add(pasteFile);

            JButton renameFile = new JButton("Rename");
            renameFile.setMnemonic('r');
            renameFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    renameFile();
                }
            });
            toolBar.add(renameFile);

            deleteFile = new JButton("Delete");
            deleteFile.setMnemonic('d');
            deleteFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    deleteFile();
                }
            });
            toolBar.add(deleteFile);

            zipFile = new JButton("Zip");
            zipFile.setMnemonic('d');
            zipFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    zipFile();
                }
            });
            toolBar.add(zipFile);

            unzipFile = new JButton("Unzip");
            unzipFile.setMnemonic('d');
            unzipFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    unzipFile();
                }
            });
            toolBar.add(unzipFile);

            toolBar.addSeparator();

            readable = new JCheckBox("Read  ");
            readable.setMnemonic('a');
            toolBar.add(readable);

            writable = new JCheckBox("Write  ");
            writable.setMnemonic('w');
            toolBar.add(writable);

            executable = new JCheckBox("Execute");
            executable.setMnemonic('x');
            toolBar.add(executable);

            JPanel fileView = new JPanel(new BorderLayout(3, 3));

            fileView.add(toolBar, BorderLayout.NORTH);
            fileView.add(fileMainDetails, BorderLayout.CENTER);

            detailView.add(fileView, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, detailView);
            gui.add(splitPane, BorderLayout.CENTER);

            JPanel output = new JPanel(new BorderLayout(3, 3));
            progressBar = new JProgressBar();
            output.add(progressBar, BorderLayout.EAST);
            progressBar.setVisible(false);

            gui.add(output, BorderLayout.SOUTH);

        }
        return gui;
    }

    public void showRootFile() {
        tree.setSelectionInterval(0, 0);
    }

    private TreePath findTreePath(File find) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath treePath = tree.getPathForRow(i);
            Object object = treePath.getLastPathComponent();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) object;
            File nodeFile = (File) node.getUserObject();
            if (nodeFile.compareTo(find) == 0) {
                return treePath;
            }
        }
        return null;
    }

    private void renameFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected to rename.", "Select File");
            return;
        }

        String renameTo = JOptionPane.showInputDialog(gui, "New Name");
        if (renameTo != null) {
            try {
                TreePath currentPath = null;
                DefaultMutableTreeNode currentNode = null;
                boolean directory = currentFile.isDirectory();
                if (directory) {
                    currentPath = findTreePath(currentFile);
                    currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
                }
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                File renameFile = new File(currentFile.getParentFile(), renameTo);

                boolean renamed = currentFile.renameTo(renameFile);
                if (renamed) {
                    if (directory) {
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(renameFile);
                        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
                        treeModel.removeNodeFromParent(currentNode);
                    }
                    currentFile = renameFile;
                    setFileDetails(currentFile);
                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + currentFile + "' could not be renamed.";
                    showErrorMessage(msg, "Rename Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

    private void deleteFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected for deleting.", "Select File");
            return;
        }

        int result = JOptionPane.showConfirmDialog(gui, "You want to delete this file?", "Delete File",
                JOptionPane.ERROR_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            progressBar.setVisible(true);
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    new DeleteWorker().execute();
                }
            });

        }
        gui.repaint();
    }

    private void newFile() {
        if (currentFile == null) {
            showErrorMessage("No location selected for new file.", "Select Location");
            return;
        }

        if (newFilePanel == null) {
            newFilePanel = new JPanel(new BorderLayout(3, 3));

            JPanel southRadio = new JPanel(new GridLayout(1, 0, 2, 2));
            newTypeFile = new JRadioButton("File", true);
            JRadioButton newTypeDirectory = new JRadioButton("Directory");
            ButtonGroup bg = new ButtonGroup();
            bg.add(newTypeFile);
            bg.add(newTypeDirectory);
            southRadio.add(newTypeFile);
            southRadio.add(newTypeDirectory);

            name = new JTextField(15);

            newFilePanel.add(new JLabel("Name"), BorderLayout.WEST);
            newFilePanel.add(name);
            newFilePanel.add(southRadio, BorderLayout.SOUTH);
        }

        int result = JOptionPane.showConfirmDialog(gui, newFilePanel, "Create File", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                boolean created;
                File parentFile = currentFile;
                if (!parentFile.isDirectory()) {
                    parentFile = parentFile.getParentFile();
                }
                File file = new File(parentFile, name.getText());
                if (newTypeFile.isSelected()) {
                    created = file.createNewFile();
                } else {
                    created = file.mkdir();
                }
                if (created) {
                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                    if (file.isDirectory()) {
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(file);
                        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
                    }
                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + file + "' could not be created.";
                    showErrorMessage(msg, "Create Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

    private void showErrorMessage(String errorMessage, String errorTitle) {
        JOptionPane.showMessageDialog(gui, errorMessage, errorTitle, JOptionPane.ERROR_MESSAGE);
    }

    private void showThrowable(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(gui, t.toString(), t.getMessage(), JOptionPane.ERROR_MESSAGE);
        gui.repaint();
    }

    private void populateFilesList(File dir, ArrayList<String> filesListInDir) throws IOException {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile())
                filesListInDir.add(file.getAbsolutePath());
            else
                populateFilesList(file, filesListInDir);
        }
    }

    class ZipWorker extends SwingWorker<Boolean, Void> {
        private File parentFile;
        private String zipName;

        public ZipWorker() {
            parentFile = currentFile;
            if (!parentFile.isDirectory()) {
                parentFile = parentFile.getParentFile();
            }
            zipName = currentFile.getPath();

            addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    progressBar.setValue(getProgress());
                }
            });
        }

        @Override

        protected void done() {
            try {
                boolean created = get();
                if (created) {
                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + currentFile + "' could not be zipped.";
                    showErrorMessage(msg, "Zipped Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
            progressBar.setVisible(false);
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            if (!currentFile.isDirectory()) {
                zipName = zipName.substring(0, zipName.lastIndexOf("."));
                zipName += ".zip";
                long zipping = 0;
                try {
                    FileOutputStream fos = new FileOutputStream(zipName);
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    ZipEntry ze = new ZipEntry(currentFile.getName());
                    zos.putNextEntry(ze);
                    FileInputStream fis = new FileInputStream(currentFile);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zipping += 1024;
                        int progress = (int) Math.round(((double) zipping / (double) currentFile.length()) * 90);
                        if (progress < 0)
                            progress = 0;
                        setProgress(progress);
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                    zos.close();
                    fis.close();
                    fos.close();
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                zipName += ".zip";
                long zipping = 0;
                try {
                    ArrayList<String> filesListInDir = new ArrayList<String>();
                    populateFilesList(currentFile, filesListInDir);
                    FileOutputStream fos = new FileOutputStream(zipName);
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    for (String filePath : filesListInDir) {
                        ZipEntry ze = new ZipEntry(
                                filePath.substring(currentFile.getAbsolutePath().length() + 1, filePath.length()));
                        zos.putNextEntry(ze);
                        FileInputStream fis = new FileInputStream(filePath);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zipping += 1024;
                            int progress = (int) Math.round(((double) zipping / (double) currentFile.length()) * 90);
                            if (progress < 0)
                                progress = 0;
                            setProgress(progress);
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                        fis.close();
                    }
                    zos.close();
                    fos.close();
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
    }

    private void zipFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected to zip.", "Select File");
            return;
        }
        progressBar.setVisible(true);
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ZipWorker().execute();
            }
        });
        gui.repaint();
    }

    class UnzipWorker extends SwingWorker<Boolean, Void> {
        private File parentFile;

        public UnzipWorker() {
            parentFile = currentFile;
            if (!parentFile.isDirectory()) {
                parentFile = parentFile.getParentFile();
            }

            addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    progressBar.setValue(getProgress());
                }
            });
        }

        @Override

        protected void done() {
            try {
                boolean unzipped = get();
                if (unzipped) {
                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + currentFile + "' could not be unzipped.";
                    showErrorMessage(msg, "Unzipped Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
            progressBar.setVisible(false);
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            String zipFilePath = currentFile.getPath();
            String destDir = parentFile.getPath();
            File dir = new File(destDir);
            if (!dir.exists())
                dir.mkdirs();
            FileInputStream fis;
            byte[] buffer = new byte[1024];
            long writing = 0;
            try {
                fis = new FileInputStream(zipFilePath);
                ZipInputStream zis = new ZipInputStream(fis);
                ZipEntry ze = zis.getNextEntry();
                while (ze != null) {
                    String fileName = ze.getName();
                    File newFile = new File(destDir + File.separator + fileName);
                    new File(newFile.getParent()).mkdirs();
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        writing += 1024;
                        int progress = (int) Math.round(((double) writing / (double) currentFile.length()) * 40);
                        if (progress < 0)
                            progress = 0;
                        setProgress(progress);
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    zis.closeEntry();
                    ze = zis.getNextEntry();
                }
                zis.closeEntry();
                zis.close();
                fis.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private void unzipFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected to zip.", "Select File");
            return;
        }

        if (!currentFile.getPath().contains(".zip")) {
            showErrorMessage("The file selected is not a zip file.", "Select File Again");
            return;
        }

        progressBar.setVisible(true);
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new UnzipWorker().execute();
            }
        });

        gui.repaint();
    }

    private void setTableData(final File[] files) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (fileTableModel == null) {
                    fileTableModel = new FileTableModel();
                    table.setModel(fileTableModel);
                }
                table.getSelectionModel().removeListSelectionListener(listSelectionListener);
                fileTableModel.setFiles(files);
                table.getSelectionModel().addListSelectionListener(listSelectionListener);
                if (!cellSizesSet) {
                    Icon icon = fileSystemView.getSystemIcon(files[0]);

                    table.setRowHeight(icon.getIconHeight() + rowIconPadding);

                    setColumnWidth(0, -1);
                    setColumnWidth(3, 60);
                    table.getColumnModel().getColumn(3).setMaxWidth(120);
                    setColumnWidth(4, -1);
                    setColumnWidth(5, -1);
                    setColumnWidth(6, -1);
                    setColumnWidth(7, -1);
                    setColumnWidth(8, -1);
                    setColumnWidth(9, -1);

                    cellSizesSet = true;
                }
            }
        });
    }

    private void setColumnWidth(int column, int width) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (width < 0) {
            JLabel label = new JLabel((String) tableColumn.getHeaderValue());
            Dimension preferred = label.getPreferredSize();
            width = (int) preferred.getWidth() + 14;
        }
        tableColumn.setPreferredWidth(width);
        tableColumn.setMaxWidth(width);
        tableColumn.setMinWidth(width);
    }

    private void showChildren(final DefaultMutableTreeNode node) {
        tree.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, File> worker = new SwingWorker<Void, File>() {
            @Override
            public Void doInBackground() {
                File file = (File) node.getUserObject();
                if (file.isDirectory()) {
                    File[] files = fileSystemView.getFiles(file, true); // !!
                    if (node.isLeaf()) {
                        for (File child : files) {
                            if (child.isDirectory()) {
                                publish(child);
                            }
                        }
                    }
                    setTableData(files);
                }
                return null;
            }

            @Override
            protected void process(List<File> chunks) {
                for (File child : chunks) {
                    node.add(new DefaultMutableTreeNode(child));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                tree.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void setFileDetails(File file) {
        currentFile = file;
        Icon icon = fileSystemView.getSystemIcon(file);
        fileName.setIcon(icon);
        fileName.setText(fileSystemView.getSystemDisplayName(file));
        path.setText(file.getPath());
        date.setText(new Date(file.lastModified()).toString());
        size.setText(file.length() + " bytes");
        readable.setSelected(file.canRead());
        writable.setSelected(file.canWrite());
        executable.setSelected(file.canExecute());
        isDirectory.setSelected(file.isDirectory());

        isFile.setSelected(file.isFile());

        JFrame f = (JFrame) gui.getTopLevelAncestor();
        if (f != null) {
            f.setTitle(APP_TITLE + " : " + fileSystemView.getSystemDisplayName(file));
        }

        gui.repaint();
    }

    class CopyFileVisitor extends SimpleFileVisitor<Path> {
        private final Path targetPath;
        private Path sourcePath = null;

        public CopyFileVisitor(Path targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir;
            } else {
                Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
            return FileVisitResult.CONTINUE;
        }
    }

    class DeleteWorker extends SwingWorker<Void, Void> {
        public DeleteWorker() {
            addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    progressBar.setValue(getProgress());
                }
            });
        }

        @Override

        protected void done() {
            progressBar.setVisible(false);
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                TreePath currentPath = null;
                DefaultMutableTreeNode currentNode = null;
                boolean directory = currentFile.isDirectory();
                if (directory) {
                    currentPath = findTreePath(currentFile);
                    currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
                }
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                boolean deleted = false;
                if (directory) {
                    try {
                        Files.walkFileTree(currentFile.toPath(), new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                if (exc == null) {
                                    Files.delete(dir);
                                    return FileVisitResult.CONTINUE;
                                } else {
                                    throw exc;
                                }
                            }
                        });

                        deleted = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                        deleted = false;
                    }
                } else {
                    deleted = currentFile.delete();
                }
                if (deleted) {
                    if (directory) {
                        treeModel.removeNodeFromParent(currentNode);
                    }
                    showChildren(parentNode);
                    int index = table.getSelectionModel().getLeadSelectionIndex();
                    if (index > 0) {
                        table.getSelectionModel().setLeadSelectionIndex(index - 1);
                    } else {
                        table.getSelectionModel().clearSelection();
                        currentFile = null;
                    }
                } else {
                    String msg = "The file '" + currentFile + "' could not be deleted.";
                    showErrorMessage(msg, "Delete Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
            return null;
        }
    }

    class PasteWorker extends SwingWorker<Void, Void> {
        public PasteWorker() {
            addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    progressBar.setValue(getProgress());
                }
            });
        }

        @Override

        protected void done() {
            progressBar.setVisible(false);
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                File parentFile = currentFile;
                if (!parentFile.isDirectory()) {
                    parentFile = parentFile.getParentFile();
                }
                String name = copyFileCtn.getName();
                String newName = copyFileCtn.getName();
                File file = null;
                do {
                    if (name.contains(".")) {
                        newName = name.substring(0, name.lastIndexOf("."));
                        newName += "_Copy";
                        newName += name.substring(name.lastIndexOf("."), name.length());
                    } else
                        newName += "_Copy";
                    name = newName;
                    file = new File(parentFile, newName);
                } while (file.exists());
                boolean created = false;
                boolean copied = false;
                if (copyFileCtn.isDirectory()) {
                    try {
                        created = file.mkdir();
                        if (created)
                            Files.walkFileTree(copyFileCtn.toPath(), new CopyFileVisitor(file.toPath()));
                    } catch (IOException e) {
                        e.printStackTrace();
                        copied = false;
                    }
                } else {
                    created = file.createNewFile();
                    file.setReadable(copyFileCtn.canRead());
                    file.setWritable(copyFileCtn.canWrite());
                    file.setExecutable(copyFileCtn.canExecute());
                    if (created) {
                        try {
                            FileInputStream fis = new FileInputStream(copyFileCtn);
                            FileOutputStream fos = new FileOutputStream(file);

                            byte[] buffer = new byte[1024];
                            int length;
                            long writing = 0;

                            while ((length = fis.read(buffer)) > 0) {
                                writing += 1024;
                                int progress = (int) Math
                                        .round(((double) writing / (double) currentFile.length()) * 100);
                                if (progress < 0)
                                    progress = 0;
                                setProgress(progress);
                                fos.write(buffer, 0, length);
                            }
                            fis.close();
                            fos.close();
                            copied = true;
                        } catch (IOException error) {
                            copied = false;
                        }
                    }
                }
                if (created && copied) {
                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                    if (file.isDirectory()) {
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(file);
                        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
                    }
                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + copyFileCtn + "' could not be copied.";
                    showErrorMessage(msg, "Copy Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
            return null;
        }
    }

    private void pasteFile() {
        if (currentFile == null) {
            showErrorMessage("No location selected for new file.", "Select Location");
            return;
        }

        if (copyFileCtn == null) {
            showErrorMessage("No file to copy.", "Select File");
            return;
        }

        progressBar.setVisible(true);
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PasteWorker().execute();
            }
        });
        gui.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception error) {
                }
                JFrame f = new JFrame(APP_TITLE);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                FileManager fileManager = new FileManager();
                f.setContentPane(fileManager.getGui());

                try {
                    URL urlBig = fileManager.getClass().getResource("fm-icon-32x32.png");
                    URL urlSmall = fileManager.getClass().getResource("fm-icon-16x16.png");
                    ArrayList<Image> images = new ArrayList<Image>();
                    images.add(ImageIO.read(urlBig));
                    images.add(ImageIO.read(urlSmall));
                    f.setIconImages(images);
                } catch (Exception weTried) {
                }

                f.pack();
                f.setLocationByPlatform(true);
                f.setMinimumSize(f.getSize());
                f.setVisible(true);

                fileManager.showRootFile();
            }
        });
    }
}
