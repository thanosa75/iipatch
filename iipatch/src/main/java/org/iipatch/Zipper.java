package org.iipatch;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class Zipper {
	private Vector<String> m_fileNames;
	private String m_directory;
	private boolean m_moveFiles;
	public static int DEFAULT_METHOD = 8;

	public Zipper() {
		this.m_fileNames = new Vector<String>();
		this.m_moveFiles = false;
	}

	public void setMoveFiles(boolean deleteFilesWhenZipping) {
		this.m_moveFiles = deleteFilesWhenZipping;
	}

	public void addFiles(String directory, FilenameFilter filter, boolean recurseOn) throws IOException {
		File dir = new File(directory);
		String[] files = null;
		if (filter == null)
			files = dir.list();
		else {
			files = dir.list(filter);
		}
		if (this.m_directory == null) {
			this.m_directory = directory.replace('\\', '/');
		}

		for (int i = 0; i < files.length; i++) {
			File tempf = new File(directory + File.separator + files[i]);

			if (tempf.exists()) if (!tempf.isDirectory()) {
				this.m_fileNames.addElement(tempf.toString());
			} else {
				if (!recurseOn) continue;
				addFiles(tempf.toString(), filter, true);
			}
		}
	}

	public void addFiles(String directory, String[] files, boolean recurseOn) throws IOException {
		new File(directory);

		if (this.m_directory == null) {
			this.m_directory = directory.replace('\\', '/');
		}

		for (int i = 0; i < files.length; i++) {
			File tempf = new File(directory + File.separator + files[i]);

			if (tempf.exists()) if (!tempf.isDirectory()) {
				this.m_fileNames.addElement(tempf.toString());
			} else {
				if (!recurseOn) continue;
				addFiles(tempf.toString(), (FilenameFilter) null, true);
			}
		}
	}

	public void create(String zip_file_name) throws IOException {
		if (this.m_fileNames.size() == 0) {
			System.out.println("Zipper: No files to add to ZIP:" + zip_file_name);
		} else {
			File zip_file = new File(zip_file_name);
			zip_file.getCanonicalPath();
			ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip_file), 65536));
			for (int i = 0; i < this.m_fileNames.size(); i++) {
				add(z, (String) this.m_fileNames.elementAt(i));
			}
			z.close();
		}
	}

	protected void add(ZipOutputStream z, String file_name) throws IOException {
		String entry_name = new String(file_name.replace('\\', '/'));

		if (entry_name.indexOf(this.m_directory) != -1) {
			entry_name = entry_name.substring(entry_name.indexOf(this.m_directory) + this.m_directory.length());
		}

		if (entry_name.indexOf(':') != -1) {
			entry_name = entry_name.substring(entry_name.indexOf(':') + 1);
		}

		if (entry_name.charAt(0) == '/') {
			entry_name = entry_name.substring(1);
		}
		ZipEntry entry = new ZipEntry(entry_name);

		entry.setMethod(DEFAULT_METHOD);
		if (DEFAULT_METHOD == 0) {
			entry.setSize(new File(file_name).length());
			entry.setCompressedSize(new File(file_name).length());
			entry.setCrc(getCrcForEntry(file_name));
		} else if (DEFAULT_METHOD == 8) {
			z.setLevel(9);
		}
		z.putNextEntry(entry);
		InputStream f = new FileInputStream(file_name);

		byte[] buf = new byte[10240];
		for (;;) {
			int len = f.read(buf);
			if (len < 0) break;
			z.write(buf, 0, len);
		}
		z.closeEntry();
		f.close();
		if (this.m_moveFiles) {
			File fDeleted = new File(file_name);
			fDeleted.delete();
		}
	}

	private long getCrcForEntry(String file_name) throws IOException {
		FileInputStream fileinputstream = null;
		CRC32 crc32 = new CRC32();
		byte[] rgb = new byte[65536];
		int n = 0;
		try {
			fileinputstream = new FileInputStream(file_name);
			while ((n = fileinputstream.read(rgb)) > -1) {
				crc32.update(rgb, 0, n);
			}
		} finally {
			if (fileinputstream != null) fileinputstream.close();
		}
		return crc32.getValue();
	}

}

/*
 * Location: C:\Users\agelatos\Desktop\iipatch.jar Qualified Name:
 * org.nosymmetry.patch.Zipper JD-Core Version: 0.6.0
 */