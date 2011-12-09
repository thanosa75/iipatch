package org.iipatch.maker;

import org.iipatch.common.PatchException;

public class PatchCreationException extends PatchException {

	public PatchCreationException() {
	}

	public PatchCreationException(String message) {
		super(message);
	}

	public PatchCreationException(Throwable cause) {
		super(cause);
	}

	public PatchCreationException(String message, Throwable cause) {
		super(message, cause);
	}

}
