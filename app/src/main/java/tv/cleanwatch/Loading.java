package tv.cleanwatch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;

/**
 * Shown from the moment Play is pressed until the film plays: the title's picture slowly
 * drifting closer, its logo (or name), a line such as "S1 · E2", and a spinner.
 */
final class Loading {
    final FrameLayout view;
    private final ImageView picture;
    private boolean gone;

    Loading(Context c, String name, String line, String backdrop, String logoPath) {
        Ui.init(c);
        view = new FrameLayout(c);
        view.setBackgroundColor(Ui.BG);
        view.setClickable(true);

        picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        if (backdrop != null) {
            Glide.with(picture).load(Title.Img.backdrop(backdrop))
                    .format(DecodeFormat.PREFER_RGB_565).dontAnimate().into(picture);
            // A slow drift towards the viewer while the film starts.
            picture.animate().scaleX(1.08f).scaleY(1.08f).setDuration(20_000)
                    .setInterpolator(new LinearInterpolator()).start();
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{0xF2050505, 0xB3050505, 0x66050505}));
        view.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cp.leftMargin = Ui.dp(Ui.EDGE);
        cp.bottomMargin = Ui.dp(64);
        view.addView(col, cp);

        TextView title = Ui.lines(c, 30, Ui.TEXT, 2);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setText(name == null ? "" : name);
        title.setMaxWidth(Ui.dp(520));
        ImageView logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        if (logoPath != null) {
            col.addView(logo, new LinearLayout.LayoutParams(-2, -2));
            Glide.with(logo).load(Title.Img.logo(logoPath)).override(Ui.dp(320), Ui.dp(96)).fitCenter().into(logo);
        } else {
            col.addView(title, new LinearLayout.LayoutParams(-2, -2));
        }
        if (line != null && !line.isEmpty()) {
            TextView sub = Ui.text(c, 13, Ui.TEXT_2, false);
            sub.setText(line);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.topMargin = Ui.dp(10);
            col.addView(sub, sp);
        }

        LinearLayout status = new LinearLayout(c);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(c);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(0xFFFFFFFF));
        status.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
        TextView starting = Ui.text(c, 12.5f, Ui.TEXT_2, false);
        starting.setText(R.string.loading_film);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = Ui.dp(10);
        status.addView(starting, tp);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = Ui.dp(22);
        col.addView(status, stp);
        // The words breathe gently so the screen never looks frozen.
        starting.animate().alpha(0.45f).setDuration(900).withEndAction(new Runnable() {
            boolean dim = true;

            @Override
            public void run() {
                if (gone) return;
                dim = !dim;
                starting.animate().alpha(dim ? 0.45f : 1f).setDuration(900).withEndAction(this).start();
            }
        }).start();
    }

    void dismiss() {
        if (gone) {
            return;
        }
        gone = true;
        picture.animate().cancel();
        view.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeView(view);
            }
        }).start();
    }
}
