package org.iipatch.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Cleaner {

	static {
		
		Runtime.getRuntime().addShutdownHook(new Thread(new CleanerExecutor()));
	}
	
	static class CleanerExecutor implements Runnable {

		@Override
		public void run() {
			for (File f : deletionList) {
				//Log.info("About to remove "+f.getAbsolutePath());
				Util.deleteAll(f.getAbsolutePath());
				if (!f.delete()) {
					Log.error("failed to delete "+f, null);
				}
			}
		}
		
	}
	
	
	static List<File> deletionList = new ArrayList<File>();
	
	public static void registerForDeletion(File f) {
		deletionList.add(f);
	}
}
