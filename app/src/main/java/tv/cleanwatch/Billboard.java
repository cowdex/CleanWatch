package tv.cleanwatch;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;

/**
 * The big picture and text at the top of a screen: the backdrop, the title's logo (or its
 * name), a line of facts and the synopsis. It follows the focused card. The text changes at
 * once; the picture and logo wait until the remote rests, so holding an arrow stays smooth.
 */
final class Billboard {
    private static final long SETTLE_MS = 260;

    final ImageView backdrop;
    private final FrameLayout stage;
    final LinearLayout info;
    private final ImageView logo;
    private final TextView name;
    private final TextView meta;
    private final TextView overview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Title current;
    private String shownBackdrop;
    private String shownLogo;

    Billboard(Context c, FrameLayout root, int overviewLines) {
        // The picture and its two fades are drawn once into a GPU layer and reused for every
        // frame, so moving through the rows doesn't repaint three full-screen layers each time
        // (the TV's graphics chip can't keep up with that).
        stage = new FrameLayout(c);
        stage.setBackgroundColor(Ui.BG);
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        // Fade the picture into the background on the left and at the bottom.
        View left = new View(c);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF5050505, 0xC8050505, 0x40050505, 0x00050505}));
        stage.addView(left, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00050505, 0x00050505, 0xB0050505, 0xFF050505}));
        stage.addView(bottom, new FrameLayout.LayoutParams(-1, -1));

        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);

        logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setMaxHeight(Ui.dp(84));
        logo.setMaxWidth(Ui.dp(330));
        info.addView(logo, new LinearLayout.LayoutParams(-2, -2));

        name = Ui.lines(c, 30, Ui.TEXT, 2);
        name.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        info.addView(name, new LinearLayout.LayoutParams(Ui.dp(440), -2));

        meta = Ui.lines(c, 12, Ui.TEXT_2, 1);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = Ui.dp(12);
        info.addView(meta, mp);

        overview = Ui.lines(c, 12.5f, Ui.TEXT_2, overviewLines);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(Ui.dp(430), -2);
        op.topMargin = Ui.dp(10);
        info.addView(overview, op);
    }

    Title current() {
        return current;
    }

    void show(Title t, boolean now) {
        if (t == null) {
            return;
        }
        boolean same = current != null && current.key().equals(t.key());
        current = t;
        if (!same) {
            name.setText(t.name);
            meta.setText(t.meta());
            overview.setText(t.overview);
            // Keep the old logo/picture until the new ones are ready; hide a logo that no longer matches.
            if (t.logo == null || !t.logo.equals(shownLogo)) {
                logo.setVisibility(View.GONE);
                name.setVisibility(View.VISIBLE);
            }
        }
        handler.removeCallbacksAndMessages(null);
        if (now) {
            settle(t);
        } else {
            handler.postDelayed(() -> settle(t), SETTLE_MS);
        }
    }

    private void settle(Title t) {
        if (t.detailed) {
            paint(t);
            return;
        }
        loadBackdrop(t.backdrop);   // what we have now; details may bring a better one
        Api.details(t, d -> {
            if (current == t) {
                meta.setText(t.meta());
                if (overview.length() == 0) {
                    overview.setText(t.overview);
                }
                paint(t);
            }
        });
    }

    private void paint(Title t) {
        loadBackdrop(t.backdrop);
        if (t.logo != null) {
            if (!t.logo.equals(shownLogo)) {
                shownLogo = t.logo;
                // A fixed box, not the size of the previous logo: logos range from wide to square.
                Glide.with(logo).load(Title.Img.logo(t.logo))
                        .override(Ui.dp(330), Ui.dp(84)).fitCenter().into(logo);
            }
            logo.setVisibility(View.VISIBLE);
            name.setVisibility(View.GONE);
        }
    }

    private void loadBackdrop(String path) {
        if (path == null || path.equals(shownBackdrop)) {
            return;
        }
        shownBackdrop = path;
        Glide.with(backdrop)
                .load(Title.Img.backdrop(path))
                .format(DecodeFormat.PREFER_RGB_565)
                .dontAnimate()   // a cross-fade repaints the whole screen for every frame of it
                .into(backdrop);
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
    }
}
