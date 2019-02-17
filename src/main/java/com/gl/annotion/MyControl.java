package com.gl.annotion;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented

public @interface MyControl {
   String value() default "";
}
