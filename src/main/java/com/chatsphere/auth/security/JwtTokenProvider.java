package com.chatsphere.auth.security;

import com.chatsphere.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final int REFRESH_TOKEN_BYTES = 32; // 256 bit entropy

    private final SecretKey key;
    private final long accessExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenProvider(JwtProperties props) {
        // Keys.hmacShaKeyFor ném WeakKeyException nếu < 256 bit → fail-fast, không cho chạy với secret yếu.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
        this.accessExpirationMs = props.accessExpirationMs();
    }

    public String generateAccessToken(UUID userId, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Parse + verify chữ ký + check hạn. Ném {@link JwtException} nếu token hỏng/hết hạn/sai chữ ký.
     * <p><b>Dùng chung</b>: REST {@code JwtAuthenticationFilter} VÀ WebSocket STOMP {@code CONNECT}
     * interceptor (Phase 4) đều gọi hàm này — không phụ thuộc {@code HttpServletRequest}.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    /**
     * KHÔNG phải JWT: 32 byte ngẫu nhiên, base64url. AuthService (§1.4) sẽ hash SHA-256 rồi mới lưu DB.
     * Xem phần "Vì sao" bên dưới để hiểu tại sao access dùng JWT còn refresh thì không.
     */
    public String generateRefreshToken() {
        byte[] buf = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}