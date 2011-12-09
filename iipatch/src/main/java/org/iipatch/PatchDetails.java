package org.iipatch;

import java.io.Serializable;
import java.util.Map;

class PatchDetails implements Serializable {
	private static final long serialVersionUID = 5545696632554850092L;
	public String origFileName;
	public long origFileSize;
	public long patchFiles;
	public long patchFilesSize;
	public Map<String, Serializable> patchCandidates;
	public Map<String, Serializable> shaBytes;
	public byte[] origMD5;
	public transient long patchTotalSize;

	public PatchDetails(String fName, long fLen, long files, long fileSize) {
		this.origFileName = fName;
		this.origFileSize = fLen;
		this.patchFiles = files;
		this.patchFilesSize = fileSize;
	}

}

/*
 * Location: C:\Users\agelatos\Desktop\iipatch.jar Qualified Name:
 * org.nosymmetry.patch.PatchDetails JD-Core Version: 0.6.0
 */