package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Bundle;

public class ManualActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);
        findViewById(R.id.manualBackButton).setOnClickListener(view -> finish());
        setImageClickListeners(findViewById(android.R.id.content));
    }

    private void setImageClickListeners(View view) {
        if (view instanceof ImageView) {
            view.setOnClickListener(this::showZoomedImage);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setImageClickListeners(group.getChildAt(i));
            }
        }
    }

    private void showZoomedImage(View view) {
        ImageView source = (ImageView) view;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);

        ZoomableImageView image = new ZoomableImageView(this);
        image.setImageDrawable(source.getDrawable());
        content.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView closeButton = new TextView(this);
        closeButton.setText("닫기");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(16);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setPadding(20, 14, 20, 14);
        closeButton.setBackgroundColor(0x99000000);
        closeButton.setOnClickListener(button -> dialog.dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, 24, 16, 0);
        content.addView(closeButton, closeParams);

        dialog.setContentView(content);
        dialog.show();
    }
}
