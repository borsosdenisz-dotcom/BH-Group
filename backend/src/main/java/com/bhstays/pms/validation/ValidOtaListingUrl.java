package com.bhstays.pms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = OtaListingUrlConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOtaListingUrl {
    String message() default "URL-ul trebuie să fie HTTPS și să folosească domeniul oficial al platformei";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    OtaPlatform platform();
}
