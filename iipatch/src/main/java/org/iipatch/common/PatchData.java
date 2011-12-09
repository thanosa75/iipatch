package org.iipatch.common;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import org.iipatch.maker.PatchCreationException;

import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicMatch;


public class PatchData implements Serializable {

	private static final String PATCH_MIMETYPE = "patch/x-delta;rawdata";

	private String name;
	
	public PatchData(String targetName) {
		name = targetName;
		Log.info("PatchData created for "+name);
	}
	
	/**
	 * uid.
	 */
	private static final long serialVersionUID = 0x10005000L;

	List<String> contentToDelete = new ArrayList<String>();
	
	Map<String, List<String>> contentTypeToFileSHAMap = new HashMap<String, List<String>>();
	
	Map<String, byte[]> contentSHAtoBytesMap = new HashMap<String, byte[]>();
	
	Map<String, String> contentSHAtoFileName = new HashMap<String,String>();
	
	List<PatchData> innerPatches = new ArrayList<PatchData>();
	
	public void addFileForCopy(String file, InputStream data) throws PatchCreationException {
		DigestInputStream dis = null;
		
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			dis = new DigestInputStream(data, MessageDigest.getInstance("SHA"));
			byte[] buf = new byte[32*1024];
			int read = 0;
			while ((read = dis.read(buf)) != -1) {
				bout.write(buf,0,read);
			}
			//eof reached, got file and SHA
			String signature = new BigInteger(1,dis.getMessageDigest().digest()).toString(16);
			//add to SHA list
			byte[] byteArray = bout.toByteArray();
			contentSHAtoBytesMap.put(signature, byteArray);
			//add to contentTypes list
			String mimeType = null;
			try {
				MagicMatch match = Magic.getMagicMatch(byteArray);
				mimeType = match.getMimeType();
				if ("text/x-java".equals(mimeType)) {
					mimeType = "text/plain"; // workaround
				}
			} catch (Exception e) {
				mimeType = "application/octet-stream";
			}
			if (!contentTypeToFileSHAMap.containsKey(mimeType)) {
				contentTypeToFileSHAMap.put(mimeType, new ArrayList<String>());
			}
			
			contentTypeToFileSHAMap.get(mimeType).add(signature);
			contentSHAtoFileName.put(signature, file);
			Log.info("COPY "+mimeType+" "+signature+" "+file+ " "+byteArray.length);
		} catch (Exception e) {
			throw new PatchCreationException("Unable to add file, error is "+e.getMessage(),e);
		}
		
	}
	
	public void addFileForDelete(String file) {
		Log.info("DELETE "+file);
		contentToDelete.add(file);
	}

	public void addInnerPatch(String file, PatchData inner) {
		Log.info("INNER "+inner);
		innerPatches.add(inner);
		//contentSHAtoFileName.put(signature, file);
	}

	public void addDeltaPatch(ZipEntry from, byte[] patch) throws PatchCreationException {
		byte[] digest;
		try {
			digest = MessageDigest.getInstance("SHA").digest(patch);

			String signature = new BigInteger(1, digest).toString(16);

			// add to SHA list
			contentSHAtoBytesMap.put(signature, patch);
			// add to contentTypes list
			if (!contentTypeToFileSHAMap.containsKey(PATCH_MIMETYPE)) {
				contentTypeToFileSHAMap.put(PATCH_MIMETYPE,
						new ArrayList<String>());
			}
			contentTypeToFileSHAMap.get(PATCH_MIMETYPE).add(signature);
			contentSHAtoFileName.put(signature, from.getName());
			Log.info("DELTA "+PATCH_MIMETYPE+" "+signature+" "+from.getName()+ " "+patch.length);
		} catch (NoSuchAlgorithmException e) {
			throw new PatchCreationException("Unable to add patch, error is "+e.getMessage(),e);
		}
	}

	@Override
	public String toString() {
		return "PatchData [name=" + name + ", contentToDelete="
				+ contentToDelete.size() + ", contentTypeToFileSHAMap="
				+ contentTypeToFileSHAMap.size() + ", contentSHAtoBytesMap="
				+ contentSHAtoBytesMap.size() + ", contentSHAtoFileName="
				+ contentSHAtoFileName.size() + ", innerPatches=" + innerPatches + "]";
	}
	
	
}
