package com.chatsphere.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Slf4j
public class SmtpEmailService implements EmailService {

    /** Khớp với AuthTokenStore.OTP_TTL / RESET_TTL — chỉ dùng để hiển thị trong mail. */
    private static final int TTL_MINUTES = 15;
    private static final Locale VI = Locale.forLanguageTag("vi");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String from;
    private final String fromName;
    private final String frontendUrl;

    public SmtpEmailService(JavaMailSender mailSender,
                            TemplateEngine templateEngine,
                            @Value("${app.mail.from:no-reply@chatsphere.local}") String from,
                            @Value("${app.mail.from-name:ChatSphere}") String fromName,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
        this.fromName = fromName;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void sendVerificationOtp(String to, String displayName, String otp) {
        Context context = new Context(VI);
        context.setVariable("displayName", displayName);
        context.setVariable("otp", otp);
        context.setVariable("ttlMinutes", TTL_MINUTES);

        String plainText = """
                Chào %s,

                Mã xác thực tài khoản ChatSphere của bạn là: %s

                Mã có hiệu lực trong %d phút và chỉ dùng được một lần.
                Nếu bạn không đăng ký ChatSphere, hãy bỏ qua email này.
                """.formatted(displayName, otp, TTL_MINUTES);

        send(to, "[ChatSphere] Mã xác thực tài khoản",
                plainText, templateEngine.process("email/verification-otp", context));
    }

    @Override
    public void sendPasswordResetToken(String to, String displayName, String resetToken) {
        // Token là UUID nên hiện tại không có ký tự đặc biệt, nhưng vẫn encode: nếu sau này
        // đổi sang token Base64 (có '+' và '/') mà quên encode thì link sẽ hỏng âm thầm.
        String resetUrl = "%s/reset-password?token=%s".formatted(
                frontendUrl, URLEncoder.encode(resetToken, StandardCharsets.UTF_8));

        Context context = new Context(VI);
        context.setVariable("displayName", displayName);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("ttlMinutes", TTL_MINUTES);

        String plainText = """
                Chào %s,

                Nhấn vào liên kết sau để đặt lại mật khẩu:
                %s

                Liên kết có hiệu lực trong %d phút và chỉ dùng được một lần.
                Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
                """.formatted(displayName, resetUrl, TTL_MINUTES);

        send(to, "[ChatSphere] Đặt lại mật khẩu",
                plainText, templateEngine.process("email/password-reset", context));
    }

    /**
     * Gửi mail dạng multipart/alternative: bản HTML cho client hiện đại, bản text thuần cho
     * client chặn HTML — client tự chọn bản nào hiển thị. Chỉ gửi HTML sẽ khiến một số bộ lọc
     * spam hạ điểm vì thiếu phần text tương ứng.
     */
    private void send(String to, String subject, String plainText, String htmlText) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            // Địa chỉ + tên hiển thị. Với Gmail, `from` BẮT BUỘC trùng tài khoản SMTP đang
            // đăng nhập (spring.mail.username), nếu không Gmail từ chối hoặc tự ghi đè.
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, htmlText); // (text, html) — thứ tự này là bắt buộc

            mailSender.send(message);
        } catch (Exception e) {
            // Cố ý bắt Exception rộng, không chỉ MailException/MessagingException: hàm này
            // chạy trong luồng @Async — bất kỳ exception nào thoát ra khỏi đây (kể cả
            // RuntimeException không lường trước từ JavaMailSender) sẽ bị SimpleAsyncUncaughtExceptionHandler
            // nuốt và chỉ log, KHÔNG bao giờ quay lại được để ảnh hưởng luồng đăng ký/quên mật khẩu
            // đang chạy — nhưng phải tự log ở đây, nếu không thì mất dấu vết hoàn toàn.
            log.error("Gửi email thất bại, subject={}", subject, e);
        }
    }
}
