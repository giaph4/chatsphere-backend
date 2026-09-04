package com.chatsphere.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.mail.from:no-reply@chatsphere.local}") String from,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void sendVerificationOtp(String to, String displayName, String otp) {
        send(to, "[ChatSphere] Mã xác thực tài khoản", """
                Chào %s,

                Mã xác thực tài khoản ChatSphere của bạn là: %s

                Mã có hiệu lực trong 15 phút. Nếu bạn không đăng ký ChatSphere, hãy bỏ qua email này.
                """.formatted(displayName, otp));
    }

    @Override
    public void sendPasswordResetToken(String to, String displayName, String resetToken) {
        send(to, "[ChatSphere] Đặt lại mật khẩu", """
                Chào %s,

                Nhấn vào liên kết sau để đặt lại mật khẩu:
                %s/reset-password?token=%s

                Liên kết có hiệu lực trong 15 phút và chỉ dùng được một lần.
                Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
                """.formatted(displayName, frontendUrl, resetToken));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // SMTP hỏng không được làm sập nghiệp vụ đăng ký/quên mật khẩu đang chạy.
            // Log lại để còn lần theo; user có thể yêu cầu gửi lại mã.
            log.error("Gửi email thất bại, subject={}", subject, e);
        }
    }
}
