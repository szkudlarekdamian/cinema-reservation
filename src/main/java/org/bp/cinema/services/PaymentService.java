package org.bp.cinema.services;


import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.bp.cinema.model.MovieReservationRequest;
import org.bp.cinema.model.Reservation;
import org.bp.cinema.model.MovieReservationInfo;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
	private HashMap<String, PaymentData> payments;
	
	@PostConstruct
	void init() {
		payments=new HashMap<>();
	}
	
	public static class PaymentData {
		MovieReservationRequest movieReservationRequest;
		MovieReservationInfo ticketInfo;
		MovieReservationInfo snackInfo;
		public boolean isReady() {
			return movieReservationRequest!=null && ticketInfo!=null && snackInfo!=null;
		}
		public MovieReservationInfo getCombinedReservationInfo(String reservationId) {
			double ticketCost = ticketInfo.getCost();
			double snackCost = snackInfo.getCost();
			
			Reservation combinedReservation = new Reservation(					
				snackInfo.getReservation().getPerson(),
				ticketInfo.getReservation().getTicket(),
				snackInfo.getReservation().getSnack());
			
			return new MovieReservationInfo(
					reservationId,
					ticketCost+snackCost,
					combinedReservation
					);
		}
	}
	
	public synchronized boolean addMovieReservationRequest(String reservationId, MovieReservationRequest movieReservationRequest) {
		PaymentData paymentData = getPaymentData(reservationId);
		paymentData.movieReservationRequest=movieReservationRequest;		
		return paymentData.isReady();
	}
	

	public synchronized boolean addReservationInfo(String reservationId, MovieReservationInfo reservationInfo, String serviceType) {
		PaymentData paymentData = getPaymentData(reservationId);
		if (serviceType.equals("ticket"))
			paymentData.ticketInfo=reservationInfo;
		else 
			paymentData.snackInfo=reservationInfo;		
		return paymentData.isReady();
	}	
	
	
	public synchronized PaymentData getPaymentData(String reservationId) {
		PaymentData paymentData = payments.get(reservationId);
		if (paymentData==null) {
			paymentData = new PaymentData();
			payments.put(reservationId, paymentData);
		}
		return paymentData;
	}

	


	

}
