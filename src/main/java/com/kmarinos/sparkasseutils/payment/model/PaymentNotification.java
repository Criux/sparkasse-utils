package com.kmarinos.sparkasseutils.payment.model;

import lombok.Builder;

public record PaymentNotification(String message, String title,String icon) {
@Builder public PaymentNotification{}
}
