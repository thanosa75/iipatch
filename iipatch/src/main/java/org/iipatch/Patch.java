package org.iipatch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import SevenZip.Compression.LZMA.Decoder;
import SevenZip.Compression.LZMA.Encoder;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;

public class Patch implements Runnable {
	private static final long IIPATCH_SIG = 263172082L;
	static final String APP_NAME = "i²patch";
	static final String APP_VERSION = "v0.50";
	private static int GLOBAL_MATCHER = 2;

	long filesFound = 0L;

	long filesSizeTotal = 0L;

	private HashMap<String, Serializable> filesMap = new HashMap<String, Serializable>();
	private long fileBytesOptimised;
	PatchWindow pWindow = null;

	PatchDetails pd = null;

	File targetFile = null;

	private Map<String, Object[]> zipEntriesDiffs = new TreeMap<String, Object[]>();

	private static final CRC32 crc32 = new CRC32();

	private GDiffPatcher patcher = new GDiffPatcher();

	public Patch() {
	}

	public Patch(PatchWindow window) throws Exception {
		this.pWindow = window;
		this.targetFile = new File(window.txtTarget.getText());
		if (window.embPatchData == null) {
			File patchFile = new File(window.txtPatch.getText());
			BufferedInputStream bstr = new BufferedInputStream(new FileInputStream(patchFile));
			this.pd = readPatch(bstr);
		} else {
			this.pd = readPatch(new ByteArrayInputStream(window.embPatchData));
		}
		this.pWindow.pbar.setMaximum((int) this.pd.patchFiles);
		this.pWindow.pbar.setVisible(true);
	}

	private void applyPatch(File targetFile, PatchDetails pd) {
		try {
			long length = targetFile.length();
			if (pd.origFileSize != length) {
				System.out.println("\n\nWarning! The file supplied for patching differs in size to the original (original:"
						+ pd.origFileSize + ", current:" + length + ")");
				JOptionPane.showMessageDialog(this.pWindow,
						"Warning!\n The file supplied for patching differs in size to the original. \nPress OK to continue.");
			} else {
				System.out.print("Verifying file " + targetFile + ": Size OK");
			}

			byte[] targetMD5 = makeMD5hash(targetFile);
			if (!MessageDigest.isEqual(targetMD5, pd.origMD5)) {
				System.out.println("\n\nError! MD5 mismatch of original (" + pd.origFileName + ") vs supplied (" + targetFile
						+ ") file. Process exit.");
				JOptionPane
						.showMessageDialog(this.pWindow,
								"ERROR!\n The file supplied differs in content from the original! This patch cannot be applied.\nPress OK to exit.");
				System.exit(120);
			} else {
				System.out.println(", MD5 OK");
			}

			Map<String, Serializable> candidates = pd.patchCandidates;

			File baseDir = new File("./temp" + System.currentTimeMillis());
			baseDir.mkdir();
			doPatchOnFile(targetFile, pd, candidates, baseDir);

			String oldFname = targetFile.getName();
			String newName = oldFname.substring(0, oldFname.indexOf(".")) + ".patched."
					+ oldFname.substring(oldFname.indexOf(".") + 1);

			File patchedTarget = new File(targetFile.getParent(), newName);
			if ((patchedTarget.exists()) && (!patchedTarget.delete())) {
				handleException("Failed to delete!", new Exception("file not readable!"));
			}
			Zipper zp = new Zipper();
			zp.addFiles(baseDir.getAbsolutePath(), (FilenameFilter) null, true);
			zp.create(patchedTarget.getAbsolutePath());
			deleteAll(baseDir.getAbsolutePath());
			baseDir.delete();

			System.out.println("Done. Patched " + patchedTarget + " successfully");
			JOptionPane.showMessageDialog(this.pWindow, "Patch completed successfully, patched file is\n" + patchedTarget
					+ "\nPress OK to exit");
			System.exit(0);
		} catch (Exception e) {
			handleException("Failed to complete patching", e);
		}
	}

