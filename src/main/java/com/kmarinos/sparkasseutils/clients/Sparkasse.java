package com.kmarinos.sparkasseutils.clients;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.kmarinos.sparkasseutils.payment.model.RequestPaymentResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Sparkasse {

  @Value("${banksy.sparkasse.transfer.url}")
  String transferUrl;
  @Value("${banksy.sparkasse.user}")
  String username;
  @Value("${banksy.sparkasse.password}")
  String password;
  @Value("${banksy.sparkasse.transfer.recipient.name}")
  String recipientName;
  @Value("${banksy.sparkasse.transfer.recipient.iban}")
  String recipientIban;

  ThreadLocal<RequestPaymentResponse.RequestPaymentResponseBuilder> responseBuilder = new ThreadLocal<>();

  public void printVariables() {
    log.debug("transferUrl: {}", transferUrl);
    log.debug("username: {}", username);
    log.debug("password: {}", password);
    log.debug("recipientName: {}", recipientName);
    log.debug("recipientIban: {}", recipientIban);
  }

  public RequestPaymentResponse sendAmount(String amount, String reasonForPayment)
      throws IOException {

    this.printVariables();
    //initialize response builder
    responseBuilder.set(RequestPaymentResponse.builder());
    responseBuilder.get().amount("0");

    //check if the amount is set properly. If not return error
    BigDecimal actualAmount = validateAmountInput(amount);
    if (actualAmount.equals(BigDecimal.ZERO)) {
      return responseBuilder.get().build();
    }

    //login and process the payment request
    return this.login(transferUrl, loggedIn -> {
      try {
        return processPaymentRequest(actualAmount, reasonForPayment, loggedIn);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

  }

  public void printLatestBookings() throws IOException {
    this.login(
        "https://www.sparkasse-bielefeld.de/de/home/onlinebanking/umsaetze/umsaetze.html",
        this::printBookingsFromLoggedInPage);
  }


  public <T> T login(String url, Function<HtmlPage, T> loggedInCallback) throws IOException {

    HtmlPage page;
    try (WebClient client = new WebClient()) {
      log.debug("CSS {}", client.getOptions().isCssEnabled());
      log.debug("JS {}", client.getOptions().isJavaScriptEnabled());
      log.debug("CookieManager {}", client.getCookieManager().isCookiesEnabled());
      client.waitForBackgroundJavaScript(15000);
      client.waitForBackgroundJavaScriptStartingBefore(5000);
      client.setAjaxController(new NicelyResynchronizingAjaxController());
      client.getOptions().setUseInsecureSSL(true);
      client.getOptions().setThrowExceptionOnScriptError(false);

      //load initial page
      page = client.getPage(url);

      //find form
      HtmlForm loginForm = page.getForms().stream()
          .filter(f -> f.getElementsByTagName("input").stream()
              .anyMatch(input -> input.getAttribute("type").equals("password")))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("Form not found"));

      //select input elements that are empty. There should only be two: username and password.
      loginForm.getInputsByValue("").forEach(input -> {
        if (input.getTypeAttribute().equals("password")) {
          input.setValueAttribute(password);
        } else {
          input.setValueAttribute(username);
        }
      });

      //click button for login
      HtmlPage loggedIn = page.getElementById("defaultAction").click();
      return loggedInCallback.apply(loggedIn);
    }
  }
  public void login(String url, Consumer<HtmlPage> loggedInCallback) throws IOException {
    this.login(url, a -> {
      loggedInCallback.accept(a);
      return null;
    });
  }
  private BigDecimal validateAmountInput(String amount) {
    BigDecimal actualAmount;
    if (amount != null) {
      try {
        amount = amount.replace(",", ".");
        actualAmount = new BigDecimal(amount).setScale(2, RoundingMode.CEILING);
      } catch (Exception e) {
        log.error("Cannot convert {} to number", amount);
        responseBuilder.get().ok(false).message("Cannot convert " + amount + " to number");
        return BigDecimal.ZERO;
      }
    } else {
      responseBuilder.get().ok(false)
          .message("Amount must be set and it cannot be less or equal to zero");
      return BigDecimal.ZERO;
    }
    return actualAmount;
  }

  private RequestPaymentResponse processPaymentRequest(BigDecimal actualAmount,
      String reasonForPayment, HtmlPage loggedIn)
      throws IOException {
    loggedIn.getForms();

    HtmlForm transferForm = loggedIn.getForms().stream().skip(2).findFirst()
        .orElseThrow(() -> new RuntimeException("No transfer form"));

    //iterate all divs to find the ones with labels and input values
    transferForm.getElementsByTagName("div").stream()
        .filter(div -> div.hasAttribute("class") && div.getAttribute("class").contains("bline"))
        .forEach(
            div -> {
              if (div.getElementsByTagName("label").size() > 0) {
                var label = div.getElementsByTagName("label").get(0);
                if (label != null) {
                  String labelText = label.getTextContent();
                  if (labelText.contains("Begünstigter")) {
                    ((HtmlInput) div.getElementsByTagName("input").get(0)).setValueAttribute(
                        recipientName);
                  } else if (labelText.contains("IBAN")) {
                    ((HtmlInput) div.getElementsByTagName("input").get(0)).setValueAttribute(
                        recipientIban);
                  } else if (labelText.contains("Betrag")) {
                    ((HtmlInput) div.getElementsByTagName("input").get(0)).setValueAttribute(
                        actualAmount.toPlainString().replace(".", ","));
                  } else if (labelText.contains("Verwendungszweck")) {
                    ((HtmlTextArea) div.getElementsByTagName("textarea").get(0)).setText(
                        reasonForPayment);
                  }
                }
              }
            }
        );
    //submit the payment form
    HtmlPage confirmPage = transferForm.getInputByValue("Weiter").click();

    //get the confirmation page and submit the default form
    HtmlForm confirmationForm = confirmPage.getForms().stream().filter(
            f -> f.getActionAttribute() != null && f.getActionAttribute().contains("ueberweisung")
                && !f.hasAttribute("class")).findFirst()
        .orElseThrow(() -> new RuntimeException("No confirmation form"));
    HtmlPage verifyPage=confirmationForm.getInputByValue("Weiter").click();
//    try{
//       verifyPage =
//    }catch (Exception e){
//      return responseBuilder.get().ok(false).message("Cannot process request. Please try again in a bit").build();
//    }

    //parse the result of the final page to read the confirmation window
    return parseConfirmationDialog(actualAmount,verifyPage);

  }
  private RequestPaymentResponse parseConfirmationDialog(BigDecimal actualAmount,HtmlPage verifyPage){
    if (verifyPage.getBody().getElementsByTagName("div").stream().anyMatch(
        div -> div.hasAttribute("class") && div.getAttribute("class").contains("success-msg"))) {
      log.info("Transfer success");
      return responseBuilder.get().ok(true).message("Transfer success")
          .amount(actualAmount.toPlainString()).build();
    } else {
      var errorMessage = verifyPage.getBody().getElementsByTagName("div").stream().filter(
              div -> div.hasAttribute("class") && div.getAttribute("class").contains("msgerror"))
          .findFirst();
      if (errorMessage.isPresent()) {
        log.error("Cannot send with error message {}", errorMessage.get().getTextContent());
        return responseBuilder.get().ok(false).message(errorMessage.get().getTextContent()).build();
      } else {
        log.error("Cannot send with unknown error");
        return responseBuilder.get().ok(false).message("Cannot send with unknown error").build();
      }
    }
  }

  private void printBookingsFromLoggedInPage(HtmlPage loggedIn) {
    HtmlForm umsatzForm = loggedIn.getForms().stream().skip(2).findFirst()
        .orElseThrow(() -> new RuntimeException("No Umsatz form"));
    //umsatzForm.getAttributesMap().forEach((key, value) -> log.info(key + ":" + value.asXml()));
    HtmlSelect select = (HtmlSelect) umsatzForm.getElementsByTagName("select").stream().findFirst()
        .orElseThrow(() -> new RuntimeException("No select"));
    AtomicBoolean print = new AtomicBoolean(true);
    select.getOptions().forEach(option -> {
      if (!option.asXml().contains("--- Bitte auswählen ---") && print.get()) {
        select.setSelectedAttribute(option, true);
        try {
          HtmlPage myPage = umsatzForm.getElementsByTagName("input").stream()
              .filter(e -> e.getAttribute("title").equals("Aktualisieren"))
              .findFirst()
              .orElseThrow(() -> new RuntimeException("Cannot find button aktualisieren"))
              .click();

          DomElement downloadButton = myPage.getElementsByTagName("input").stream()
              .filter(e -> e.getAttribute("title").equals("CSV-CAMT-Format"))
              .findFirst().orElseThrow(() -> new RuntimeException("no download button"));
          //System.out.println(downloadButton.click().getUrl());

          WebResponse response = downloadButton.click(false, false, false, false, false, true,
              false).getWebResponse();

          response.getResponseHeaders()
              .forEach(pair -> log.info("{}:{}", pair.getName(), pair.getValue()));
          log.info("################");
          response.getWebRequest().getAdditionalHeaders()
              .forEach((key, value) -> log.info("{}:{}", key, value));
          log.info("################");
          log.info(response.getContentAsString());
          print.set(false);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }
}
