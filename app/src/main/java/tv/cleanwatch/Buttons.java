package tv.cleanwatch;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The pill buttons (Play, My List, Details). The focused one turns white and grows a little. */
final class Buttons {
    private Buttons() {
    }

    static TextView make(Context c, String label, boolean primary,
                         View.OnClickListener click, View.OnFocusChangeListener focus) {
        TextView b = Ui.text(c, 13, Ui.TEXT, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setMinWidth(Ui.dp(primary ? 120 : 0));
        b.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(22), Ui.dp(10));
        paint(b, false, primary);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, has) -> {
            paint(b, has, primary);
            Ui.zoom(b, has, 1.06f);
            if (focus != null) {
                focus.onFocusChange(v, has);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    static void setLabel(TextView b, String label) {
        b.setText(label);
    }

    private static void paint(TextView b, boolean focused, boolean primary) {
        int fill = focused ? 0xFFFFFFFF : (primary ? 0x59FFFFFF : 0x33FFFFFF);
        b.setBackground(Ui.pill(fill, 22));
        b.setTextColor(focused ? 0xFF000000 : Ui.TEXT);
    }
}
