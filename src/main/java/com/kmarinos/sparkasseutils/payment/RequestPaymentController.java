package com.kmarinos.sparkasseutils.payment;

import com.kmarinos.sparkasseutils.payment.model.PaymentNotification;
import com.kmarinos.sparkasseutils.payment.model.RequestPaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000"})
@Slf4j
public class RequestPaymentController {

  private final PaymentService paymentService;
  @Value("${banksy.token}")
  String token;
  @Value("${banksy.ha.webhook}/api/webhook/")
  String webhookUrl;

  @PostMapping("request-payment")
  public ResponseEntity<Void> requestPayment(@RequestHeader("Authorization") String token,
      @RequestBody RequestPaymentRequest requestPaymentRequest) {

    log.debug("Expected token:{}",this.token);
    log.debug("Received token:{}",token);
    log.debug("HomeAssistant Url:{}",webhookUrl);
    if (token == null || !token.replace("Bearer ", "").equals(this.token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (requestPaymentRequest == null || requestPaymentRequest.amount() == null
        || requestPaymentRequest.amount().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    paymentService.registerPaymentRequest(requestPaymentRequest, response -> {
      var notification = PaymentNotification.builder()
          .title(response.ok() ? "Money Transferred" : "Error while transferring money")
          .message(response.ok() ? response.amount() + "€ was added to your account."
              : response.message())
          .icon(response.ok()?"mdi:currency-usd":"mdi:currency-usd-off")
          .build();

      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      restTemplate.exchange(webhookUrl + this.token,
          HttpMethod.POST, new HttpEntity<>(notification, headers), String.class);
      return notification;
    });

    return ResponseEntity.ok().build();
  }
}
