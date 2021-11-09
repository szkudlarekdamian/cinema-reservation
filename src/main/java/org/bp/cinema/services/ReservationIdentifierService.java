package org.bp.cinema.services;


import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ReservationIdentifierService {
	
	public String getReservationIdentifier() {
		return UUID.randomUUID().toString();
	}
	

}