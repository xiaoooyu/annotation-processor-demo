package com.xiaoooyu.annotation_processor.annotationprocessdemoapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.xiaoooyu.annotationprocessor.processor.CustomAnnotation;
import com.xiaoooyu.annotationprocessor.processor.TraceClick;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

@CustomAnnotation
public class MainActivity extends AppCompatActivity {


    @BindView(R.id.button) Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
    }

    @OnClick(R.id.button)
    @CustomAnnotation
    @TraceClick(id = R.id.button, page = R.string.page, event = R.string.event)
    public void onButtonClick() {
        Toast.makeText(this, "ABC", Toast.LENGTH_LONG).show();
    }
}
