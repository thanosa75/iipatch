package org.iipatch.maker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import org.iipatch.common.Cleaner;
import org.iipatch.common.Log;
import org.iipatch.common.PatchAction;
import org.iipatch.common.PatchData;
import org.iipatch.common.Util;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;

public class PatchMaker {

	private String fromFile = null;
	private String toFile = null;
	
	private PatchData completePatch = null;
	
	public PatchMaker(String[] args) throws PatchCreationException {
		if (args.length != 3) {
			throw new PatchCreationException("wrong number of arguments");
		}
		try {
			fromFile = Util.verifyFileExists(args[1]);
			toFile = Util.verifyFileExists(args[2]);
		} catch(Exception e) {
			throw new PatchCreationException("failure to configure", e);
		}
	}

	public void calculateDifferences() throws PatchCreationException {
		completePatch = calculateBetweenFiles(fromFile, toFile, null);
		Log.info("Completed patch is :"+completePatch);
	}

	private File createWorkspace() {
		File tempWorkspace = new File("tempWorkspace"+System.currentTimeMillis());
		tempWorkspace.mkdirs();
		Cleaner.registerForDeletion(tempWorkspace);
		return tempWorkspace;
	}

	private PatchData calculateBetweenFiles(String from, String to, String parent) throws PatchCreationException {
		
		File f = new File(from);
		
		Map<String, ZipEntry> fromEntries = Util.getZipEntries(from);
		Map<String, ZipEntry> toEntries = Util.getZipEntries(to);

		Set<String> commonFiles = Util.intersection(fromEntries.keySet(), toEntries.keySet());

		Set<String> onlyInOrigFiles = Util.difference(fromEntries.keySet(), commonFiles);

		Set<String> onlyInDestFiles = Util.difference(toEntries.keySet(), commonFiles);

		PatchData data = new PatchData(parent == null ? f.getName() : parent+"@"+f.getName());
		// files only in destination, copy
		addToPatch(PatchAction.COPY, onlyInDestFiles, to, data);
		// files only in source, delete
		addToPatch(PatchAction.DELETE, onlyInOrigFiles, from, data);
		// files common - copy or copyxdelta
		calculateDeltaPatch( fromEntries, toEntries, commonFiles, data, from, to, parent);
		
		return data;
	}

	private void calculateDeltaPatch(Map<String, ZipEntry> fromEntries,
			Map<String, ZipEntry> toEntries, Set<String> commonFiles,
			PatchData data, String fromFile, String toFile, String parent) throws PatchCreationException {
		for(String commonFile : commonFiles) {
			ZipEntry from = fromEntries.get(commonFile);
			ZipEntry to = toEntries.get(commonFile);
			
			if (from.getCrc() != to.getCrc() && !from.isDirectory()) {
				if(from.getName().endsWith("war") ||
						from.getName().endsWith("jar") ||
						from.getName().endsWith("ear") ||
						from.getName().endsWith("zip")
						) { //recurse to calculate a patch
					PatchData inner = calculateBetweenFiles(extract(from, fromFile), extract(to, toFile), parent);
					data.addInnerPatch(from.getName(), inner);
				} else {
					byte[] patch = calculateDeltaPatchSingle(from, fromFile, to, toFile);
					if (patch != null) {
						data.addDeltaPatch(from, patch);
					} else { // fail? just copy
						data.addFileForCopy(from.getName(), Util.getInputStream(fromFile,from.getName()));
					}
				}
				
			}
		}
		
		
	}

	private byte[] calculateDeltaPatchSingle(ZipEntry from, String fromFile,
			ZipEntry to, String toFile) throws PatchCreationException {
		Delta dCalc = new Delta();
		int chunkSize = 32;
		if (from.getName().endsWith("java") || 
				from.getName().endsWith("txt") ||
				from.getName().endsWith("html") ||
				from.getName().endsWith("jsp") ||
				from.getName().endsWith("js") ) { // text files
			chunkSize = 16;
		}

		byte[] fromBytes = Util.getInputBytes(fromFile, from.getName());
		byte[] toBytes = Util.getInputBytes(toFile, to.getName());
		byte[] candidate = null;
		try {
			boolean foundBetter;
			do {
				foundBetter = false;
				dCalc.setChunkSize(chunkSize);
				byte[] xDelta = dCalc.compute(fromBytes, toBytes);
				//verify the patch here
				if (verifyPatch(fromBytes, toBytes, xDelta)) {
					if (candidate!=null) {
						if (candidate.length > xDelta.length) {
							candidate = xDelta; //replace
							foundBetter = true;
						}
					} else {
						candidate = xDelta;
					}
				}
				chunkSize -= 4;
				//Log.info("delta is "+candidate.length+" trying with chunksize "+chunkSize);
				dCalc.setChunkSize(chunkSize);
			} while ((chunkSize <= 30 && chunkSize > 10) || (foundBetter && chunkSize > 6));
			
		} catch (IOException e) {
			Log.error("Failed to create patch",e);
		}
		
		return candidate;
	}

	private boolean verifyPatch(byte[] fromBytes, byte[] toBytes, byte[] xDelta) {
		GDiffPatcher patcher = new GDiffPatcher();
		byte[] patched;
		try {
			patched = patcher.patch(fromBytes, xDelta);
			long patchDestCRC = calculateCRC(patched);
			long origDestCRC = calculateCRC(toBytes);
			if (patchDestCRC == origDestCRC) return true;
		} catch (IOException e) {
			Log.error("Failed to verify patch : "+e.getMessage(),e);
		}
		return false;
	}

	private final long calculateCRC(byte[] originalByteArray) {
		CRC32 crc32 = new CRC32();
		crc32.reset();
		crc32.update(originalByteArray);
		return crc32.getValue();
	}

	private String extract(ZipEntry fileEntry, String zipFile) throws PatchCreationException {
		InputStream inputStream = Util.getInputStream(zipFile, fileEntry.getName());
		File workspace = createWorkspace();
		OutputStream out = null;
		File target = new File(workspace, fileEntry.getName());
		try {
			target.getParentFile().mkdirs();
			
			out = new FileOutputStream(target);
			byte[] buf = new byte[32*1024];
			int read = 0;
			while ((read = inputStream.read(buf)) != -1) {
				out.write(buf, 0, read);
			}
				
			out.flush();
			return target.getAbsolutePath();	
		} catch (IOException e) {
			throw new PatchCreationException("failed to extract file, reason :"+e.getMessage(),e);
		} finally {
			if (out!=null)
				try {
					out.close();
				} catch (IOException e) {
				}
		}
		
	}

	private void addToPatch(PatchAction action, Set<String> files,
			String zipFile, PatchData data) throws PatchCreationException {
		
		switch(action) {
		case COPY: {
			for (String file : files) {
				data.addFileForCopy(file, Util.getInputStream(zipFile,file));
			}
			break;
		}
		case DELETE: {
			for (String file : files) {
				data.addFileForDelete(file);
			}
			break;
		}
		
		}
	}

	public void compress() {
		Util.writePatchToFile(completePatch);
	}

}
