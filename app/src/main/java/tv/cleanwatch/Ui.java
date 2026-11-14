package tv.cleanwatch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

/** Small helpers for building the screens in code. Sizes are in dp for a 960 x 540 dp TV. */
final class Ui {
    static final int BG = 0xFF050505;
    static final int TEXT = Color.WHITE;
    static final int TEXT_2 = 0xCCFFFFFF;
    static final int TEXT_3 = 0x99FFFFFF;
    static final int SURFACE = 0x26FFFFFF;
    static final int LINE = 0x33FFFFFF;

    /** Left edge of all content, the same on every screen. */
    static final int EDGE = 41;

    private static float density = 2f;

    private Ui() {
    }

    static void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        return t;
    }

    static TextView lines(Context c, float sp, int color, int maxLines) {
        TextView t = text(c, sp, color, false);
        t.setMaxLines(maxLines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    static GradientDrawable pill(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable pill(int fill, float radiusDp, int stroke) {
        GradientDrawable d = pill(fill, radiusDp);
        d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    /** Rounded corners drawn by the GPU (no extra bitmap work while scrolling). */
    static void round(View v, float radiusDp) {
        final int r = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** The zoom a focused card gets; the same timing everywhere so the app feels consistent. */
    static void zoom(View v, boolean focused, float scale) {
        float s = focused ? scale : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(140).start();
    }
}
