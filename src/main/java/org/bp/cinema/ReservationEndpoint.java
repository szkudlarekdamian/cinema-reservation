package org.bp.cinema;

import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.model.rest.RestParamType.body;
import java.time.OffsetDateTime;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;


import org.bp.cinema.model.ExceptionResponse;
import org.bp.cinema.exceptions.SnackException;
import org.bp.cinema.exceptions.TicketException;
import org.bp.cinema.model.MovieReservationInfo;
import org.bp.cinema.model.MovieReservationRequest;
import org.bp.cinema.model.Reservation;
import org.bp.cinema.services.PaymentService;
import org.bp.cinema.services.ReservationIdentifierService;
import org.bp.cinema.services.ResponseService;
import org.bp.cinema.services.Utils;
import org.bp.cinema.state.ProcessingEvent;
import org.bp.cinema.state.ProcessingState;
import org.bp.cinema.state.StateService;

@Component
public class ReservationEndpoint extends RouteBuilder {
	
	private final static String RESERVATION_ID = "reservationId";

//	GATEWAY
	private final static String MOVIE_RESERVE = "movieReserve";
	private final static String MOVIE_RESERVE_FINALIZER = "movieReserveFinalizer";
	private final static String GET_RESERVATION_BY_ID = "getReservationById";
	private final static String CACHE = "cache";

//	TICKET
	private final static String TICKET_RESERVE = "ticketReserve";
	private final static String TICKET_RESERVE_COMPENSATION = "ticketReserveCompensation";
	private final static String TICKET_RESERVE_COMPENSATION_ACTION = "ticketReserveCompensationAction";

//	SNACK
	private final static String SNACK_RESERVE = "snackReserve";
	private final static String SNACK_RESERVE_COMPENSATION = "snackReserveCompensation";
	private final static String SNACK_RESERVE_COMPENSATION_ACTION = "snackReserveCompensationAction";

//	PAYMENT
	private final static String PAYMENT_FINALIZER = "paymentFinalizer";
	private final static String NOTIFICATION = "notification";

	
//	KAFKA	
	private final static String TOPIC_MOVIE_RESERVE = "movieReserveTopic";
	private final static String TOPIC_RESERVATION_INFO= "reservationInfoTopic";
	private final static String TOPIC_RESERVATION_FAIL = "reservationFailTopic";
	private final static String CACHE_MOVIE_RESERVATION_INFO = "cacheMovieReservationInfo";
	private final static String CACHE_ERORR_MESSAGE = "cacheError";


	String cacheErrorMessage = null;
	MovieReservationInfo cacheMovieReservationInfo = null;
	Exchange cache = null;
	
	@Autowired
	ReservationIdentifierService reservationIdentifierService;
	@Autowired
	PaymentService paymentService; 
	@Autowired
	ResponseService responseService;
	@Autowired
	StateService ticketStateService;
	@Autowired
	StateService snackStateService;
	
	@Value("${kafka.server}")
	private String kafkaServer;
	
	@Value("${kafka.service.type}")
	private String kafkaServiceType;
	
