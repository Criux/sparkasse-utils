package com.kmarinos.sparkasseutils.payment.model;

import lombok.Builder;


public record RequestPaymentResponse(boolean ok,String amount,String message) {
  @Builder public RequestPaymentResponse{}
}
