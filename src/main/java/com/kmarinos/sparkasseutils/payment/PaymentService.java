package com.kmarinos.sparkasseutils.payment;

import com.kmarinos.sparkasseutils.clients.Sparkasse;
import com.kmarinos.sparkasseutils.payment.model.RequestPaymentRequest;
import com.kmarinos.sparkasseutils.payment.model.RequestPaymentResponse;
import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final ScheduledThreadPoolExecutor executorService=new ScheduledThreadPoolExecutor(1);
  private final Sparkasse sparkasse;

  public <T> void registerPaymentRequest(RequestPaymentRequest request,
      Function<RequestPaymentResponse, T> callback) {
      executorService.submit(()->processPayment(request,callback));
  }

  public <T> T processPayment(RequestPaymentRequest request,
      Function<RequestPaymentResponse, T> callback) {
    log.info("Started processing request");
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    RequestPaymentResponse response;
    try {
      response = sparkasse.sendAmount(request.amount(), request.reason());
    } catch (IOException e) {
      e.printStackTrace();
      response = RequestPaymentResponse.builder()
          .ok(false)
          .amount(request.amount())
          .message("Transfer successful").build();
    }

    return callback.apply(response);
  }
}
