package com.chatsphere.email;

public interface EmailService {

    void sendVerificationOtp(String to, String displayName, String otp);

    void sendPasswordResetToken(String to, String displayName, String resetToken);
}
