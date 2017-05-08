package com.xiaoooyu.annotation_processor.annotationprocessdemoapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.xiaoooyu.annotationprocessor.processor.CustomAnnotation;

@CustomAnnotation
public class MainActivity extends AppCompatActivity {

    @CustomAnnotation
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
