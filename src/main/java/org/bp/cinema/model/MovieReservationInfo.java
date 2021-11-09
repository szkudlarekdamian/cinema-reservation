/*
 * Cinema reservation service
 * Micro service to buy cinema tickets and bar snacks.
 *
 * OpenAPI spec version: 1.0.0
 * Contact: supportm@bp.org
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package org.bp.cinema.model;

import java.util.Objects;
import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.bp.cinema.model.Reservation;
/**
 * MovieReservationInfo
 */

@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.JavaClientCodegen", date = "2021-02-20T22:53:52.011523+01:00[Europe/Warsaw]")
public class MovieReservationInfo {
  @JsonProperty("id")
  private String id = null;

  @JsonProperty("cost")
  private Double cost = null;

  @JsonProperty("reservation")
  private Reservation reservation = null;

  public MovieReservationInfo(String reservationId, double d, Reservation combinedReservation) {
	this.setId(reservationId);
	this.setCost(d);
	this.setReservation(combinedReservation);
}

public MovieReservationInfo() {
	this.setId(null);
	this.setCost(null);
	this.setReservation(null);
}

public MovieReservationInfo id(String id) {
    this.id = id;
    return this;
  }

   /**
   * Get id
   * @return id
  **/

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public MovieReservationInfo cost(Double cost) {
    this.cost = cost;
    return this;
  }

   /**
   * Get cost
   * @return cost
  **/

  public Double getCost() {
    return cost;
  }

  public void setCost(Double cost) {
    this.cost = cost;
  }

  public MovieReservationInfo reservation(Reservation reservation) {
    this.reservation = reservation;
    return this;
  }

   /**
   * Get reservation
   * @return reservation
  **/

  public Reservation getReservation() {
    return reservation;
  }

  public void setReservation(Reservation reservation) {
    this.reservation = reservation;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MovieReservationInfo movieReservationInfo = (MovieReservationInfo) o;
    return Objects.equals(this.id, movieReservationInfo.id) &&
        Objects.equals(this.cost, movieReservationInfo.cost) &&
        Objects.equals(this.reservation, movieReservationInfo.reservation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, cost, reservation);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MovieReservationInfo {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    cost: ").append(toIndentedString(cost)).append("\n");
    sb.append("    reservation: ").append(toIndentedString(reservation)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}