	private void doPatchOnFile(File targetFile, PatchDetails pd, Map<String, Serializable> candidates, File baseDir) {
		try {
			UnZipper uz = new UnZipper(targetFile.getAbsolutePath(), true, baseDir.getAbsolutePath());
			uz.extractAll();

			Iterator<String> i = candidates.keySet().iterator();
			while (i.hasNext()) {
				String file = (String) i.next();
				Object action = candidates.get(file);
				if ((action instanceof String)) {
					String strAction = (String) action;
					if (strAction.equals("-")) {
						File removed = new File(baseDir, file);
						if ((removed.exists()) && (removed.delete())) {
							System.out.println("file " + file + " removed");
						} else
							System.out.println("\nWARNING: Unable to remove file :" + file
									+ "! could this be a wrong starting archive?\n");
					} else if ((strAction.length() > 10) && (strAction.startsWith("#"))) {
						String patched = strAction.substring(1);
						copyXdeltaFromPatch(pd, baseDir, file, patched);
					} else if (strAction.length() > 10) {
						System.out.println(" action is "+strAction);
						copyFromPatch(pd, baseDir, file, strAction);
					} else {
						System.out.println("STR I dont know what to do with " + file + " = " + action);
					}
				} else if ((action instanceof Map)) {
					File bDir = new File("./tempI" + System.currentTimeMillis());
					System.out.println("now patching internal " + file);
					doPatchOnFile(new File(baseDir, file), pd, (Map<String,Serializable>)action, bDir);
					Zipper zp = new Zipper();
					zp.addFiles(bDir.getAbsolutePath(), (FilenameFilter) null, true);
					zp.setMoveFiles(true);
					zp.create(new File(baseDir, file).getAbsolutePath());
					deleteAll(bDir.getAbsolutePath());
					bDir.delete();
				} else {
					System.out.println("OBJ I dont know what to do with " + file + " = " + action);
				}
			}

			System.out.println("done patching " + targetFile.getName());
			this.pWindow.pbar.setValue(this.pWindow.pbar.getValue() + 1);
		} catch (Exception e) {
			handleException("Error unzipping file", e);
		}
	}

