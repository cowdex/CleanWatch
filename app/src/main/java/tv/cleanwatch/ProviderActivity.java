package tv.cleanwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;

import com.bumptech.glide.Glide;

import java.util.List;

/** One streaming service, like cinejoy's provider page: its films or series, most popular first. */
public class ProviderActivity extends Activity {
    private static final String EXTRA_ID = "tv.cleanwatch.PROVIDER_ID";
    private static final String EXTRA_NAME = "tv.cleanwatch.PROVIDER_NAME";
    private static final String EXTRA_LOGO = "tv.cleanwatch.PROVIDER_LOGO";
    private static final int COLUMNS = 6;

    private int providerId;
    private String type = Title.MOVIE;
    private int page;
    private int pages = 1;
    private boolean loading;
    private TextView movies;
    private TextView series;
    private VerticalGridView grid;
    private Cards.PosterAdapter results;

    static void open(Activity a, Cards.Provider p) {
        a.startActivity(new Intent(a, ProviderActivity.class)
                .putExtra(EXTRA_ID, p.id).putExtra(EXTRA_NAME, p.name).putExtra(EXTRA_LOGO, p.logo));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        providerId = getIntent().getIntExtra(EXTRA_ID, 8);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        Ui.round(logo, 9);
        Glide.with(logo).load("https://image.tmdb.org/t/p/w154" + getIntent().getStringExtra(EXTRA_LOGO))
                .override(Ui.dp(40), Ui.dp(40)).centerCrop().into(logo);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        TextView name = Ui.text(this, 22, Ui.TEXT, true);
        name.setText(getIntent().getStringExtra(EXTRA_NAME));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.leftMargin = Ui.dp(14);
        head.addView(name, np);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2);
        hp.leftMargin = Ui.dp(Ui.EDGE);
        hp.topMargin = Ui.dp(26);
        root.addView(head, hp);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        movies = chip(getString(R.string.provider_movies), Title.MOVIE);
        series = chip(getString(R.string.provider_series), Title.TV);
        chips.addView(movies);
        chips.addView(series);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2);
        cp.leftMargin = Ui.dp(Ui.EDGE);
        cp.topMargin = Ui.dp(80);
        root.addView(chips, cp);

        results = new Cards.PosterAdapter(new Cards.OnTitle() {
            @Override
            public void focused(Title t) {
            }

            @Override
            public void clicked(Title t) {
                Nav.details(ProviderActivity.this, t);
            }
        }, false);
        grid = new VerticalGridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setHorizontalSpacing(Ui.dp(14));
        grid.setVerticalSpacing(Ui.dp(20));
        grid.setPadding(Ui.dp(Ui.EDGE), Ui.dp(14), Ui.dp(Ui.EDGE), Ui.dp(30));
        grid.setClipToPadding(false);
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        grid.setWindowAlignmentOffsetPercent(30);
        grid.setAdapter(results);
        grid.setOnChildSelectedListener((parent, view, pos, id) -> {
            if (pos >= results.getItemCount() - COLUMNS * 2) loadMore();
        });
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -1);
        gp.topMargin = Ui.dp(118);
        root.addView(grid, gp);
        setContentView(root);

        show(Title.MOVIE);
        grid.requestFocus();
    }

    private TextView chip(String label, String kind) {
        TextView t = Ui.text(this, 12, Ui.TEXT, true);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setPadding(Ui.dp(18), Ui.dp(7), Ui.dp(18), Ui.dp(7));
        t.setOnFocusChangeListener((v, has) -> paintChips());
        t.setOnClickListener(v -> show(kind));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void paintChips() {
        for (TextView t : new TextView[]{movies, series}) {
            boolean on = (t == movies) == Title.MOVIE.equals(type);
            boolean white = t.hasFocus() || on;
            t.setBackground(Ui.pill(white ? 0xFFFFFFFF : 0x26FFFFFF, 16));
            t.setTextColor(white ? 0xFF000000 : Ui.TEXT_2);
        }
    }

    private void show(String kind) {
        type = kind;
        page = 0;
        pages = 1;
        results.set(null);
        paintChips();
        loadMore();
    }

    private void loadMore() {
        if (loading || page >= pages || page >= 10) return;
        loading = true;
        final String kind = type;
        final int next = page + 1;
        String url = Api.tmdb("discover/" + kind, "sort_by", "popularity.desc",
                "with_watch_providers", String.valueOf(providerId),
                "watch_region", BrowseActivity.REGION, "page", String.valueOf(next));
        Api.json(url, o -> {
            loading = false;
            if (o == null || !kind.equals(type)) return;
            page = next;
            pages = o.optInt("total_pages", 1);
            Api.list(url, kind, (List<Title> items) -> {
                if (items != null && kind.equals(type)) results.add(items);
            });
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Up from the first row of posters goes to the Movies / Series switch.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && grid.hasFocus() && grid.getSelectedPosition() < COLUMNS) {
            (Title.MOVIE.equals(type) ? movies : series).requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
