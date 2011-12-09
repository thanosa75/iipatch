package org.iipatch.common;

import SevenZip.ICodeProgress;

public class ProgressLZMA implements ICodeProgress {
	/**
	 * totalsize
	 */
	long totalSize;

	public ProgressLZMA(long totalBytes) {
		this.totalSize = (totalBytes / 1024L);
		System.out.println();
	}

	public void SetProgress(long inputSize, long outputSize) {
		outputSize /= 1024L;
		if (outputSize % 100L == 0L) {
			inputSize /= 1024L;
			int percent = (int) (inputSize * 1.0D / this.totalSize * 1.0D * 100.0D);
			System.out.print("[" + percent + "%] [written: " + outputSize + "KB]\r");
		}
	}
}