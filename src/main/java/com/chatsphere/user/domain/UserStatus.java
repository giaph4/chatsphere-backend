package com.chatsphere.user.domain;

public enum UserStatus {

    PENDING_VERIFICATION, // vừa đăng ký, chưa xác thực email → chưa cho đăng nhập
    ACTIVE,               // đã xác thực email, dùng bình thường
    LOCKED,               // bị khóa (brute-force hoặc admin khóa)
    DEACTIVATED           // user tự vô hiệu hóa tài khoản
}
