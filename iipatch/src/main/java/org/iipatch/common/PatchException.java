package org.iipatch.common;

public class PatchException extends Exception {

	public PatchException() {
	}

	public PatchException(String message) {
		super(message);
	}

	public PatchException(Throwable cause) {
		super(cause);
	}

	public PatchException(String message, Throwable cause) {
		super(message, cause);
	}

}
