package com.chatsphere.email;

import com.chatsphere.auth.event.EmailVerificationRequestedEvent;
import com.chatsphere.auth.event.PasswordResetRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailService emailService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmailVerificationRequestedEvent event) {
        emailService.sendVerificationOtp(event.email(), event.displayName(), event.otp());
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PasswordResetRequestedEvent event) {
        emailService.sendPasswordResetToken(event.email(), event.displayName(), event.resetToken());
    }
}