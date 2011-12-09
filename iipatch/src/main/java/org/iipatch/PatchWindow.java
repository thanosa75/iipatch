package org.iipatch;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

class PatchWindow extends JFrame {
	private static final long serialVersionUID = 1832053811297161133L;
	JTextField txtTarget = new JTextField(30);
	JTextField txtPatch = new JTextField(30);
	JProgressBar pbar = new JProgressBar();
	Vector<JComponent> toDisable = new Vector<JComponent>();
	byte[] embPatchData = null;

	public PatchWindow() {
		super("i²patch v0.22 (20090511)");
		getContentPane().setLayout(new BorderLayout());
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				JOptionPane.showMessageDialog(e.getWindow(), "Press OK to exit", "i²patch exiting", 0);

				System.exit(100);
			}

		});
		ActionListener targetListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				PatchWindow.this.browseForTarget(e);
			}

		};
		ActionListener patchListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				PatchWindow.this.browseForChanges(e);
			}

		};
		ActionListener doPatchListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				PatchWindow.this.doPatch(e);
			}

		};
		JPanel fileSelect = new JPanel(new FlowLayout());
		addToPane(fileSelect, this.txtTarget, "File to patch:", "Browse...", targetListener);
		JPanel patchSelect = new JPanel(new FlowLayout());
		JButton browsePatch = addToPane(patchSelect, this.txtPatch, "Patch (provided by II):", "Browse...", patchListener);

		JPanel gatherer = new JPanel(new BorderLayout());
		gatherer.add(fileSelect, "North");
		gatherer.add(patchSelect, "South");
		getContentPane().add(gatherer, "North");

		JButton patch = new JButton("Patch!");
		patch.addActionListener(doPatchListener);
		patch.setPreferredSize(new Dimension(80, 30));
		this.toDisable.add(patch);
		JPanel pPane = new JPanel();
		pPane.add(patch);
		getContentPane().add(pPane, "Center");

		this.pbar.setPreferredSize(new Dimension(500, 20));
		this.pbar.setVisible(false);
		this.pbar.setStringPainted(true);
		getContentPane().add(this.pbar, "South");

		checkEmbeddedPatch(browsePatch, this.txtPatch);
		setSize(new Dimension(600, 200));
		setVisible(true);
	}

	private void checkEmbeddedPatch(JButton browse, JTextField textField) {
		URL u = getClass().getResource("/changes.iipatch");
		if (u != null) {
			ByteArrayOutputStream bout = new ByteArrayOutputStream(16384);
			byte[] temp = new byte[16384];
			InputStream is = null;
			try {
				is = new BufferedInputStream(u.openStream());
				int r = is.read(temp, 0, temp.length);
				while (r > -1) {
					bout.write(temp, 0, r);
					r = is.read(temp, 0, temp.length);
				}
				byte[] patchData = bout.toByteArray();
				if ((patchData != null) && (patchData.length > 0)) {
					browse.setEnabled(false);
					textField.setEnabled(false);
					textField.setText("<disabled, found embedded patch data>");
					this.embPatchData = patchData;
				}
			} catch (IOException e) {
				JOptionPane.showMessageDialog(null, "Embedded patch file is not readable. Press OK to continue", "i²patch error", 0);

				e.printStackTrace();
			} finally {
				try {
					bout.close();
				} catch (IOException e1) {
				}
				if (is != null) try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private JButton addToPane(JPanel p, JTextField txt, String label, String button, ActionListener listener) {
		JLabel lbl = new JLabel(label);
		lbl.setPreferredSize(new Dimension(140, 30));
		lbl.setMinimumSize(new Dimension(140, 30));
		JButton btn = new JButton(button);
		btn.addActionListener(listener);
		p.add(lbl);
		p.add(txt);
		p.add(btn);
		this.toDisable.add(txt);
		this.toDisable.add(btn);
		return btn;
	}

	private void doPatch(ActionEvent evt) {
		if ((this.txtTarget.getText() == null) || (this.txtTarget.getText().length() < 1)) {
			JOptionPane.showMessageDialog((Component) evt.getSource(), "Please select a file to patch.", "i²patch error", 0);

			return;
		}
		if ((this.embPatchData == null) && ((this.txtPatch.getText() == null) || (this.txtPatch.getText().length() < 1))) {
			JOptionPane.showMessageDialog((Component) evt.getSource(), "Please select a file containing the Patch delivered.",
					"i²patch error", 0);

			return;
		}

		File target = new File(this.txtTarget.getText());
		File patch = null;
		if (this.embPatchData == null) {
			patch = new File(this.txtPatch.getText());
		}
		if (this.embPatchData == null) {
			if ((target.exists()) && (target.isFile()) && (patch.exists()) && (patch.isFile())) {
				System.out.println("OK");
			} else {
				JOptionPane.showMessageDialog((Component) evt.getSource(), "Either " + target + " or " + patch + " does not exist.",
						"i²patch error", 0);

				return;
			}
		} else if ((target.exists()) && (target.isFile())) {
			System.out.println("OK");
		} else {
			JOptionPane.showMessageDialog((Component) evt.getSource(), "Either " + target + " or " + patch + " does not exist.",
					"i²patch error", 0);

			return;
		}

		((Component) evt.getSource()).setEnabled(false);
		for (Iterator<JComponent> i = this.toDisable.iterator(); i.hasNext();) {
			Component comp = (Component) i.next();
			comp.setEnabled(false);
		}
		try {
			Thread tr = new Thread(new Patch(this));
			tr.start();
		} catch (Exception e) {
			JOptionPane
					.showMessageDialog(this, "ERROR!\nWhile reading the patch file, the following error occured:\n " + e.getMessage()
							+ "\n\nPress OK to exit.");

			System.exit(130);
		}
	}

	private void browseForChanges(ActionEvent evt) {
		JFileChooser fc = new JFileChooser(new File("."));
		fc.addChoosableFileFilter(new FileFilter() {
			public boolean accept(File file) {
				String filename = file.getName();
				if (file.isDirectory()) return true;
				return filename.endsWith(".iipatch");
			}

			public String getDescription() {
				return "i²patch files";
			}

		});
		fc.showOpenDialog(SwingUtilities.getWindowAncestor(this));
		File selFile = fc.getSelectedFile();
		if (selFile != null) this.txtPatch.setText(selFile.getAbsolutePath());
	}

	private void browseForTarget(ActionEvent evt) {
		JFileChooser fc = new JFileChooser(new File("."));
		fc.addChoosableFileFilter(new FileFilter() {
			public boolean accept(File file) {
				String filename = file.getName();
				if (file.isDirectory()) return true;
				return (filename.endsWith(".ear")) || (filename.endsWith(".war")) || (filename.endsWith(".zip"))
						|| (filename.endsWith(".jar"));
			}

			public String getDescription() {
				return "Archives (*.ear, *.war, *.jar, *.zip)";
			}

		});
		fc.showOpenDialog(SwingUtilities.getWindowAncestor(this));
		File selFile = fc.getSelectedFile();
		if (selFile != null) this.txtTarget.setText(selFile.getAbsolutePath());
	}

}

/*
 * Location: C:\Users\agelatos\Desktop\iipatch.jar Qualified Name:
 * org.nosymmetry.patch.PatchWindow JD-Core Version: 0.6.0
 */