package org.bp.cinema.services;


import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.bp.cinema.model.MovieReservationRequest;
import org.bp.cinema.model.Reservation;
import org.bp.cinema.model.MovieReservationInfo;
import org.springframework.stereotype.Service;

@Service
public class ResponseService {
	private HashMap<String, ResponseData> responses;
	
	@PostConstruct
	void init() {
		responses=new HashMap<>();
	}
	
	public static class ResponseData {
		String errorMessage;
		MovieReservationInfo info;
		
		public String getErrorMessage() {
			return errorMessage;
		}
		public MovieReservationInfo getInfo() {
			return info;
		}

		public boolean isError() {
			return errorMessage!=null && errorMessage!="";
		}
		public boolean isReady() {
			return errorMessage != null || info != null;
		}
		
	}
	
	public synchronized boolean addErrorMessage(String reservationId, String errorMessage) {
		ResponseData paymentData = getResponseData(reservationId);
		paymentData.errorMessage=errorMessage;		
		return paymentData.isReady();
	}
	
	

	public synchronized boolean addReservationInfo(String reservationId, MovieReservationInfo reservationInfo) {
		ResponseData paymentData = getResponseData(reservationId);
		paymentData.info=reservationInfo;		
		return paymentData.isReady();
	}	
	
	
	public synchronized ResponseData getResponseData(String reservationId) {
		ResponseData responseData = responses.get(reservationId);
		if (responseData==null) {
			responseData = new ResponseData();
			responses.put(reservationId, responseData);
		}
		return responseData;
	}

	


	

}
