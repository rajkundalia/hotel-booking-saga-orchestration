package org.example.bookingservice.feignclient;

import org.example.common.command.AuthorizePaymentCommand;
import org.example.common.command.CancelPaymentCommand;
import org.example.common.command.CommandResult;
import org.example.common.dto.PaymentAuthorizationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${services.payment.url:http://localhost:8082}")
public interface PaymentServiceClient {

    @PostMapping("/api/payment/authorize")
    CommandResult<PaymentAuthorizationDto> authorizePayment(@RequestBody AuthorizePaymentCommand command);

    @PostMapping("/api/payment/cancel")
    CommandResult<Void> cancelPayment(@RequestBody CancelPaymentCommand command);
}