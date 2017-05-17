package com.xiaoooyu.annotationprocessor.processor;

/**
 * Copyright (c) 2017 Teambition All Rights Reserved.
 */
public @interface TraceClick {
    int id() default -1;
    int page() default -1;
    int event() default -1;
}
