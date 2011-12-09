package org.iipatch.common;

/**
 * A simple concentrator for logging - allows flexibility on choosing 
 * logging options.
 * @author agelatos
 *
 */
public final class Log {
	public static final void info(String message) {
		System.out.println(message);
	}

	public static void error(String message, Exception e) {
		System.err.println(message);
		if (e!=null) e.printStackTrace();
	}
}
