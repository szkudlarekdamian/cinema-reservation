package org.bp.cinema.services;

import org.bp.cinema.model.MovieReservationInfo;
import org.bp.cinema.model.Reservation;

public class Utils {
	static public MovieReservationInfo prepareMovieReservationInfo(String id, double cost, Reservation reservation) {
		MovieReservationInfo info = new MovieReservationInfo();
		info.setId(id);
		info.setCost(cost);
		info.setReservation(reservation);
		return info;
	}

}
