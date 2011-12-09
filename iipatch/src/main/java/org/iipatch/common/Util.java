package org.iipatch.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.iipatch.maker.PatchCreationException;

import SevenZip.Compression.LZMA.Encoder;

public class Util {

	/**
	 * Checks to see if the file given exists and is readable. 
	 * Returns the full path.
	 * @param file the file to check
	 * @return the file full path
	 * @throws PatchException 
	 */
	public static String verifyFileExists(String file) throws PatchException {
		File f = new File(file);
		
		if (f.isFile() && f.exists() && f.canRead() && f.canWrite() ) {
			return f.getAbsolutePath();
		}
		
		throw new PatchException("File "+file+" is not available (cannot read/write or not a file.)");
	}

	public static Map<String, ZipEntry> getZipEntries(String zipFile) throws PatchCreationException {
		Map<String, ZipEntry> result = new HashMap<String, ZipEntry>();
		ZipFile zF = null;
		try {
			zF = new ZipFile(zipFile);

			for (Enumeration<? extends ZipEntry> zEntries = zF.entries(); zEntries.hasMoreElements();) {
				ZipEntry entry = (ZipEntry) zEntries.nextElement();
				if (!entry.isDirectory()) if ((entry.getCrc() == -1L) || (entry.getCrc() == 0L)) {
					Log.info("warning: " + entry.getName() + " has wrong CRC or is a directory, ignoring");
				} else {
					result.put(entry.getName(), entry);
				}
			}
		} catch (Exception e) { 
			throw new PatchCreationException("Problem getting zip entries",e);
		} finally {
			try {
				if (zF != null) zF.close();
			} catch (Exception e) {
			}
		}
		return result;
	}
	
	

	@SuppressWarnings("unchecked")
	public static Set<String> difference(Collection<String> from, Collection<String> whichItems) {
		Set<String> fromNew =   (Set<String>) new HashSet<String>(from).clone();
		Set<String> whichNew =   (Set<String>) new HashSet<String>(whichItems).clone();

		fromNew.removeAll(whichNew);

		return fromNew;
	}

	@SuppressWarnings("unchecked")
	public static  Set<String> intersection(Collection<String> a, Collection<String> b) {
		Set<String> aNew = (Set<String>) new HashSet<String>(a).clone();
		Set<String> bNew = (Set<String>) new HashSet<String>(b).clone();

		aNew.retainAll(bNew);
		return aNew;
	}

	public static InputStream getInputStream(String zipFile, String file) throws PatchCreationException {
		ZipFile zF = null;
		try {
			zF = new ZipFile(zipFile);
			ZipEntry entry = zF.getEntry(file);
			InputStream inputStream = zF.getInputStream(entry);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			byte[] buf = new byte[32*1024];
			int read = 0;
			while ((read = inputStream.read(buf)) != -1) {
				bout.write(buf,0,read);
			}
			
			return new ByteArrayInputStream(bout.toByteArray());
		} catch (Exception e) { 
			throw new PatchCreationException("Problem getting input stream for "+file+" from "+zipFile,e);
		} finally {
			try {
				if (zF != null) zF.close();
			} catch (Exception e) {
			}
		}

	}
	
	public static byte[] getInputBytes(String zipFile, String file) throws PatchCreationException {
		ZipFile zF = null;
		try {
			zF = new ZipFile(zipFile);
			ZipEntry entry = zF.getEntry(file);
			InputStream inputStream = zF.getInputStream(entry);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			byte[] buf = new byte[32*1024];
			int read = 0;
			while ((read = inputStream.read(buf)) != -1) {
				bout.write(buf,0,read);
			}
			
			return bout.toByteArray();
		} catch (Exception e) { 
			throw new PatchCreationException("Problem getting input stream for "+file+" from "+zipFile,e);
		} finally {
			try {
				if (zF != null) zF.close();
			} catch (Exception e) {
			}
		}

	}
	
	public static void deleteAll(String startDir) {
		File dir = new File(startDir);
		if (!dir.exists()) { return; }
		File[] files = dir.listFiles();
		if (files == null) { return; }
		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				deleteAll(files[i].getAbsolutePath());
			}
			boolean r = files[i].delete();
			if (!r) Log.error("failed to delete "+files[i], null);
		}
	}

	
	private static byte[] getBytes(long something) {
		byte[] result = new byte[4];
		result[0] = (byte) (int) (something & 0xFF);
		result[1] = (byte) (int) ((something & 0xFF00) >> 8);
		result[2] = (byte) (int) ((something & 0xFF0000) >> 16);
		result[3] = (byte) (int) ((something & 0xFF000000) >> 24);
		return result;
	}

	public static void writePatchToFile(PatchData data) {
		byte[] detailsData = null;
		FileOutputStream fout = null;
		DigestOutputStream digestOut = null;
		try {
			System.out.print("writing to disk... ");
			
			detailsData = encodeToLZMA(objectWriteToByteArray(data));
			fout = new FileOutputStream(new File("changes.paz"));
			MessageDigest md = MessageDigest.getInstance("SHA");
			digestOut = new DigestOutputStream(fout, md);
			System.out.print(" patch data...");
			digestOut.write(getBytes(org.iipatch.common.Constants.SIG_V1));
			digestOut.write(getBytes(detailsData.length));
			digestOut.write(detailsData);
			digestOut.flush();
			System.out.print(" SHA checksum...");
			digestOut.on(false);
			digestOut.write(md.digest());
			digestOut.flush();
			digestOut.close();
			System.out.print(" done, file closed.\n\n");
		} catch (Exception e) {
			Log.error("Failed to write patch to disk: ", e);
		} finally {
			if (digestOut != null) try {
				digestOut.close();
			} catch (Exception e) {
			}
			if (fout != null) try {
				fout.close();
			} catch (Exception e) {
			}
		}
	}

	private static byte[] encodeToLZMA(byte[] detailsData) throws IOException {
		Encoder lzma = new Encoder();
		ByteArrayOutputStream lzOut = new ByteArrayOutputStream();
		lzma.SetEndMarkerMode(true);
		lzma.WriteCoderProperties(lzOut);
		lzOut.write(getBytes(detailsData.length));
		lzma.Code(new ByteArrayInputStream(detailsData), lzOut, 0, 0, new ProgressLZMA(detailsData.length));
		lzOut.close();
		detailsData = lzOut.toByteArray();
		return detailsData;
	}

	private static byte[] objectWriteToByteArray(Object o) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(bout);
			oos.writeObject(o);
			oos.flush();
		} finally {
			oos.close();
			bout.close();
		}
		byte[] content = bout.toByteArray();
		System.out.print(" [true size: " + content.length / 1024 + "KB] ");
		return content;
	}
	
	
	
}
