package com.soda.helper;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DonateActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 247, 248));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("支持作者");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(23, 23, 23));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("如果这个工具帮你省了时间，可以请我喝杯奶茶");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(82, 82, 91));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(card);

        ImageView qr = new ImageView(this);
        qr.setImageResource(R.drawable.donate_qr);
        qr.setAdjustViewBounds(true);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(qr, new LinearLayout.LayoutParams(dp(260), dp(260)));

        TextView hint = new TextView(this);
        hint.setText("微信扫码赞赏");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(82, 82, 91));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, 0);
        card.addView(hint);

        TextView note = new TextView(this);
        note.setText("赞赏完全自愿，不影响任何功能。\n感谢你让这个工具继续更新。");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(130, 130, 140));
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backParams.topMargin = dp(20);
        root.addView(back, backParams);

        return scroll;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
