package org.iipatch.common;

/**
 * possible actions on a patch description file
 * @author agelatos
 *
 */
public enum PatchAction {
	COPY, // a simple copy
	COPYXDELTA, // a copy with xdelta (differences)
	DELETE // a delete
}
