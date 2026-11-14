package tv.cleanwatch;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The large slide at the top of Home, Movies and Shows, laid out like cinejoy's: the backdrop,
 * the title's logo, rating, year and genre, "#N on Cinejoy today", the synopsis, Play and the
 * + / info buttons, and the slide dots. Slides change by themselves while the hero is in view;
 * Left on Play and Right on info change them by hand.
 *
 * The backdrop and its fades are one GPU layer behind the whole screen, so moving through the
 * rows below never repaints them.
 */
final class Hero {
    interface Actions {
        void play(Title t);

        void toggleList(Title t);

        void info(Title t);
    }

    static final int HEIGHT = 468;
    private static final long SLIDE_MS = 9000;

    final View view;
    private final Context context;
    private final Actions actions;
    private final ImageView backdrop;
    private final View dim;
    private final ImageView logo;
    private final TextView name;
    private final LinearLayout meta;
    private final LinearLayout badge;
    private final TextView badgeText;
    private final TextView overview;
    private final LinearLayout dots;
    private final TextView play;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Title> slides = new ArrayList<>();
    private final Map<String, Integer> ranks = new HashMap<>();
    private int index;
    private boolean active = true;
    private String shownBackdrop;
    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            if (active && slides.size() > 1) {
                show(index + 1);
            }
        }
    };

    Hero(Context c, FrameLayout root, Actions actions) {
        this.context = c;
        this.actions = actions;

        // ---- backdrop layer (behind everything on the screen)
        FrameLayout stage = new FrameLayout(c);
        stage.setBackgroundColor(Ui.BG);
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View left = new View(c);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD9050505, 0x99050505, 0x26050505, 0x00050505}));
        stage.addView(left, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00050505, 0x00050505, 0x33050505, 0xD9050505, 0xFF050505}));
        stage.addView(bottom, new FrameLayout.LayoutParams(-1, -1));
        dim = new View(c);
        dim.setBackgroundColor(0xFF050505);
        dim.setAlpha(0f);
        stage.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, 0, new FrameLayout.LayoutParams(-1, -1));

        // ---- the slide itself (first row of the list)
        FrameLayout box = new FrameLayout(c);
        box.setClipChildren(false);
        box.setLayoutParams(new FrameLayout.LayoutParams(-1, Ui.dp(HEIGHT)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.dp(420), -2);
        cp.leftMargin = Ui.dp(Ui.EDGE);
        cp.topMargin = Ui.dp(96);
        box.addView(col, cp);

        FrameLayout logoBox = new FrameLayout(c);
        logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logoBox.addView(logo, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        name = Ui.lines(c, 30, Ui.TEXT, 2);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        logoBox.addView(name, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START));
        col.addView(logoBox, new LinearLayout.LayoutParams(Ui.dp(340), Ui.dp(112)));

        meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = Ui.dp(14);
        col.addView(meta, mp);

        badge = new LinearLayout(c);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(c);
        mark.setBackground(Ui.pill(0xFFFFFFFF, 3));
        ImageView markLogo = new ImageView(c);
        markLogo.setImageResource(R.drawable.cinejoy_logo);
        markLogo.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
        mark.addView(markLogo, new FrameLayout.LayoutParams(Ui.dp(11), Ui.dp(11), Gravity.CENTER));
        badge.addView(mark, new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        badgeText = Ui.text(c, 11.5f, Ui.TEXT_2, false);
        LinearLayout.LayoutParams bt = new LinearLayout.LayoutParams(-2, -2);
        bt.leftMargin = Ui.dp(7);
        badge.addView(badgeText, bt);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = Ui.dp(11);
        col.addView(badge, bp);

        overview = Ui.lines(c, 12, Ui.TEXT, 4);
        overview.setLineSpacing(Ui.dp(3), 1f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(Ui.dp(380), -2);
        op.topMargin = Ui.dp(16);
        col.addView(overview, op);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        play = Ui.text(c, 12.5f, 0xFF000000, true);
        play.setText("Play");
        play.setGravity(Gravity.CENTER);
        play.setFocusable(true);
        play.setCompoundDrawablePadding(Ui.dp(7));
        android.graphics.drawable.Drawable pi = c.getDrawable(R.drawable.ic_play);
        if (pi != null) {
            pi.setBounds(0, 0, Ui.dp(12), Ui.dp(12));
            play.setCompoundDrawables(pi, null, null, null);
        }
        play.setPadding(Ui.dp(22), 0, Ui.dp(24), 0);
        play.setSingleLine(true);
        play.setBackground(Ui.pill(0xFFFFFFFF, 17));
        play.setOnFocusChangeListener((v, has) -> Ui.zoom(play, has, 1.06f));
        play.setOnClickListener(v -> act(0));
        play.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && e.getAction() == KeyEvent.ACTION_DOWN) {
                show(index - 1);
                return true;
            }
            return false;
        });
        buttons.addView(play, new LinearLayout.LayoutParams(-2, Ui.dp(33)));

        LinearLayout group = new LinearLayout(c);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setBackground(Ui.pill(0x66101418, 17, 0x40FFFFFF));
        View add = roundIcon(c, R.drawable.ic_plus, () -> act(1), false);
        View info = roundIcon(c, R.drawable.ic_info, () -> act(2), true);
        group.addView(add);
        View divider = new View(c);
        divider.setBackgroundColor(0x4DFFFFFF);
        group.addView(divider, new LinearLayout.LayoutParams(Ui.dp(1), Ui.dp(15)));
        group.addView(info);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(33));
        gp.leftMargin = Ui.dp(8);
        buttons.addView(group, gp);
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(-2, -2);
        btp.topMargin = Ui.dp(20);
        col.addView(buttons, btp);

        dots = new LinearLayout(c);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-2, Ui.dp(8), Gravity.END | Gravity.TOP);
        dp.rightMargin = Ui.dp(Ui.EDGE);
        dp.topMargin = Ui.dp(373);
        box.addView(dots, dp);

        view = box;
    }

    /** A round icon button inside the + / info pill; the focused one turns white. */
    private View roundIcon(Context c, int res, Runnable click, boolean nextOnRight) {
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        b.addView(i, new FrameLayout.LayoutParams(Ui.dp(17), Ui.dp(17), Gravity.CENTER));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(has ? Ui.pill(0xFFFFFFFF, 15) : null);
            i.setColorFilter(has ? 0xFF000000 : 0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
            Ui.zoom(b, has, 1.06f);
        });
        b.setOnClickListener(v -> click.run());
        if (nextOnRight) {
            b.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == KeyEvent.ACTION_DOWN) {
                    show(index + 1);
                    return true;
                }
                return false;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(31));
        lp.leftMargin = Ui.dp(1);
        lp.rightMargin = Ui.dp(1);
        b.setLayoutParams(lp);
        return b;
    }

    private void act(int which) {
        Title t = current();
        if (t == null) return;
        if (which == 0) actions.play(t);
        else if (which == 1) actions.toggleList(t);
        else actions.info(t);
    }

    View firstButton() {
        return play;
    }

    Title current() {
        return slides.isEmpty() ? null : slides.get(index);
    }

    void setSlides(List<Title> list) {
        slides.clear();
        for (Title t : list) {
            if (t.backdrop != null) slides.add(t);
            if (slides.size() == 7) break;
        }
        dots.removeAllViews();
        for (int i = 0; i < slides.size(); i++) {
            View d = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(5), Ui.dp(5));
            lp.leftMargin = Ui.dp(5);
            dots.addView(d, lp);
        }
        show(0);
    }

    void setRanks(List<Title> top10) {
        ranks.clear();
        if (top10 != null) {
            for (Title t : top10) ranks.put(t.key(), t.rank);
        }
        paintBadge();
    }

    /** True while the hero is the focused row: slides then change by themselves. */
    void setActive(boolean on) {
        if (on == active) return;
        active = on;
        dim.setAlpha(on ? 0f : 0.55f);   // one repaint of the backdrop layer, no animation
        handler.removeCallbacks(advance);
        if (on) handler.postDelayed(advance, SLIDE_MS);
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
    }

    private void show(int i) {
        if (slides.isEmpty()) return;
        index = (i % slides.size() + slides.size()) % slides.size();
        Title t = slides.get(index);
        name.setText(t.name);
        overview.setText(t.overview);
        paintMeta(t);
        paintBadge();
        for (int k = 0; k < dots.getChildCount(); k++) {
            View d = dots.getChildAt(k);
            boolean on = k == index;
            d.setBackground(Ui.pill(on ? 0xFFFFFFFF : 0x80FFFFFF, 3));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = Ui.dp(on ? 19 : 5);
            d.setLayoutParams(lp);
        }
        loadBackdrop(t.backdrop);
        showLogo(t);
        if (!t.detailed) {
            Api.details(t, d -> {
                if (current() == t) {
                    paintMeta(t);
                    showLogo(t);
                }
            });
        }
        handler.removeCallbacks(advance);
        if (active) handler.postDelayed(advance, SLIDE_MS);
        // Have the next slide's details ready before it shows.
        Title next = slides.get((index + 1) % slides.size());
        if (!next.detailed) Api.details(next, d -> { });
    }

    private void showLogo(Title t) {
        if (t.logo != null) {
            name.setVisibility(View.GONE);
            logo.setVisibility(View.VISIBLE);
            Glide.with(logo).load(Title.Img.logo(t.logo))
                    .override(Ui.dp(340), Ui.dp(112)).fitCenter().into(logo);
        } else {
            Glide.with(logo).clear(logo);
            logo.setVisibility(View.GONE);
            name.setVisibility(View.VISIBLE);
        }
    }

    private void paintMeta(Title t) {
        meta.removeAllViews();
        if (t.rating > 0) {
            metaItem(R.drawable.ic_star, String.format(Locale.US, "%.1f", t.rating));
        }
        if (!t.year.isEmpty()) {
            metaItem(R.drawable.ic_calendar, t.year);
        }
        if (!t.genres.isEmpty()) {
            String genre = t.genres.split(",")[0].trim();
            metaItem("Adventure".equals(genre) ? R.drawable.ic_globe : R.drawable.ic_film, genre);
        }
    }

    private void metaItem(int icon, String text) {
        if (meta.getChildCount() > 0) {
            TextView sep = Ui.text(context, 11, Ui.TEXT_3, false);
            sep.setText("•");
            sep.setPadding(Ui.dp(9), 0, Ui.dp(9), 0);
            meta.addView(sep);
        }
        meta.addView(Cards.icon(context, icon, 12, Ui.TEXT));
        TextView v = Ui.text(context, 11.5f, Ui.TEXT, false);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setText(text);
        v.setPadding(Ui.dp(5), 0, 0, 0);
        meta.addView(v);
    }

    private void paintBadge() {
        Title t = current();
        Integer rank = t == null ? null : ranks.get(t.key());
        if (rank == null || rank <= 0) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setVisibility(View.VISIBLE);
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder();
        s.append("#").append(String.valueOf(rank));
        s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, s.length(), 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF), 0, s.length(), 0);
        s.append(" on Cinejoy today");
        badgeText.setText(s);
    }

    private void loadBackdrop(String path) {
        if (path == null || path.equals(shownBackdrop)) return;
        shownBackdrop = path;
        Glide.with(backdrop)
                .load(Title.Img.backdrop(path))
                .format(DecodeFormat.PREFER_RGB_565)
                .dontAnimate()   // a cross-fade repaints the whole screen for every frame of it
                .into(backdrop);
    }
}