	@Override
	public void configure() throws Exception {
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("ticket"))
			exceptionHandlers(TicketException.class, "ticket");
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("snack"))
			exceptionHandlers(SnackException.class, "snack");
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("gateway"))
			gateway();
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("ticket"))
			ticket();
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("snack"))
			snack();
		if (kafkaServiceType.equals("all") || kafkaServiceType.equals("payment"))
			payment();

	}

	private void exceptionHandlers(@SuppressWarnings("rawtypes") Class _class, String serviceType) {
		onException(_class).process((exchange) -> {
			ExceptionResponse er = new ExceptionResponse();
			er.setTimestamp(OffsetDateTime.now());
			Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
			er.setMessage(cause.getMessage());
			exchange.getMessage().setBody(er);
		}).marshal().json().to("stream:out").setHeader("serviceType", constant(serviceType))
				.to(getKafkaTopic(TOPIC_RESERVATION_FAIL)).handled(true);
	}
	
	private void gateway() {
		restConfiguration()
			.component("servlet")
			.bindingMode(RestBindingMode.json)
			.dataFormatProperty("prettyPrint", "true")
			.enableCORS(true)
			.contextPath("/api")
			// turn on swagger api-doc
			.apiContextPath("/api-doc")
			.apiProperty("api.title", "Cinema reservation API")
			.apiProperty("api.version", "1.0.0");
		
		
        rest("/reservation/")
	        .description("Cinema tickets and snacks reservation service.")
	        .consumes("application/json")
	        .produces("application/json")
	        
	        .post("/")
		        .description("Seat reservation for the movie.")
		        .type(MovieReservationRequest.class)
		        .outType(MovieReservationInfo.class)
		        .param()
		        	.name("body")
		        	.type(body)
		        	.description("Details of seat reservation for the movie screening.")
		        .endParam()
		        .responseMessage()
		        	.code(200)
		        	.message("Seat successfully reserved.")
		        .endResponseMessage()
		        .responseMessage()
		        	.responseModel(String.class)
		        	.code(406)
		        	.message("Reservation not acceptable.")
		        .endResponseMessage()
		        .to("direct:"+MOVIE_RESERVE)

	        .get("/{id}")
	        	.description("Returns information about a person, ticket and a snack.")
	        	.outType(MovieReservationInfo.class)
		        .param()
		        	.type(RestParamType.path)
		        	.name("id")
		        	.description("Reservation ID")
		        .endParam()
		        .responseMessage().code(200).message("Reservation found.").endResponseMessage()
		        .responseMessage().responseModel(String.class).code(404).message("Reservation doesn't exist").endResponseMessage()
		        .to("direct:"+GET_RESERVATION_BY_ID);
        
        //GET
        from("direct:"+GET_RESERVATION_BY_ID).routeId(GET_RESERVATION_BY_ID)
        .log(GET_RESERVATION_BY_ID + "[MAIN]")
        .to(getKafkaTopic(GET_RESERVATION_BY_ID, "payment"))
        .delay(200)
        .process((exchange)->{
        	if(cache != null) {	
				if(cache.getMessage().getHeaders().containsKey("RESERVATION_INFO")) {
					String reservationInfoHeader = cache.getMessage().getHeader("RESERVATION_INFO", String.class);
	        		if(reservationInfoHeader.equals("true")) {
	        			if(cacheMovieReservationInfo!=null) {
		            		exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
		            		exchange.getMessage().setBody(cacheMovieReservationInfo);
		        		}
	        		}
	        		else {
		        		if(cacheErrorMessage!=null) {
		            		exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
		            		exchange.getMessage().setBody(cacheErrorMessage);
		        		}
	        		}
				}
        		else {
	        		exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
	        		System.out.println("UNKNOWN INPUT");
	        		exchange.getMessage().setBody(cache.getMessage().getBody());
        		}
        		cache=null;
        		cacheErrorMessage=null;
        		cacheMovieReservationInfo=null;
        	}
        	else 
        		System.out.println("CACHE NULL");
        })
        .end();
        

        from(getKafkaTopic(CACHE_MOVIE_RESERVATION_INFO)).routeId(CACHE_MOVIE_RESERVATION_INFO)
        .log(CACHE_MOVIE_RESERVATION_INFO + " fired")
        .unmarshal().json(JsonLibrary.Jackson, MovieReservationInfo.class)
        .process((exchange)->{
            	cacheMovieReservationInfo = exchange.getMessage().getBody(MovieReservationInfo.class);
            	cache = exchange.copy();
         })
        .end();
        
        
        from(getKafkaTopic(CACHE_ERORR_MESSAGE)).routeId(CACHE_ERORR_MESSAGE)
        .log(CACHE_ERORR_MESSAGE + " fired")
        .process((exchange)->{
            	cacheErrorMessage = exchange.getMessage().getBody(String.class);
            	cache = exchange.copy();
         })
        .end();
        
              
        //POST
        from("direct:"+MOVIE_RESERVE).routeId(MOVIE_RESERVE)
	        .log(MOVIE_RESERVE+"[POST] fired")
	        .process((exchange) -> {
	        	exchange.getMessage().setHeader(RESERVATION_ID, reservationIdentifierService.getReservationIdentifier());
	        })
	        .to("direct:"+TOPIC_MOVIE_RESERVE)
	        .delay(200)
        	.to("direct:"+MOVIE_RESERVE_FINALIZER);
        
        from("direct:"+MOVIE_RESERVE_FINALIZER).routeId(MOVIE_RESERVE_FINALIZER)
        	.log(MOVIE_RESERVE_FINALIZER+" fired")
        	.process((exchange) -> {
				String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
				if(cache != null) {	
					if(!cache.getMessage().getHeaders().containsKey("RESERVATION_INFO")) {
						if (cacheMovieReservationInfo!=null) {
		            		exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
							exchange.getMessage().setBody(cacheMovieReservationInfo);
						}
						else if (cacheErrorMessage!=null) {
							exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 406);
							exchange.getMessage().setBody(cacheErrorMessage);
						}
						else
							System.out.println("UNKNOWN CACHED MESSAGE");
						cache=null;
						cacheErrorMessage=null;
						cacheMovieReservationInfo=null;
					}
				}
				else {
        			exchange.getMessage().setBody(
        					Utils.prepareMovieReservationInfo(
        							exchange.getMessage().getHeader(RESERVATION_ID, String.class),
        							0.0,
        							new Reservation(null,null,null)
        					));
        		}
        	});
        
		from("direct:"+TOPIC_MOVIE_RESERVE).routeId(TOPIC_MOVIE_RESERVE)
			.log(TOPIC_MOVIE_RESERVE+"[KAFKA] fired")
			.marshal().json()
			.to(getKafkaTopic(TOPIC_MOVIE_RESERVE));
			
        
	}
	
	private void ticket() {
		from(getKafkaTopic(TOPIC_MOVIE_RESERVE)).routeId(TICKET_RESERVE)
			.log(TICKET_RESERVE+" fired")
			.unmarshal().json(JsonLibrary.Jackson, MovieReservationRequest.class)
			.process(
				(exchange) -> {
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					ProcessingState previousState = ticketStateService.sendEvent(reservationId, ProcessingEvent.START);
					if (previousState != ProcessingState.CANCELLED) {
						MovieReservationInfo info = new MovieReservationInfo();
						info.setId(reservationIdentifierService.getReservationIdentifier());
						MovieReservationRequest request = exchange.getMessage().getBody(MovieReservationRequest.class);
						Boolean isCity = request!=null &&
								request.getReservation()!=null && 
								request.getReservation().getTicket() != null &&
								request.getReservation().getTicket().getCinema()!=null &&
								request.getReservation().getTicket().getCinema().getCity() != null;
						if (isCity) {
							info.setReservation(request.getReservation());
							String city = request.getReservation().getTicket().getCinema().getCity();
							if (city.equals("Poznan"))
								throw new TicketException("Cinemas in " + city + " closed due to COVID-19.");
							else 
								info.setCost(info.getReservation().getTicket().getPrice());						
						}
				
						exchange.getMessage().setBody(info);
						previousState = ticketStateService.sendEvent(reservationId, ProcessingEvent.FINISH);
					}
					exchange.getMessage().setHeader("previousState", previousState);
				}
			)
			.marshal().json()
			.to("stream:out")
			.choice()
				.when(header("previousState").isEqualTo(ProcessingState.CANCELLED))
					.to("direct:"+TICKET_RESERVE_COMPENSATION_ACTION)
				.otherwise()
					.setHeader("serviceType", constant("ticket"))
					.to(getKafkaTopic(TOPIC_RESERVATION_INFO))
			.endChoice();	
		
		from(getKafkaTopic(TOPIC_RESERVATION_FAIL)).routeId(TICKET_RESERVE_COMPENSATION)
			.log(TICKET_RESERVE_COMPENSATION+" fired").unmarshal().json(JsonLibrary.Jackson, ExceptionResponse.class)
			.choice()
				.when(header("serviceType").isNotEqualTo("ticket"))
					.process((exchange) -> {
						String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
						ProcessingState previousState = ticketStateService.sendEvent(reservationId,ProcessingEvent.CANCEL);
						exchange.getMessage().setHeader("previousState", previousState);
						exchange.getMessage().setBody(exchange.getMessage().getBody(ExceptionResponse.class).getMessage());
					})
					.to(getKafkaTopic(CACHE_ERORR_MESSAGE, "gateway"))
					.choice()
						.when(header("previousState").isNotEqualTo(ProcessingState.FINISHED))
							.to("direct:"+TICKET_RESERVE_COMPENSATION_ACTION)
					.endChoice()
			.endChoice();

		from("direct:"+TICKET_RESERVE_COMPENSATION_ACTION).routeId(TICKET_RESERVE_COMPENSATION_ACTION)
				.log(TICKET_RESERVE_COMPENSATION_ACTION+" fired").process((exchange)->{
					ProcessingState previousState = exchange.getMessage().getHeader("previousState", ProcessingState.class);
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					ticketStateService.removeState(reservationId);
				}).to("stream:out")
				;
	}
	
	private void snack() {
		from(getKafkaTopic(TOPIC_MOVIE_RESERVE)).routeId(SNACK_RESERVE)
		.log(SNACK_RESERVE+" fired")
		.unmarshal().json(JsonLibrary.Jackson, MovieReservationRequest.class)
		.process(
			(exchange) -> {
				String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
				ProcessingState previousState = snackStateService.sendEvent(reservationId, ProcessingEvent.START);
				if (previousState != ProcessingState.CANCELLED) {
					MovieReservationInfo info = new MovieReservationInfo();
					info.setId(reservationIdentifierService.getReservationIdentifier());
					MovieReservationRequest request = exchange.getMessage().getBody(MovieReservationRequest.class);
					Boolean isName = request!=null &&
							request.getReservation()!=null && 
							request.getReservation().getSnack()!=null &&
							request.getReservation().getSnack().getName()!=null;
					if (isName) {
						info.setReservation(request.getReservation());
						String name = request.getReservation().getSnack().getName();
						Integer age = request.getReservation().getPerson().getAge();
						
						if (name.equals("Beer") && age<18)
							throw new SnackException("Alcohol is for adults only.");
						else 
							info.setCost(info.getReservation().getSnack().getPrice());					
					}
					exchange.getMessage().setBody(info);
					previousState = snackStateService.sendEvent(reservationId, ProcessingEvent.FINISH);
				}
				exchange.getMessage().setHeader("previousState", previousState);
			}
		)
		.marshal().json()
		.to("stream:out")
		.choice()
			.when(header("previousState").isEqualTo(ProcessingState.CANCELLED))
				.to("direct:"+SNACK_RESERVE_COMPENSATION_ACTION)
			.otherwise()
				.setHeader("serviceType", constant("snack"))
				.to(getKafkaTopic(TOPIC_RESERVATION_INFO))
		.endChoice();	
		
		from(getKafkaTopic(TOPIC_RESERVATION_FAIL)).routeId(SNACK_RESERVE_COMPENSATION)
		.log(SNACK_RESERVE_COMPENSATION+" fired").unmarshal().json(JsonLibrary.Jackson, ExceptionResponse.class)
		.choice()
			.when(header("serviceType").isNotEqualTo("snack"))
				.process((exchange) -> {
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					ProcessingState previousState = ticketStateService.sendEvent(reservationId,ProcessingEvent.CANCEL);
					exchange.getMessage().setHeader("previousState", previousState);
					exchange.getMessage().setBody(exchange.getMessage().getBody(ExceptionResponse.class).getMessage());
				})
				.to(getKafkaTopic(CACHE_ERORR_MESSAGE, "gateway"))
				.choice()
					.when(header("previousState").isNotEqualTo(ProcessingState.FINISHED))
						.to("direct:"+SNACK_RESERVE_COMPENSATION_ACTION)
				.endChoice()
		.endChoice();

	from("direct:"+SNACK_RESERVE_COMPENSATION_ACTION).routeId(SNACK_RESERVE_COMPENSATION_ACTION)
			.log(SNACK_RESERVE_COMPENSATION_ACTION+" fired")
			.process((exchange)->{
				ProcessingState previousState = exchange.getMessage().getHeader("previousState", ProcessingState.class);
				String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
				snackStateService.removeState(reservationId);
			})
			.to("stream:out");
	}
	
	private void payment() {
		from(getKafkaTopic(TOPIC_RESERVATION_INFO)).routeId(TOPIC_RESERVATION_INFO)
			.log(TOPIC_RESERVATION_INFO+"[KAFKA] fired")
			.unmarshal().json(JsonLibrary.Jackson, MovieReservationInfo.class)
			.process(
				(exchange) -> {
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					boolean isReady= paymentService.addReservationInfo(
						reservationId,
						exchange.getMessage().getBody(MovieReservationInfo.class),
						exchange.getMessage().getHeader("serviceType", String.class));
					exchange.getMessage().setHeader("isReady", isReady);
				}
			)
			.choice()
				.when(header("isReady").isEqualTo(true)).to("direct:"+PAYMENT_FINALIZER)
			.endChoice();
		
		from(getKafkaTopic(TOPIC_MOVIE_RESERVE)).routeId(TOPIC_MOVIE_RESERVE+"Payment")
			.log(TOPIC_MOVIE_RESERVE+"Payment[KAFKA] fired")
			.unmarshal().json(JsonLibrary.Jackson, MovieReservationRequest.class)
			.process(
				(exchange) -> {
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					boolean isReady= paymentService.addMovieReservationRequest(
						reservationId,
						exchange.getMessage().getBody(MovieReservationRequest.class));
					exchange.getMessage().setHeader("isReady", isReady);
				}
			)
			.choice()
				.when(header("isReady").isEqualTo(true)).to("direct:"+PAYMENT_FINALIZER)
			.endChoice();
		
		from("direct:"+PAYMENT_FINALIZER).routeId(PAYMENT_FINALIZER)
			.log(PAYMENT_FINALIZER+" fired")
			.process(
				(exchange) -> {
					String reservationId = exchange.getMessage().getHeader(RESERVATION_ID, String.class);
					PaymentService.PaymentData paymentData = paymentService.getPaymentData(reservationId);
					MovieReservationInfo combined = paymentData.getCombinedReservationInfo(reservationId);
					exchange.getMessage().setBody(combined);
//					responseService.addReservationInfo(reservationId, combined);
				}
			).to("direct:"+NOTIFICATION);
		
		from("direct:"+NOTIFICATION).routeId(NOTIFICATION)
			.log(NOTIFICATION + " fired")
			.marshal().json()
			.to(getKafkaTopic(CACHE_MOVIE_RESERVATION_INFO, "gateway"))
			.to("stream:out");
		
		//GET
        from(getKafkaTopic(GET_RESERVATION_BY_ID)).routeId(GET_RESERVATION_BY_ID+"Payment")
		.log(GET_RESERVATION_BY_ID + " payment [KAFKA] fired")
		.process((exchange) ->{
			String reservationId = exchange.getIn().getHeader("id", String.class);
			PaymentService.PaymentData paymentData = paymentService.getPaymentData(reservationId);	
			if(paymentData.isReady()) {
				MovieReservationInfo info = paymentData.getCombinedReservationInfo(reservationId);
				exchange.getMessage().setHeader("RESERVATION_INFO", "true");
				exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
				exchange.getMessage().setBody(info);
			}
			else {
				exchange.getMessage().setHeader("RESERVATION_INFO", "false");
				exchange.getMessage().setBody("Reservation with id: "+reservationId+" doesn't exist.");
				exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);			
			}
		})
		.choice()
			.when(header("RESERVATION_INFO").isEqualTo("true"))
				.marshal().json()
				.to(getKafkaTopic(CACHE_MOVIE_RESERVATION_INFO, "gateway"))
			.otherwise()
				.to(getKafkaTopic(CACHE_ERORR_MESSAGE, "gateway"))
		.endChoice()
		.end();
//		});
	}
	
	
	private String getKafkaTopic(String topic, String... service) {
//		return String.format("kafka:%s?brokers=localhost:9092", topic);
		
		return String.format("kafka:%s?brokers=%s", topic, this.kafkaServer+"&groupId="+ (service.length > 0 ? service[0] : this.kafkaServiceType));
	}

}
