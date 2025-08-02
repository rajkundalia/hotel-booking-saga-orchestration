package org.example.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.command.AuthorizePaymentCommand;
import org.example.common.command.CancelPaymentCommand;
import org.example.common.command.CommandResult;
import org.example.common.dto.PaymentAuthorizationDto;
import org.example.paymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/authorize")
    public ResponseEntity<CommandResult<PaymentAuthorizationDto>> authorizePayment(
            @RequestBody AuthorizePaymentCommand command) {
        CommandResult<PaymentAuthorizationDto> result = paymentService.authorizePayment(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<CommandResult<Void>> cancelPayment(
            @RequestBody CancelPaymentCommand command) {
        CommandResult<Void> result = paymentService.cancelPayment(command);
        return ResponseEntity.ok(result);
    }
}