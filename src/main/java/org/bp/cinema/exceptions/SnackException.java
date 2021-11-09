package org.bp.cinema.exceptions;

@SuppressWarnings("serial")
public class SnackException extends Exception {

	public SnackException() {
	}

	public SnackException(String message) {
		super(message);
	}

	public SnackException(Throwable cause) {
		super(cause);
	}

	public SnackException(String message, Throwable cause) {
		super(message, cause);
	}

	public SnackException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
