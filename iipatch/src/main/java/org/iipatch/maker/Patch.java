package org.iipatch.maker;

import org.iipatch.applier.PatchApplier;
import org.iipatch.common.Log;

public class Patch {
	static final String APP_NAME = "i³patch";
	static final String APP_VERSION = "v0.60";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Log.info(APP_NAME + " " + APP_VERSION
				+ " - Universal ZIP/JAR/WAR/EAR patch creator/applier");
		if ((args.length == 0) || (args.length < 2)) {
			fail();
			return;
		}

		try {
			String switchParam = args[0];
			if (switchParam.contains("-c")) {
				Patch.createPatch(args);
			} else if (switchParam.contains("-p")) {
				Patch.applyPatch(args);
			} else {
				fail();
			}
		} catch (Exception e) {
			Log.error("Failed to complete the operation, error is "+e.getMessage(),e);
		}
		
	}

	private static void fail() {
		Log.info("\tOptions: ");
		Log.info("\t-c <file1> <file2>");
		Log.info("\t\tcreate 'changes.paz' containing all changes that, if applied ");
		Log.info("\t\twill upgrade file1 to be the same as file2.");
		
		Log.info("\t-p [<file1>]");
		Log.info("\t\twill apply a patch (either found locally, named 'changes.paz' or inside the archive)");
		Log.info("\t\tto file1 - if not specified, it will look for 'file1' locally.");
	}

	private static void applyPatch(String[] args) {
		PatchApplier applier = new PatchApplier(args);
	}

	private static void createPatch(String[] args) throws PatchCreationException {
		PatchMaker maker = new PatchMaker(args);
		maker.calculateDifferences();
		maker.compress();
	}

	
	
}
