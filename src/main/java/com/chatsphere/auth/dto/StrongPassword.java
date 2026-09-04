package com.chatsphere.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@NotBlank
@Size(min = 8, max = 72)
@Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
        message = "Mật khẩu phải có tối thiểu 1 chữ hoa và 1 chữ số"
)
public @interface StrongPassword {
    String message() default "Mật khẩu không hợp lệ";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}