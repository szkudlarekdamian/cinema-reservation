package org.bp.cinema.state;


import java.util.HashMap;


public class StateService {
	private HashMap<String, StateMachine> processingStates=new HashMap<>();

	public StateService(StateMachineBuilder stateMachineBuilder) {
		this.stateMachineBuilder = stateMachineBuilder;
	}


	private StateMachineBuilder stateMachineBuilder = null;

	public ProcessingState sendEvent(String reservationId, ProcessingEvent event) {
		StateMachine stateMachine;
		synchronized(this){
			stateMachine = processingStates.get(reservationId);
			if (stateMachine==null) {
				stateMachine=stateMachineBuilder.build();
				processingStates.put(reservationId, stateMachine);
			}
		}
		return stateMachine.sendEvent(event);
		
	}
	
	public void removeState(String reservationId) {
		processingStates.remove(reservationId);
	}

}