	private void copyXdeltaFromPatch(PatchDetails pd, File baseDir, String file, String patched) {
		try {
			Object obj =  pd.shaBytes.get(patched);
			if (obj instanceof byte[]) {
				System.out.println("detected duplicate - calling copy for "+patched);
				copyFromPatch(pd, baseDir, file, patched);
			} else {
				Object[] diff = (Object[]) obj;
				patchXdelta(new File(baseDir, file), diff);
			}
			this.pWindow.pbar.setValue(this.pWindow.pbar.getValue() + 1);
		} catch (Exception e) {
			System.out.println("trying to execute action "+patched+" the object was of type "+pd.shaBytes.get(patched).getClass()+" message :"+e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * @param pd
	 * @param baseDir
	 * @param file
	 * @param strAction
	 * @throws IOException
	 */
	private void copyFromPatch(PatchDetails pd, File baseDir, String file, String strAction) throws IOException {
		Serializable dataPack = pd.shaBytes.get(strAction);
		if (dataPack instanceof byte[]) {
			byte[] data =  (byte[]) dataPack;
			RandomAccessFile rf = null;
			try {
				File fToWrite = new File(baseDir, file);
				fToWrite.getParentFile().mkdirs();
				rf = new RandomAccessFile(fToWrite, "rw");
				rf.setLength(data.length);
				rf.seek(0L);
				rf.write(data);
				rf.close();
				System.out.println("file " + file + " (" + data.length / 1024 + "K) copied");
				this.pWindow.pbar.setValue(this.pWindow.pbar.getValue() + 1);
			} catch (Exception e) {
				handleException("Failed to write to " + file, e);
			} finally {
				if (rf != null) rf.close();
			}
		} else {
			System.out.println(strAction+" is NOT a bytepack, trying xdelta");
			copyXdeltaFromPatch(pd,baseDir,file,strAction);
		}
		
	}

	private PatchDetails readPatch(InputStream patchFileStream) throws Exception {
		DigestInputStream digestIn = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");

			digestIn = new DigestInputStream(patchFileStream, md);
			byte[] testBuf = new byte[4];
			digestIn.read(testBuf, 0, 4);
			if (getLong(testBuf, 0) != IIPATCH_SIG) {
				JOptionPane.showMessageDialog(this.pWindow, "Patch file provided is not an i²patch file.");
				handleException("Not a patch file", new Exception("failure"));
			}
			System.out.println("It is a patch file, got proper signature");
			digestIn.read(testBuf, 0, 4);
			long bytesToRead = getLong(testBuf, 0);
			testBuf = new byte[(int) bytesToRead];
			System.out.println("reading " + bytesToRead + " bytes...");
			int readBytes = digestIn.read(testBuf);
			int rightNow = readBytes;
			while (rightNow < bytesToRead) {
				readBytes = digestIn.read(testBuf, rightNow, testBuf.length - rightNow);
				rightNow += readBytes;
			}
			digestIn.on(false);
			byte[] digestComputed = md.digest();
			byte[] fileDigest = new byte[digestComputed.length];
			digestIn.read(fileDigest);

			if (!MessageDigest.isEqual(digestComputed, fileDigest)) {
				JOptionPane.showMessageDialog(this.pWindow, "Verification of file has failed. Corrupt patch file.");
				handleException("Corrupt patch file!", new Exception("failure"));
			} else {
				System.out.println("MD5 verified!");
			}
			Object o = null;
			try {
				o = objectReadFromByteArray(testBuf, true);
				System.out.println("PatchDetails: read BZIP2 compressed object");
			} catch (Exception e) {
				testBuf = decodeFromLZMA(testBuf);
				System.out.println("PatchDetails: fallback; LZMA compressed object");
				o = objectReadFromByteArray(testBuf, false);
			}
			if (!(o instanceof PatchDetails)) { throw new Exception(); }
			PatchDetails pd = (PatchDetails) o;
			System.out.println("PatchDetails: " + pd.origFileName + " files: " + pd.patchCandidates.size());
			return pd;
		} catch (Exception ex) {
			handleException("Failed to read patch from disk: ", ex, false);
			throw new Exception(ex.getMessage(), ex);
		} finally {
			if (digestIn != null) try {
				digestIn.close();
			} catch (Exception e) {
			}
			if (patchFileStream != null) try {
				patchFileStream.close();
			} catch (Exception e) {
			}
		}
	}

	private byte[] decodeFromLZMA(byte[] testBuf) throws Exception {
		byte[] properties = new byte[5];
		System.arraycopy(testBuf, 0, properties, 0, 5);

		Decoder decoder = new Decoder();
		try {
			decoder.SetDecoderProperties(properties);
			byte[] lzmaData = new byte[testBuf.length - 5];
			System.arraycopy(testBuf, 5, lzmaData, 0, lzmaData.length);
			ByteArrayInputStream inStream = new ByteArrayInputStream(lzmaData);
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			long outSize = 0L;
			byte[] lzmaLEN = new byte[4];
			inStream.read(lzmaLEN, 0, 4);
			outSize = getLong(lzmaLEN, 0);
			if (outSize == -1L) outSize = 9223372036854775807L;
			BufferedInputStream inStream0 = new BufferedInputStream(inStream);
			BufferedOutputStream outStream0 = new BufferedOutputStream(outStream);
			decoder.Code(inStream0, outStream0, outSize);
			inStream0.close();
			outStream0.close();
			return outStream.toByteArray();
		} catch (Exception e) {
			handleException("Problem decoding LZMA? maybe not an LZMA stream!", e);
		}
		return null;
	}

	public static Object objectReadFromByteArray(byte[] content, boolean useBZ2) throws Exception, IOException, ClassNotFoundException {
		ObjectInputStream ois = null;
		if (useBZ2)
			ois = new ObjectInputStream(new BZip2CompressorInputStream(new ByteArrayInputStream(content)));
		else {
			ois = new ObjectInputStream(new ByteArrayInputStream(content));
		}
		Object o = null;
		try {
			o = ois.readObject();
		} catch (Exception e) {
			throw new Exception("Invalid patch format; possibly a previous version", e);
		} finally {
			if (ois != null) ois.close();
		}
		return o;
	}

	public byte[] makeMD5hash(File file) {
		FileInputStream fin = null;
		DigestInputStream digestIn = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			fin = new FileInputStream(file);
			digestIn = new DigestInputStream(new BufferedInputStream(fin), md);

			byte[] testBuf = new byte[10485760];
			long bytesToRead = file.length();

			int readBytes = digestIn.read(testBuf);
			int rightNow = readBytes;
			while (rightNow < bytesToRead) {
				readBytes = digestIn.read(testBuf, 0, testBuf.length);
				rightNow += readBytes;
			}

			digestIn.on(false);
			byte[] digestComputed = md.digest();
			byte[] arrayOfByte1 = digestComputed;
			return arrayOfByte1;
		} catch (Exception e) {
			handleException("Failed to compute MD5: ", e);
		} finally {
			if (digestIn != null) try {
				digestIn.close();
			} catch (Exception e) {
			}
			if (fin != null) try {
				fin.close();
			} catch (Exception e) {
			}
		}
		return null;
	}

	public static void main(String[] args) {
		System.out.println("iiPatch v0.22 (20090511) - Universal ZIP/JAR/WAR/EAR patch creator/applier");
		if ((args.length == 1) || (args.length > 3)) {
			System.out.println("\tOptions: ");
			System.out.println("\t<file1> <file2> [-d<num>]");
			System.out.println("\t\tcreate 'changes.iipatch' containing all changes that, if applied ");
			System.out.println("\t\twill upgrade file1 to be the same as file2.");
			System.out.println("\t-d<num> \tmodify matcher (0,1,2 default 2) ");

			return;
		}

		Patch p = new Patch();

		if ((args.length != 0) && (args[0] != null)) {
			if ((args.length > 1) && (args.length < 4)) {
				File f1 = new File(args[0]);
				File f2 = new File(args[1]);

				if (args.length == 3) {
					if (args[2].startsWith("-d")) {
						String matcherStr = args[2].substring(2);
						try {
							int matcher = Integer.parseInt(matcherStr);
							if ((matcher > -1) && (matcher < 3)) {
								GLOBAL_MATCHER = matcher;
								System.out.println(" Using LZMA matcher level " + GLOBAL_MATCHER);
							}
						} catch (Exception e) {
						}
					}
				}
				if ((f1.exists()) && (f2.exists())) {
					File pTemp = new File("patchTemp");
					pTemp.mkdirs();
					Map<String, Serializable> trueCandidates = new HashMap<String, Serializable>();
					p.makePatch(f1, f2, trueCandidates);
					PatchDetails pd = new PatchDetails(f1.getName(), f1.length(), p.filesFound, p.filesSizeTotal);

					pd.origMD5 = p.makeMD5hash(f1);

					p.writePatchToFile(trueCandidates, pd);

					System.out.println("===================================================");

					System.out.println("Original File    : " + f1.getName() + ", " + (int) (f1.length() * 1.0D / 1024.0D) + "KB\n"
							+ "New  File        : " + f2.getName() + ", " + (int) (f2.length() * 1.0D / 1024.0D) + "KB\n"
							+ "Files changed    : " + p.filesFound + "\n" + "Size of patch    : "
							+ (int) (p.filesSizeTotal * 1.0D / 1024.0D) + "KB (optimised: "
							+ (int) (p.fileBytesOptimised * 1.0D / 1024.0D) + "KB)\n" + "Size compressed  : "
							+ (int) (pd.patchTotalSize * 1.0D / 1024.0D) + "KB\n" + "Savings (vs orig): "
							+ (int) (100.0D - 1.0D * pd.patchTotalSize / f2.length() * 100.0D) + "%");

					p.deleteAll("patchTemp");
					pTemp.delete();
				} else {
					System.out.println("error: either " + f1 + " or " + f2 + " does not exist.");
				}
			} else {
				System.out.println("error: wrong parameters ");
			}
		} else
			p.startGUI();
	}

	public long getLong(byte[] buffer, int offset) {
		long value = buffer[(offset + 3)] << 24 & 0xFF000000;
		value += (buffer[(offset + 2)] << 16 & 0xFF0000);
		value += (buffer[(offset + 1)] << 8 & 0xFF00);
		value += (buffer[offset] & 0xFF);
		return value;
	}

	public byte[] getBytes(long something) {
		byte[] result = new byte[4];
		result[0] = (byte) (int) (something & 0xFF);
		result[1] = (byte) (int) ((something & 0xFF00) >> 8);
		result[2] = (byte) (int) ((something & 0xFF0000) >> 16);
		result[3] = (byte) (int) ((something & 0xFF000000) >> 24);
		return result;
	}

	private void writePatchToFile(Map<String, Serializable> trueCandidates, PatchDetails pdet) {
		byte[] detailsData = null;
		FileOutputStream fout = null;
		DigestOutputStream digestOut = null;
		try {
			System.out.print("writing to disk : [files: " + this.filesMap.size() + ", objects: " + trueCandidates.size() + "] ");
			if (pdet.patchFiles == 0L) {
				System.out.println(". aborted! No files have been changed.");
				deleteAll("patchTemp");
				new File("patchTemp").delete();
				System.exit(1);
			}

			pdet.patchCandidates = trueCandidates;
			pdet.shaBytes = this.filesMap;

			detailsData = objectWriteToByteArray(pdet, false);

			trueCandidates = null;
			this.filesMap = null;
			pdet.patchCandidates = null;
			pdet.shaBytes = null;
			Runtime.getRuntime().gc();
			Runtime.getRuntime().gc();

			detailsData = encodeToLZMA(detailsData);
			fout = new FileOutputStream(new File("changes.iipatch"));
			MessageDigest md = MessageDigest.getInstance("MD5");
			digestOut = new DigestOutputStream(fout, md);
			System.out.print(" patch data...");
			digestOut.write(getBytes(263172082L));
			digestOut.write(getBytes(detailsData.length));
			digestOut.write(detailsData);
			digestOut.flush();
			System.out.print(" MD5 checksum...");
			digestOut.on(false);
			digestOut.write(md.digest());
			digestOut.flush();
			digestOut.close();
			System.out.print(" done, file closed.\n\n");
			pdet.patchTotalSize = detailsData.length;
		} catch (Exception e) {
			handleException("Failed to write patch to disk: ", e);
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

	private byte[] encodeToLZMA(byte[] detailsData) throws IOException {
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

	public byte[] objectWriteToByteArray(Object o, boolean useBZ2) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		if (useBZ2)
			oos = new ObjectOutputStream(new BZip2CompressorOutputStream(bout, 9));
		else
			oos = new ObjectOutputStream(bout);
		try {
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

	private void makePatch(File f1, File f2, Map<String, Serializable> trueCandidates) {
		Map<String, ZipEntry> origZipEntries = getZipEntries(f1);
		Map<String, ZipEntry> destZipEntries = getZipEntries(f2);

		Set<String> commonFiles = intersection(origZipEntries.keySet(), destZipEntries.keySet());

		Set<String> onlyInOrigFiles = difference(origZipEntries.keySet(), commonFiles);

		Set<String> onlyInDestFiles = difference(destZipEntries.keySet(), commonFiles);

		trueCandidates
				.putAll(getPatchCandidates(f1, f2, commonFiles, onlyInOrigFiles, onlyInDestFiles, origZipEntries, destZipEntries));

		handleCandidatesForArchives(trueCandidates, f1, f2);
		replaceWithBytes(f2, trueCandidates);
	}

	private void handleCandidatesForArchives(Map<String, Serializable> trueCandidates, File origArc, File destArc) {
		Map<String, Serializable> additionalCandidates = new HashMap<String, Serializable>();
		for (Iterator<String> i = trueCandidates.keySet().iterator(); i.hasNext();) {
			String key = i.next();
			if (trueCandidates.get(key).toString().compareTo("+") == 0) {
				if ((key.endsWith(".war")) || (key.endsWith(".jar")) || (key.endsWith(".ear"))
						|| (key.endsWith(".zip"))) {
					i.remove();
					HashMap<String, Serializable> internalTargets = new HashMap<String, Serializable>();
					checkInternalFile(key, origArc, destArc, internalTargets);
					if ((internalTargets.size() == 1) && (internalTargets.containsKey(key))) {
						additionalCandidates.put(key, "+");
						System.out.println("---> adding " + key + " to patch, not in orig");
					} else {
						additionalCandidates.put(key, internalTargets);
					}
				}
			}

		}

		trueCandidates.putAll(additionalCandidates);
	}

	private void replaceWithBytes(File zipFile, Map<String, Serializable> filesToReplace) {
		ZipFile zf = null;
		ByteArrayOutputStream bout = null;
		try {
			zf = new ZipFile(zipFile);
			Iterator<String> i = filesToReplace.keySet().iterator();
			while (i.hasNext()) {
				String filename = (String) i.next();
				if (filesToReplace.get(filename).toString().compareTo("+") == 0) {
					ZipEntry zentry = zf.getEntry(filename);
					if (zentry != null) {
						MessageDigest md = MessageDigest.getInstance("SHA");
						DigestInputStream din = new DigestInputStream(zf.getInputStream(zentry), md);
						byte[] tempbuf = new byte[8192];
						int dataRead = din.read(tempbuf, 0, tempbuf.length);
						bout = new ByteArrayOutputStream();
						while (dataRead != -1) {
							bout.write(tempbuf, 0, dataRead);
							dataRead = din.read(tempbuf, 0, tempbuf.length);
						}
						bout.flush();
						bout.close();
						String hashSHA = encodeToHex(md.digest());
						if (!this.filesMap.containsKey(hashSHA)) {
							this.filesMap.put(hashSHA, bout.toByteArray());
						} else {
							System.out.println("SHA-match : duplicate (" + hashSHA + ") removed for " + new File(filename).getName());
							this.fileBytesOptimised += bout.toByteArray().length;
						}

						filesToReplace.put(filename, hashSHA);
						this.filesFound += 1L;
						this.filesSizeTotal += bout.toByteArray().length;
					}
				} else if (filesToReplace.get(filename).toString().compareTo("#") == 0) {
					ZipEntry zentry = zf.getEntry(filename);
					if (zentry != null) {
						MessageDigest md = MessageDigest.getInstance("SHA");
						DigestInputStream din = new DigestInputStream(zf.getInputStream(zentry), md);
						byte[] tempbuf = new byte[8192];
						int dataRead = din.read(tempbuf, 0, tempbuf.length);
						while (dataRead != -1) {
							dataRead = din.read(tempbuf, 0, tempbuf.length);
						}
						String hashSHA = encodeToHex(md.digest());
						if (!this.filesMap.containsKey(hashSHA)) {
							this.filesMap.put(hashSHA, (Object[]) (Object[]) this.zipEntriesDiffs.get(filename));
						} else {
							System.out.println("SHA-match : duplicate (" + hashSHA + ") removed for " + new File(filename).getName());
							Object[] patchData = (Object[]) (Object[]) this.zipEntriesDiffs.get(filename);
							this.fileBytesOptimised += ((byte[]) (byte[]) patchData[1]).length;
						}

						filesToReplace.put(filename, "#" + hashSHA);
						this.filesFound += 1L;
						Object[] data = (Object[]) (Object[]) this.zipEntriesDiffs.get(filename);
						this.filesSizeTotal += ((byte[]) (byte[]) data[1]).length;
					}
				}
			}
		} catch (Exception e) {
			handleException("while trying to get patched files! ", e);
		} finally {
			try {
				if (zf != null) zf.close();
			} catch (Exception e) {
			}
		}
	}

	public static String encodeToHex(byte[] binaryData) {
		char[] hexadecimal = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
		char[] buffer = new char[64];
		for (int i = 0; i < binaryData.length; i++) {
			int low = binaryData[i] & 0xF;
			int high = (binaryData[i] & 0xF0) >> 4;
			buffer[(i * 2)] = hexadecimal[high];
			buffer[(i * 2 + 1)] = hexadecimal[low];
		}
		return new String(buffer, 0, binaryData.length * 2);
	}

	private void checkInternalFile(String fileName, File origArc, File destArc, Map<String, Serializable> candidates) {
		long tt = System.currentTimeMillis();

		File newOrig = getInternal("patchTemp/tempOrig" + tt, origArc, fileName);
		if (newOrig == null) {
			candidates.put(fileName, "+");
			return;
		}
		File newDest = getInternal("patchTemp/tempDest" + tt, destArc, fileName);

		newOrig.deleteOnExit();
		newDest.deleteOnExit();

		makePatch(newOrig, newDest, candidates);
	}

	public void deleteAll(String startDir) {
		File dir = new File(startDir);
		if (!dir.exists()) { return; }
		File[] files = dir.listFiles();
		if (files == null) { return; }
		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				deleteAll(files[i].getAbsolutePath());
			}
			boolean r = files[i].delete();
			if (!r) files[i].deleteOnExit();
		}
	}

	private File getInternal(String tempFileDirectory, File zipFile, Object fileName) {
		File tempDirO = new File(tempFileDirectory);
		if (!tempDirO.exists()) tempDirO.mkdirs();
		InputStream zipData = null;
		BufferedOutputStream bout = null;
		ZipFile ZF = null;
		try {
			File target = new File(tempDirO, fileName.toString());
			target.getParentFile().mkdirs();
			ZF = new ZipFile(zipFile);
			ZipEntry file = ZF.getEntry(fileName.toString());
			if (file == null) { return null; }
			zipData = ZF.getInputStream(file);
			byte[] tempbuf = new byte[8192];
			int dataRead = zipData.read(tempbuf, 0, tempbuf.length);
			bout = new BufferedOutputStream(new FileOutputStream(target));
			while (dataRead != -1) {
				bout.write(tempbuf, 0, dataRead);
				dataRead = zipData.read(tempbuf, 0, tempbuf.length);
			}

			return target;
		} catch (Exception e) {
			handleException("zip extraction error", e);
		} finally {
			try {
				zipData.close();
			} catch (Exception e) {
			}
			try {
				ZF.close();
			} catch (Exception e) {
			}
			try {
				bout.close();
			} catch (Exception e) {
			}
		}
		return null;
	}

	private Map<String, String> getPatchCandidates(File f1, File f2, Set<String> commonFiles, Set<String> inOrigFiles, Set<String> inDestFiles, Map<String, ZipEntry> origZE, Map<String, ZipEntry> destZE) {
		Map<String, String> zipEntriesCandidates = new HashMap<String, String>();

		if (inOrigFiles.size() > 0) for (Iterator<String> i = inOrigFiles.iterator(); i.hasNext();) {
			String me = i.next().toString();
			zipEntriesCandidates.put(me, "-");
		}

		if (inDestFiles.size() > 0) {
			for (Iterator<String> i = inDestFiles.iterator(); i.hasNext();) {
				String me = i.next().toString();
				zipEntriesCandidates.put(me, "+");
			}

		}

		Iterator<String> c = commonFiles.iterator();
		while (c.hasNext()) {
			String file = (String) c.next();
			Long crcOrig = Long.valueOf(((ZipEntry) origZE.get(file)).getCrc());
			Long crcDest = Long.valueOf(((ZipEntry) destZE.get(file)).getCrc());
			long sizeDest = ((ZipEntry) destZE.get(file)).getSize();
			if (crcOrig.longValue() != crcDest.longValue()) {
				if ((file.endsWith(".war")) || (file.endsWith(".jar")) || (file.endsWith(".ear")) || (file.endsWith(".zip"))) {
					System.out.println("internal archive :" + file);
					zipEntriesCandidates.put(file, "+");
				} else if (sizeDest > 512L) {
					PatchResult result = calculateXdelta(f1, f2, origZE, destZE, file);
					if (result.isOK) {
						this.zipEntriesDiffs.put(file, result.returnedObject);
						zipEntriesCandidates.put(file, "#");
					} else {
						zipEntriesCandidates.put(file, "+");
					}
				} else {
					zipEntriesCandidates.put(file, "+");
				}
			}

		}

		return zipEntriesCandidates;
	}

	@SuppressWarnings("unchecked")
	private Set<String> difference(Collection<String> from, Collection<String> whichItems) {
		Set<String> fromNew =   (Set<String>) new HashSet<String>(from).clone();
		Set<String> whichNew =   (Set<String>) new HashSet<String>(whichItems).clone();

		fromNew.removeAll(whichNew);

		return fromNew;
	}

	@SuppressWarnings("unchecked")
	private Set<String> intersection(Collection<String> a, Collection<String> b) {
		Set<String> aNew = (Set<String>) new HashSet<String>(a).clone();
		Set<String> bNew = (Set<String>) new HashSet<String>(b).clone();

		aNew.retainAll(bNew);
		return aNew;
	}

	private Map<String, ZipEntry> getZipEntries(File zipFile) {
		Map<String, ZipEntry> result = new HashMap<String, ZipEntry>();
		ZipFile zF = null;
		try {
			zF = new ZipFile(zipFile);

			for (Enumeration<? extends ZipEntry> zEntries = zF.entries(); zEntries.hasMoreElements();) {
				ZipEntry entry = (ZipEntry) zEntries.nextElement();
				if (!entry.isDirectory()) if ((entry.getCrc() == -1L) || (entry.getCrc() == 0L)) {
					System.out.println("warning: " + entry.getName() + " has wrong CRC, ignoring");
				} else {
					result.put(entry.getName(), entry);
				}
			}
		} catch (Exception e) { 
			handleException("zip file access error", e);
		} finally {
			try {
				if (zF != null) zF.close();
			} catch (Exception e) {
			}
		}
		return result;
	}

	private void handleException(String message, Exception e) {
		handleException(message, e, true);
	}

	private void handleException(String message, Exception e, boolean exit) {
		System.out.println(message);
		e.printStackTrace();
		if (exit) System.exit(250);
	}

	private void startGUI() {
		PatchWindow pw = new PatchWindow();
		pw.setVisible(true);
	}

	public void run() {
		applyPatch(this.targetFile, this.pd);
	}

	public static void makeZipFromDir(String ZipFilename, String dirToZip, String[] files, boolean recurseDirectory, boolean moveFiles)
			throws Exception {
		try {
			Zipper z = new Zipper();
			z.addFiles(dirToZip, files, recurseDirectory);
			z.setMoveFiles(moveFiles);
			z.create(ZipFilename);
		} catch (Exception e) {
		}
	}

	public static void makeFilesFromZipFile(String ZipFilename, FilenameFilter filter, String extractToDir, boolean useStoredFolders)
			throws Exception {
		try {
			UnZipper uz = new UnZipper(ZipFilename, useStoredFolders, extractToDir);
			uz.extract(filter);
		} catch (Exception e) {
		}
	}

	private PatchResult calculateXdelta(File origZip, File destZip, Map<String, ZipEntry> origZE, Map<String, ZipEntry> destZE, String fn) {
		try {
			Delta dCalc = new Delta();
			ZipFile zOrig = new ZipFile(origZip);
			ZipFile zDest = new ZipFile(destZip);
			int chunk = 0;
			if (fn.endsWith(".class"))
				chunk = 12;
			else {
				chunk = 32;
			}
			dCalc.setChunkSize(chunk);

			byte[] originalByteArray = readToByteArray(zOrig.getInputStream((ZipEntry) origZE.get(fn)));
			byte[] destinationByteArray = readToByteArray(zDest.getInputStream((ZipEntry) destZE.get(fn)));
			long crc = calculateCRC(destinationByteArray);
			byte[] patch = dCalc.compute(originalByteArray, destinationByteArray);
			byte[] better = null;

			boolean isOK = verifyPatch(crc, originalByteArray, destinationByteArray, patch);

			PatchResult pr = new PatchResult();
			pr.isOK = isOK;
			pr.returnedObject = new Object[] { Long.valueOf(crc), patch };
			do {
				chunk -= 2;
				dCalc.setChunkSize(chunk);
				better = dCalc.compute(originalByteArray, destinationByteArray);
				if (better.length < ((byte[]) pr.returnedObject[1]).length) {
					isOK = verifyPatch(crc, originalByteArray, destinationByteArray, better);
					if (!isOK) continue;
					pr.isOK = true;
					pr.returnedObject[1] = better;
				}
			} while (chunk > 4);

			return pr;
		} catch (Exception e) {
			handleException("failed to calculate x-delta", e, true);
		}
		return null;
	}

	private boolean verifyPatch(long destCRC, byte[] originalByteArray, byte[] destinationByteArray, byte[] patch) {
		try {
			byte[] patched = this.patcher.patch(originalByteArray, patch);
			long patchDestCRC = calculateCRC(patched);
			if (patchDestCRC == destCRC) return true;
		} catch (IOException e) {
		}
		return false;
	}

	private final long calculateCRC(byte[] originalByteArray) {
		crc32.reset();
		crc32.update(originalByteArray);

		return crc32.getValue();
	}

	private void patchXdelta(File fileToPatch, Object[] diffData) throws Patch.PatchVerificationFailedException {
		try {
			FileInputStream fis = new FileInputStream(fileToPatch);
			File newFile = new File(fileToPatch.getAbsolutePath() + ".patch");
			FileOutputStream fout = new FileOutputStream(newFile);
			System.out.print("file " + makePrintable(fileToPatch.getAbsolutePath()) + " (xd " + diffData.length / 1024 + "K)");
			byte[] patched = this.patcher.patch(readToByteArray(fis),(byte[]) diffData[1]);
			fis.close();
			long newCRC = calculateCRC(patched);
			fout.write(patched);
			fout.close();
			fileToPatch.delete();
			if ((newFile.renameTo(fileToPatch) == true) && (newCRC == ((Long) diffData[0]).longValue())) {
				System.out.println(" patched OK");
			} else {
				System.out.print(" **** FAILED **** (orig:" + (Long) diffData[0] + "/now:" + newCRC + ")");
				throw new PatchVerificationFailedException("CRC does not match the original");
			}
		} catch (PatchVerificationFailedException pe) {
			throw pe;
		} catch (Exception e) {
			e.printStackTrace();
			throw new PatchVerificationFailedException(e.getMessage());
		}
	}

	private String makePrintable(String path) {
		String newStr = path.replace('\\', '/');
		newStr = newStr.substring(newStr.indexOf("./temp") + 5);

		newStr = newStr.substring(newStr.indexOf('/') + 1);
		return newStr;
	}

	private byte[] readToByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buff = new byte[16384];
		int read = is.read(buff, 0, buff.length);
		while (read > 0) {
			bout.write(buff, 0, read);
			read = is.read(buff, 0, buff.length);
		}
		is.close();
		return bout.toByteArray();
	}

	class PatchVerificationFailedException extends IOException {
		private static final long serialVersionUID = 1L;

		public PatchVerificationFailedException(String string) {
			super(string);
		}
	}

	private class PatchResult {
		Object[] returnedObject;
		boolean isOK;
	}
}

/*
 * Location: C:\Users\agelatos\Desktop\iipatch.jar Qualified Name:
 * org.nosymmetry.patch.Patch JD-Core Version: 0.6.0
 */