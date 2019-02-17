package com.gl.annotion;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented

public @interface MyAutoGl {
    String value() default "";

}


