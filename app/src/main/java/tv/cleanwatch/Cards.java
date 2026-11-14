package tv.cleanwatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cards in a row, sized like cinejoy's: posters (optionally ranked, Top 10 style),
 * Continue Watching cards, provider tiles, episodes and season chips. A focused card grows and,
 * like cinejoy's hover, shows a play button and the title over the poster.
 */
final class Cards {
    static final int POSTER_W = 127;
    static final int POSTER_H = 190;
    static final float ZOOM = 1.08f;

    interface OnTitle {
        void focused(Title t);

        void clicked(Title t);
    }

    private Cards() {
    }

    static void image(ImageView v, String url, int wDp, int hDp) {
        Glide.with(v)
                .load(url)
                .format(DecodeFormat.PREFER_RGB_565)   // half the memory; these have no transparency
                .override(Ui.dp(wDp), Ui.dp(hDp))
                .centerCrop()
                .into(v);
    }

    static ImageView icon(Context c, int res, int sizeDp, int color) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        return i;
    }

    // ------------------------------------------------------------- posters

    static final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.VH> {
        final List<Title> items = new ArrayList<>();
        private final OnTitle listener;
        private final boolean ranked;

        PosterAdapter(OnTitle listener, boolean ranked) {
            this.listener = listener;
            this.ranked = ranked;
            setHasStableIds(true);
        }

        void set(List<Title> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        void add(List<Title> list) {
            int start = items.size();
            items.addAll(list);
            notifyItemRangeInserted(start, list.size());
        }

        @Override
        public int getItemViewType(int position) {
            return ranked ? 11 : 10;   // rows share one view pool, so each card kind has its own type
        }

        @Override
        public long getItemId(int position) {
            Title t = items.get(position);
            return ((long) t.id << 1) | (t.isTv() ? 1 : 0);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setFocusable(true);
            card.setClipChildren(false);

            OutlineNumber number = null;
            if (ranked) {
                number = new OutlineNumber(c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(76), Ui.dp(POSTER_H));
                lp.rightMargin = -Ui.dp(10);   // the poster overlaps the number a little
                card.addView(number, lp);
            }
            FrameLayout frame = new FrameLayout(c);
            frame.setBackground(Ui.pill(0xFF1A1A1A, 9));
            Ui.round(frame, 9);
            ImageView image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView fallback = Ui.lines(c, 12, Ui.TEXT_2, 4);
            fallback.setGravity(Gravity.CENTER);
            fallback.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
            frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

            // cinejoy's hover: darkened poster, a play button, the title, year and rating.
            LinearLayout veil = new LinearLayout(c);
            veil.setOrientation(LinearLayout.VERTICAL);
            veil.setGravity(Gravity.CENTER);
            veil.setBackgroundColor(0x8C000000);
            veil.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
            veil.setVisibility(View.GONE);
            FrameLayout playCircle = new FrameLayout(c);
            playCircle.setBackground(Ui.pill(0xFFFFFFFF, 20));
            ImageView play = new ImageView(c);
            play.setImageResource(R.drawable.ic_play);
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Ui.dp(15), Ui.dp(15), Gravity.CENTER);
            pp.leftMargin = Ui.dp(2);
            playCircle.addView(play, pp);
            veil.addView(playCircle, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
            TextView name = Ui.lines(c, 11.5f, Ui.TEXT, 2);
            name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Ui.dp(10);
            veil.addView(name, np);
            TextView info = Ui.lines(c, 10, Ui.TEXT_2, 1);
            info.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
            ip.topMargin = Ui.dp(4);
            veil.addView(info, ip);
            frame.addView(veil, new FrameLayout.LayoutParams(-1, -1));

            card.addView(frame, new LinearLayout.LayoutParams(Ui.dp(POSTER_W), Ui.dp(POSTER_H)));
            card.setLayoutParams(new RecyclerView.LayoutParams(-2, Ui.dp(POSTER_H)));
            return new VH(card, frame, image, fallback, number, veil, name, info);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Title t = items.get(position);
            h.title = t;
            h.fallback.setText(t.name);
            h.fallback.setVisibility(t.poster == null ? View.VISIBLE : View.GONE);
            h.name.setText(t.name);
            h.info.setText(t.year + (t.rating > 0 ? "   ★ " + String.format(Locale.US, "%.1f", t.rating) : ""));
            h.veil.setVisibility(h.itemView.hasFocus() ? View.VISIBLE : View.GONE);
            if (h.number != null) {
                h.number.setNumber(t.rank > 0 ? t.rank : position + 1);
            }
            image(h.image, Title.Img.poster(t.poster), POSTER_W, POSTER_H);
        }

        @Override
        public void onViewRecycled(@NonNull VH h) {
            Glide.with(h.image).clear(h.image);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView fallback;
            final OutlineNumber number;
            final View veil;
            final TextView name;
            final TextView info;
            Title title;

            VH(View card, View frame, ImageView image, TextView fallback, OutlineNumber number,
               View veil, TextView name, TextView info) {
                super(card);
                this.image = image;
                this.fallback = fallback;
                this.number = number;
                this.veil = veil;
                this.name = name;
                this.info = info;
                card.setOnFocusChangeListener((v, has) -> {
                    Ui.zoom(frame, has, ZOOM);
                    veil.setVisibility(has ? View.VISIBLE : View.GONE);
                    if (has && title != null) {
                        listener.focused(title);
                    }
                });
                card.setOnClickListener(v -> {
                    if (title != null) {
                        listener.clicked(title);
                    }
                });
            }
        }
    }

    /** The big outlined rank beside a Top 10 poster. */
    static final class OutlineNumber extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";

        OutlineNumber(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(1.6f));
            paint.setColor(0xCCFFFFFF);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.RIGHT);
        }

        void setNumber(int n) {
            text = String.valueOf(n);
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float size = getHeight() * (text.length() > 1 ? 0.6f : 0.8f);
            paint.setTextSize(size);
            canvas.drawText(text, getWidth() - Ui.dp(2), getHeight() * 0.92f, paint);
        }
    }

    // ----------------------------------------------------- continue watching

    interface OnResume {
        void play(Progress.Item item);

        void remove(Progress.Item item);
    }

    static final class ResumeAdapter extends RecyclerView.Adapter<ResumeAdapter.VH> {
        static final int W = 183;
        static final int H = 103;
        final List<Progress.Item> items = new ArrayList<>();
        private final OnResume listener;
        boolean editing;

        ResumeAdapter(OnResume listener) {
            this.listener = listener;
        }

        void set(List<Progress.Item> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        void setEditing(boolean on) {
            editing = on;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return 40;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClipChildren(false);

            FrameLayout frame = new FrameLayout(c);
            frame.setBackground(Ui.pill(0xFF1A1A1A, 9));
            Ui.round(frame, 9);
            ImageView image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            // Progress bar along the bottom of the picture.
            FrameLayout track = new FrameLayout(c);
            track.setBackgroundColor(0x40FFFFFF);
            View bar = new View(c);
            bar.setBackgroundColor(0xFFFFFFFF);
            track.addView(bar, new FrameLayout.LayoutParams(0, -1));
            frame.addView(track, new FrameLayout.LayoutParams(-1, Ui.dp(2.5f), Gravity.BOTTOM));
            // Edit mode: a remove mark.
            FrameLayout remove = new FrameLayout(c);
            remove.setBackgroundColor(0x99000000);
            FrameLayout x = new FrameLayout(c);
            x.setBackground(Ui.pill(0xFFFFFFFF, 16));
            ImageView xi = new ImageView(c);
            xi.setImageResource(R.drawable.ic_close);
            xi.setColorFilter(0xFF000000, PorterDuff.Mode.SRC_IN);
            x.addView(xi, new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.CENTER));
            remove.addView(x, new FrameLayout.LayoutParams(Ui.dp(32), Ui.dp(32), Gravity.CENTER));
            remove.setVisibility(View.GONE);
            frame.addView(remove, new FrameLayout.LayoutParams(-1, -1));
            card.addView(frame, new LinearLayout.LayoutParams(Ui.dp(W), Ui.dp(H)));

            TextView name = Ui.lines(c, 11.5f, Ui.TEXT, 1);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(W), -2);
            lp.topMargin = Ui.dp(10);
            lp.leftMargin = Ui.dp(2);
            card.addView(name, lp);
            TextView left = Ui.lines(c, 10.5f, Ui.TEXT_3, 1);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(Ui.dp(W), -2);
            lp2.topMargin = Ui.dp(5);
            lp2.leftMargin = Ui.dp(2);
            card.addView(left, lp2);
            card.setLayoutParams(new RecyclerView.LayoutParams(Ui.dp(W), -2));
            return new VH(card, frame, image, bar, remove, name, left);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Progress.Item it = items.get(position);
            h.item = it;
            h.name.setText(it.title.name);
            h.left.setText(it.left());
            h.remove.setVisibility(editing ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) h.bar.getLayoutParams();
            bp.width = Math.round(Ui.dp(W) * it.fraction());
            h.bar.setLayoutParams(bp);
            String url = it.backdropUrl != null ? it.backdropUrl : Title.Img.backdrop(it.title.backdrop);
            image(h.image, url, W, H);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView image;
            final View bar;
            final View remove;
            final TextView name;
            final TextView left;
            Progress.Item item;

            VH(View card, View frame, ImageView image, View bar, View remove, TextView name, TextView left) {
                super(card);
                this.image = image;
                this.bar = bar;
                this.remove = remove;
                this.name = name;
                this.left = left;
                card.setOnFocusChangeListener((v, has) -> Ui.zoom(frame, has, 1.05f));
                card.setOnClickListener(v -> {
                    if (item == null) return;
                    if (editing) listener.remove(item);
                    else listener.play(item);
                });
                card.setOnLongClickListener(v -> {
                    if (item != null) listener.remove(item);
                    return true;
                });
            }
        }
    }

    // ------------------------------------------------------------ providers

    /** A streaming service: TMDB id, name and logo path. */
    static final class Provider {
        final int id;
        final String name;
        final String logo;

        Provider(int id, String name, String logo) {
            this.id = id;
            this.name = name;
            this.logo = logo;
        }
    }

    interface OnProvider {
        void clicked(Provider p);
    }

    static final class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.VH> {
        static final int TILE = 52;
        static final int W = 74;
        final List<Provider> items = new ArrayList<>();
        private final OnProvider listener;

        ProviderAdapter(List<Provider> list, OnProvider listener) {
            items.addAll(list);
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return 50;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setFocusable(true);
            card.setClipChildren(false);
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            logo.setBackground(Ui.pill(0xFF1A1A1A, 9));
            Ui.round(logo, 9);
            card.addView(logo, new LinearLayout.LayoutParams(Ui.dp(TILE), Ui.dp(TILE)));
            TextView name = Ui.lines(c, 9.5f, Ui.TEXT_3, 2);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(W), -2);
            lp.topMargin = Ui.dp(9);
            card.addView(name, lp);
            card.setLayoutParams(new RecyclerView.LayoutParams(Ui.dp(W), -2));
            return new VH(card, logo, name);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Provider p = items.get(position);
            h.provider = p;
            h.name.setText(p.name);
            Glide.with(h.logo).load("https://image.tmdb.org/t/p/w154" + p.logo)
                    .override(Ui.dp(TILE), Ui.dp(TILE)).centerCrop().into(h.logo);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView logo;
            final TextView name;
            Provider provider;

            VH(View card, ImageView logo, TextView name) {
                super(card);
                this.logo = logo;
                this.name = name;
                card.setOnFocusChangeListener((v, has) -> {
                    Ui.zoom(logo, has, 1.12f);
                    name.setTextColor(has ? Ui.TEXT : Ui.TEXT_3);
                });
                card.setOnClickListener(v -> {
                    if (provider != null) listener.clicked(provider);
                });
            }
        }
    }

    // ------------------------------------------------------------ episodes

    interface OnEpisode {
        void clicked(int season, int episode);
    }

    static final class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {
        static final int W = 200;
        static final int H = 112;
        final List<JSONObject> items = new ArrayList<>();
        private final OnEpisode listener;
        private String fallbackStill;
        int season;

        EpisodeAdapter(OnEpisode listener) {
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return 20;
        }

        void set(int season, List<JSONObject> list, String fallbackStill) {
            this.season = season;
            this.fallbackStill = fallbackStill;
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClipChildren(false);
            FrameLayout frame = new FrameLayout(c);
            frame.setBackground(Ui.pill(0xFF1A1A1A, 8));
            Ui.round(frame, 8);
            ImageView image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            card.addView(frame, new LinearLayout.LayoutParams(Ui.dp(W), Ui.dp(H)));
            TextView name = Ui.lines(c, 12, Ui.TEXT, 1);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(W), -2);
            lp.topMargin = Ui.dp(8);
            card.addView(name, lp);
            TextView info = Ui.lines(c, 11, Ui.TEXT_3, 1);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(Ui.dp(W), -2);
            lp2.topMargin = Ui.dp(3);
            card.addView(info, lp2);
            card.setLayoutParams(new RecyclerView.LayoutParams(Ui.dp(W), -2));
            return new VH(card, frame, image, name, info);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject e = items.get(position);
            h.episode = e.optInt("episode_number", position + 1);
            h.name.setText(h.episode + ".  " + e.optString("name"));
            int rt = e.optInt("runtime", 0);
            String date = e.optString("air_date", "");
            h.info.setText((rt > 0 ? rt + " min" : "") + (rt > 0 && !date.isEmpty() ? "  ·  " : "") + date);
            String still = Title.path(e.optString("still_path", null));
            image(h.image, Title.Img.still(still != null ? still : fallbackStill), W, H);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView name;
            final TextView info;
            int episode;

            VH(View card, View frame, ImageView image, TextView name, TextView info) {
                super(card);
                this.image = image;
                this.name = name;
                this.info = info;
                card.setOnFocusChangeListener((v, has) -> {
                    Ui.zoom(frame, has, 1.06f);
                    name.setTextColor(has ? Ui.TEXT : Ui.TEXT_2);
                });
                card.setOnClickListener(v -> listener.clicked(season, episode));
            }
        }
    }

    // --------------------------------------------------------- season chips

    interface OnChip {
        void chosen(int index);
    }

    static final class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.VH> {
        final List<String> labels = new ArrayList<>();
        int selected;
        private final OnChip listener;

        ChipAdapter(OnChip listener) {
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return 30;
        }

        void set(List<String> list, int selected) {
            labels.clear();
            labels.addAll(list);
            this.selected = selected;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return labels.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView chip = Ui.text(parent.getContext(), 13, Ui.TEXT, true);
            chip.setFocusable(true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(18), Ui.dp(9), Ui.dp(18), Ui.dp(9));
            chip.setLayoutParams(new RecyclerView.LayoutParams(-2, -2));
            return new VH(chip);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.chip.setText(labels.get(position));
            h.paint();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView chip;

            VH(TextView chip) {
                super(chip);
                this.chip = chip;
                chip.setOnFocusChangeListener((v, has) -> paint());
                chip.setOnClickListener(v -> choose());
            }

            private void choose() {
                int p = getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION || p == selected) {
                    return;
                }
                int old = selected;
                selected = p;
                notifyItemChanged(old);
                paint();
                listener.chosen(p);
            }

            void paint() {
                boolean on = getBindingAdapterPosition() == selected;
                boolean focused = chip.hasFocus();
                chip.setBackground(on || focused
                        ? Ui.pill(focused ? 0xFFFFFFFF : 0x40FFFFFF, 20)
                        : Ui.pill(0x00000000, 20, Ui.LINE));
                chip.setTextColor(focused ? 0xFF000000 : Ui.TEXT);
            }
        }
    }
}
