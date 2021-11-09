package org.bp.cinema.exceptions;

@SuppressWarnings("serial")
public class TicketException extends Exception {

	public TicketException() {
	}

	public TicketException(String message) {
		super(message);
	}

	public TicketException(Throwable cause) {
		super(cause);
	}

	public TicketException(String message, Throwable cause) {
		super(message, cause);
	}

	public TicketException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
