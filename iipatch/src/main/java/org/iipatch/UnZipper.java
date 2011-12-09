package org.iipatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class UnZipper {
	private String m_zfname = null;
	private boolean m_useRelFolders = false;
	private Vector<ZipEntry> m_files = null;
	private String m_extractDir = null;

	public UnZipper(String ZipFile, boolean useRelFolders, String extractTo) {
		this.m_zfname = ZipFile;
		this.m_useRelFolders = useRelFolders;
		this.m_files = new Vector<ZipEntry>();
		this.m_extractDir = (extractTo == null ? "." : extractTo);
		new File(this.m_extractDir).mkdirs();
	}

	public void extractAll() throws IOException {
		extract(null);
	}

	public void extract(FilenameFilter filter) throws IOException {
		readFiles(filter);
		extractFiles();
	}

	private void extractFiles() throws IOException {
		ZipFile z = new ZipFile(this.m_zfname);
		try {
			for (Enumeration<ZipEntry> e = this.m_files.elements(); e.hasMoreElements();) {
				ZipEntry entry = (ZipEntry) e.nextElement();
				InputStream input = z.getInputStream(entry);
				FileOutputStream output = null;
				int j = 0;
				try {
					String fname = entry.getName();

					if (!this.m_useRelFolders) {
						fname = fname.substring(fname.lastIndexOf('/') + 1);
					}

					fname = this.m_extractDir + "/" + fname;

					if (entry.isDirectory()) {
						File dir = new File(fname);
						dir.mkdirs();

						if (input != null) input.close();
						continue;
					}
					if (this.m_useRelFolders) {
						String dir = fname.substring(0, fname.lastIndexOf('/'));
						new File(dir).mkdirs();
					}

					output = new FileOutputStream(fname);
					
					byte[] buf = new byte[256000];
					j = 0;
					while (true) {
						int length = input.read(buf);
						if (length <= 0) break;
						j += length;
						output.write(buf, 0, length);
					}
				} finally {
					if (input != null) input.close();
					if (output != null) output.close();
				}
			}
		} finally {
			try { 
				z.close();
			} catch (Exception e) {
			}
		}
	}

	private void readFiles(FilenameFilter filter) throws IOException {
		if (!new File(this.m_zfname).exists()) { throw new IOException("zip file " + this.m_zfname + " does not exist"); }
		ZipFile z = null;
		try {
			z = new ZipFile(this.m_zfname);
			Enumeration<?> enm = z.entries();
			while (enm.hasMoreElements()) {
				ZipEntry e = (ZipEntry) enm.nextElement();
				if (e.isDirectory()) {
					this.m_files.addElement(e);
				} else if ((filter != null) && (filter.accept(new File("."), e.getName()))) {
					this.m_files.addElement(e);
				} else if (filter == null) this.m_files.addElement(e);
			}
		} catch (IOException ioe) {
			throw new IOException("Error adding files:" + ioe.getMessage());
		} finally {
			try {
				z.close();
			} catch (Exception e) {
			}
		}
	}

}

/*
 * Location: C:\Users\agelatos\Desktop\iipatch.jar Qualified Name:
 * org.nosymmetry.patch.UnZipper JD-Core Version: 0.6.0
 */