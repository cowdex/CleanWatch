package tv.cleanwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The start screen, laid out like cinejoy's home page but drawn by the app itself: the hero
 * slide, Continue Watching, Browse by Provider, Top 10 and the rows below it. Movies and Shows
 * follow the same pattern; My List has no hero. Play hands the title to cinejoy's player.
 */
public class BrowseActivity extends Activity {
    private static final int HOME = 0;
    private static final int MOVIES = 1;
    private static final int SHOWS = 2;
    private static final int MY_LIST = 3;
    private static final String[] TAB_NAMES = {"Home", "Movies", "Shows", "My List"};

    private static final long EXIT_WINDOW_MS = 2500;
    /** Where a focused row's title sits, just below the header. */
    private static final int ROW_TOP = 60;
    /** The region cinejoy uses for "Movies on …" and the provider pages. */
    static final String REGION = "US";

    /** cinejoy's "Browse by Provider" row, in its order (TMDB provider ids and logos). */
    static final List<Cards.Provider> PROVIDERS = Arrays.asList(
            new Cards.Provider(8, "Netflix", "/rK1KljqmbvO9HQa1PBFLILWah72.png"),
            new Cards.Provider(9, "Amazon Prime Video", "/gMZdpavHmxFNnLpMHwVxfqeux2g.png"),
            new Cards.Provider(337, "Disney Plus", "/5eZ872CghnHFLB1j8grszbrx0dx.png"),
            new Cards.Provider(350, "Apple TV+", "/9icYBfYFcwgCbky5VdGUIKJ4C5i.png"),
            new Cards.Provider(2, "Apple TV", "/qdEGArH3lKfFnAtYXMkSYk5wxuG.png"),
            new Cards.Provider(15, "Hulu", "/44uAnmSqvA4yBOdbPWN8YgQHjWm.png"),
            new Cards.Provider(1899, "HBO Max", "/skypuy7SXuugIQeYg0IglmzoKaS.png"),
            new Cards.Provider(2303, "Paramount Plus", "/4N4BMd0Mm0kHAmF7RZgL5lW3cwc.png"),
            new Cards.Provider(386, "Peacock Premium", "/a1UIdq5BrkcAxnxcUhFsNbXnxeu.png"),
            new Cards.Provider(283, "Crunchyroll", "/uFL3c4Cq8M6WoLymlC5Y8bmGytV.png"),
            new Cards.Provider(43, "Starz", "/h25xjouKmiSmFiiqw0aDXbxGZo7.png"),
            new Cards.Provider(526, "AMC+", "/wsCUflcmL4dCkzBUaGP8cj1cTkh.png"),
            new Cards.Provider(34, "MGM Plus", "/q63Uzpu7JAs566vA2G23Lk7LcID.png"),
            new Cards.Provider(188, "YouTube Premium", "/eWHpKZihdiY617s5LipWJWvN28U.png"),
            new Cards.Provider(192, "YouTube", "/5Maob4o5w8oZnNeYpCDyVFD3M7X.png"),
            new Cards.Provider(73, "Tubi TV", "/9dEuvA8wg5TSeFBZlPxSVxFdimJ.png"),
            new Cards.Provider(300, "Pluto TV", "/fN4czqaMQNLeF6sSSIjGbAWzvwK.png"));

    private Hero hero;
    private VerticalGridView list;
    private View scrim;
    private final Rows.Adapter rows = new Rows.Adapter();
    private final View[] tabs = new View[TAB_NAMES.length];
    private final List<List<Rows.Row>> tabRows = new ArrayList<>();
    private final List<List<Title>> tabSlides = new ArrayList<>();
    private int tab = HOME;
    private long lastBackAt;

    private Rows.Row heroRow;
    private Rows.Row resumeRow;
    private Rows.Row myListRow;
    private Rows.Row becauseRow;
    private Cards.ResumeAdapter resumeAdapter;
    private Cards.PosterAdapter myListAdapter;
    private String becauseKey;

    private final Cards.OnTitle openDetails = new Cards.OnTitle() {
        @Override
        public void focused(Title t) {
        }

        @Override
        public void clicked(Title t) {
            Nav.details(BrowseActivity.this, t);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        installHttpCache();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabRows.add(null);
            tabSlides.add(null);
        }

        FrameLayout root = new FrameLayout(this);
        hero = new Hero(this, root, new Hero.Actions() {
            @Override
            public void play(Title t) {
                Nav.play(BrowseActivity.this, t, 0, 0);
            }

            @Override
            public void toggleList(Title t) {
                boolean added = Store.toggleMyList(BrowseActivity.this, t);
                Toast.makeText(BrowseActivity.this, added ? R.string.added_to_list : R.string.removed_from_list,
                        Toast.LENGTH_SHORT).show();
                refreshLibrary();
            }

            @Override
            public void info(Title t) {
                Nav.details(BrowseActivity.this, t);
            }
        });
        heroRow = Rows.Row.view(hero.view);

        resumeAdapter = new Cards.ResumeAdapter(new Cards.OnResume() {
            @Override
            public void play(Progress.Item item) {
                Title t = item.title;
                Nav.play(BrowseActivity.this, t, t.isTv() ? t.season : 0, t.isTv() ? t.episode : 0);
            }

            @Override
            public void remove(Progress.Item item) {
                Progress.hide(BrowseActivity.this, item);
                Toast.makeText(BrowseActivity.this, getString(R.string.removed_from_continue, item.title.name),
                        Toast.LENGTH_SHORT).show();
                refreshLibrary();
            }
        });
        resumeRow = Rows.Row.cards(getString(R.string.row_continue), resumeAdapter, Cards.ResumeAdapter.H + 44);
        resumeRow.chevron = true;
        resumeRow.actionIcon = R.drawable.ic_pencil;
        resumeRow.action = v -> {
            resumeAdapter.setEditing(!resumeAdapter.editing);
            if (resumeAdapter.editing) {
                Toast.makeText(this, R.string.continue_edit_hint, Toast.LENGTH_LONG).show();
            }
        };
        myListAdapter = new Cards.PosterAdapter(openDetails, false);
        myListRow = Rows.Row.cards(getString(R.string.row_my_list), myListAdapter, Cards.POSTER_H);

        list = Rows.list(this, ROW_TOP);
        list.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);   // the hero stays at the very top
        list.setClipChildren(true);
        list.setPadding(0, 0, 0, Ui.dp(40));
        list.setAdapter(rows);
        list.setOnChildSelectedListener((parent, view, pos, id) -> onRowSelected(pos));
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));

        // Rows scrolled up pass under the header: fade them out there.
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF050505, 0xFF050505, 0xE6050505, 0x00050505}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, Ui.dp(64)));

        root.addView(header());
        setContentView(root);
        getWindow().setBackgroundDrawable(null);   // the hero layer paints the background

        Api.top10(hero::setRanks);
        showTab(HOME);
        hero.firstButton().requestFocus();
        // Once this screen is up, get cinejoy running in the background so Play starts fast.
        root.postDelayed(() -> Web.warm(this), 2500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLibrary();
        hero.setActive(list.getSelectedPosition() == 0 && rows.rows.contains(heroRow));
        if (Web.view == null) {
            list.postDelayed(() -> Web.warm(this), 2500);   // e.g. after the page's process was lost
        }
    }

    @Override
    protected void onPause() {
        hero.setActive(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            Web.release();   // leaving the app: free the background page
        }
        hero.release();
        super.onDestroy();
    }

    private void installHttpCache() {
        try {
            if (HttpResponseCache.getInstalled() == null) {
                HttpResponseCache.install(new File(getCacheDir(), "http"), 16L * 1024 * 1024);
            }
        } catch (Exception ignored) {
        }
    }

    private void onRowSelected(int pos) {
        boolean onHero = pos == 0 && rows.rows.contains(heroRow);
        hero.setActive(onHero);
        scrim.setAlpha(onHero ? 0f : 1f);
    }

    // ---------------------------------------------------------------- header

    private View header() {
        FrameLayout bar = new FrameLayout(this);
        bar.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.cinejoy_logo);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(28), Gravity.START | Gravity.TOP);
        lp.leftMargin = Ui.dp(30);
        lp.topMargin = Ui.dp(17);
        bar.addView(logo, lp);

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(5), Ui.dp(4), Ui.dp(5), Ui.dp(4));
        pill.setBackground(Ui.pill(0x59182430, 18, 0x26FFFFFF));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.HORIZONTAL);
            t.setGravity(Gravity.CENTER);
            t.setFocusable(true);
            t.setPadding(Ui.dp(i == HOME ? 14 : 16), 0, Ui.dp(16), 0);
            if (i == HOME) {
                ImageView home = Cards.icon(this, R.drawable.ic_home, 11, Ui.TEXT);
                ((LinearLayout.LayoutParams) home.getLayoutParams()).rightMargin = Ui.dp(6);
                t.addView(home);
            }
            TextView label = Ui.text(this, 10.5f, Ui.TEXT_2, true);
            label.setText(TAB_NAMES[i]);
            t.addView(label);
            t.setOnFocusChangeListener((v, has) -> paintTabs());
            t.setOnClickListener(v -> showTab(index));
            tabs[i] = t;
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, Ui.dp(26));
            tp.rightMargin = Ui.dp(4);
            pill.addView(t, tp);
        }
        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(1), Ui.dp(12));
        dp.leftMargin = Ui.dp(4);
        dp.rightMargin = Ui.dp(6);
        pill.addView(divider, dp);
        pill.addView(iconButton(R.drawable.ic_search, () -> startActivity(new Intent(this, SearchActivity.class))));
        pill.addView(iconButton(R.drawable.ic_settings, this::showMenu));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, Ui.dp(34), Gravity.END | Gravity.TOP);
        pp.rightMargin = Ui.dp(31);
        pp.topMargin = Ui.dp(13);
        bar.addView(pill, pp);
        return bar;
    }

    private View iconButton(int res, Runnable click) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setColorFilter(Ui.TEXT_2, PorterDuff.Mode.SRC_IN);
        b.addView(i, new FrameLayout.LayoutParams(Ui.dp(14), Ui.dp(14), Gravity.CENTER));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(has ? Ui.pill(0xFFFFFFFF, 13) : null);
            i.setColorFilter(has ? 0xFF000000 : Ui.TEXT_2, PorterDuff.Mode.SRC_IN);
        });
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(26));
        lp.leftMargin = Ui.dp(1);
        b.setLayoutParams(lp);
        return b;
    }

    private void paintTabs() {
        boolean inHeader = false;
        for (View t : tabs) {
            inHeader |= t.hasFocus();
        }
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout t = (LinearLayout) tabs[i];
            boolean white = t.hasFocus() || (i == tab && !inHeader);
            int fill = white ? 0xFFFFFFFF : (i == tab ? 0x40FFFFFF : 0x00000000);
            t.setBackground(Ui.pill(fill, 13));
            int color = white ? 0xFF000000 : (i == tab ? Ui.TEXT : Ui.TEXT_2);
            for (int k = 0; k < t.getChildCount(); k++) {
                View c = t.getChildAt(k);
                if (c instanceof TextView) ((TextView) c).setTextColor(color);
                if (c instanceof ImageView) ((ImageView) c).setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    // ------------------------------------------------------------------ tabs

    private void showTab(int index) {
        tab = index;
        paintTabs();
        List<Rows.Row> r = tabRows.get(index);
        if (r == null || index == MY_LIST) {
            r = build(index);
            tabRows.set(index, r);
        }
        boolean withHero = index != MY_LIST;
        list.setPadding(0, withHero ? 0 : Ui.dp(ROW_TOP), 0, Ui.dp(40));
        rows.set(r);
        list.setSelectedPosition(0);
        if (withHero && tabSlides.get(index) != null) {
            hero.setSlides(tabSlides.get(index));
        }
        onRowSelected(withHero ? 0 : 1);
    }

    private List<Rows.Row> build(int index) {
        List<Rows.Row> r = new ArrayList<>();
        switch (index) {
            case HOME:
                r.add(heroRow);
                heroSlides(index, Api.tmdb("trending/movie/day"), Title.MOVIE);
                syncResumeRow(r);
                r.add(Rows.Row.cards(getString(R.string.row_providers),
                        new Cards.ProviderAdapter(PROVIDERS, p -> ProviderActivity.open(this, p)),
                        Cards.ProviderAdapter.TILE + 34));
                Rows.Row top = Rows.Row.cards(getString(R.string.row_top10),
                        new Cards.PosterAdapter(openDetails, true), Cards.POSTER_H);
                r.add(top);
                Api.top10(items -> fill(HOME, top, items));
                becauseRow = Rows.Row.cards("", new Cards.PosterAdapter(openDetails, false), Cards.POSTER_H);
                r.add(becauseRow);
                r.add(tmdbRow(index, "Trending Movies", Api.tmdb("trending/movie/day"), Title.MOVIE));
                r.add(tmdbRow(index, "Trending Series", Api.tmdb("trending/tv/day"), Title.TV));
                r.add(tmdbRow(index, "Movies on Netflix", Api.tmdb("discover/movie", "sort_by", "popularity.desc",
                        "with_watch_providers", "8", "watch_region", REGION), Title.MOVIE));
                r.add(tmdbRow(index, "TV Series on Netflix", Api.tmdb("discover/tv", "sort_by", "popularity.desc",
                        "with_watch_providers", "8", "watch_region", REGION), Title.TV));
                r.add(tmdbRow(index, "Top Rated Movies", Api.tmdb("movie/top_rated"), Title.MOVIE));
                r.add(tmdbRow(index, "Top Rated Series", Api.tmdb("tv/top_rated"), Title.TV));
                // cinejoy's themed lists come last, in its order.
                Api.collections(cs -> {
                    if (cs == null) return;
                    List<Rows.Row> extra = new ArrayList<>();
                    for (String[] c : cs) {
                        Rows.Row row = Rows.Row.cards(c[1], new Cards.PosterAdapter(openDetails, false), Cards.POSTER_H);
                        extra.add(row);
                        Api.collection(c[0], items -> fill(HOME, row, items));
                    }
                    append(HOME, extra);
                });
                break;
            case MOVIES:
                r.add(heroRow);
                heroSlides(index, Api.tmdb("trending/movie/week"), Title.MOVIE);
                r.add(tmdbRow(index, "Popular Movies", Api.tmdb("movie/popular"), Title.MOVIE));
                r.add(tmdbRow(index, "Now Playing", Api.tmdb("movie/now_playing"), Title.MOVIE));
                r.add(tmdbRow(index, "Top Rated Movies", Api.tmdb("movie/top_rated"), Title.MOVIE));
                r.add(tmdbRow(index, "Coming Soon", Api.tmdb("movie/upcoming"), Title.MOVIE));
                addGenres(r, index, Title.MOVIE, new String[][]{
                        {"28", "Action"}, {"35", "Comedy"}, {"18", "Drama"}, {"53", "Thriller"},
                        {"27", "Horror"}, {"878", "Science Fiction"}, {"16", "Animation"},
                        {"10749", "Romance"}, {"80", "Crime"}, {"99", "Documentary"}});
                break;
            case SHOWS:
                r.add(heroRow);
                heroSlides(index, Api.tmdb("trending/tv/week"), Title.TV);
                r.add(tmdbRow(index, "Popular Series", Api.tmdb("tv/popular"), Title.TV));
                r.add(tmdbRow(index, "Top Rated Series", Api.tmdb("tv/top_rated"), Title.TV));
                r.add(tmdbRow(index, "On the Air", Api.tmdb("tv/on_the_air"), Title.TV));
                addGenres(r, index, Title.TV, new String[][]{
                        {"18", "Drama"}, {"35", "Comedy"}, {"80", "Crime"}, {"10765", "Sci-Fi & Fantasy"},
                        {"9648", "Mystery"}, {"10759", "Action & Adventure"}, {"16", "Animation"},
                        {"99", "Documentary"}, {"10764", "Reality"}, {"10762", "Kids"}});
                break;
            case MY_LIST:
            default:
                List<Title> mine = Store.myList(this);
                myListAdapter.set(mine);
                if (!mine.isEmpty()) r.add(myListRow);
                syncResumeRow(r);
                if (r.isEmpty()) {
                    TextView empty = Ui.lines(this, 14, Ui.TEXT_2, 3);
                    empty.setText(R.string.my_list_empty);
                    empty.setFocusable(true);
                    empty.setPadding(Ui.dp(Ui.EDGE), Ui.dp(8), Ui.dp(Ui.EDGE), Ui.dp(8));
                    r.add(Rows.Row.view(empty));
                }
                break;
        }
        if (index == HOME) {
            tabRows.set(HOME, r);   // loadBecause() edits this list
            loadBecause();
        }
        return r;
    }

    private void heroSlides(int index, String url, String type) {
        Api.list(url, type, items -> {
            if (items == null) return;
            tabSlides.set(index, items);
            if (tab == index) hero.setSlides(items);
        });
    }

    private void addGenres(List<Rows.Row> r, int index, String type, String[][] genres) {
        for (String[] g : genres) {
            r.add(tmdbRow(index, g[1], Api.tmdb("discover/" + type,
                    "with_genres", g[0], "sort_by", "popularity.desc", "vote_count.gte", "150"), type));
        }
    }

    private Rows.Row tmdbRow(int index, String title, String url, String type) {
        Rows.Row row = Rows.Row.cards(title, new Cards.PosterAdapter(openDetails, false), Cards.POSTER_H);
        Api.list(url, type, items -> fill(index, row, items));
        return row;
    }

    /** Puts loaded titles into a row, or drops the row when there is nothing to show. */
    private void fill(int index, Rows.Row row, List<Title> items) {
        if (items == null || items.isEmpty()) {
            removeRow(index, row);
            return;
        }
        ((Cards.PosterAdapter) row.adapter).set(items);
        rows.changed(row);
    }

    private void append(int index, List<Rows.Row> extra) {
        List<Rows.Row> r = tabRows.get(index);
        if (r == null) return;
        r.addAll(extra);
        if (tab == index) {
            int pos = rows.rows.size();
            rows.rows.addAll(extra);
            rows.notifyItemRangeInserted(pos, extra.size());
        }
    }

    private void removeRow(int index, Rows.Row row) {
        List<Rows.Row> r = tabRows.get(index);
        if (r != null) r.remove(row);
        if (tab == index) rows.remove(row);
    }

    /** "Because you watched …": recommendations for the latest title in Continue Watching. */
    private void loadBecause() {
        List<Progress.Item> items = Progress.items(this);
        if (items.isEmpty()) {
            removeRow(HOME, becauseRow);
            return;
        }
        Title last = items.get(0).title;
        if (last.key().equals(becauseKey)) return;
        becauseKey = last.key();
        becauseRow.title = getString(R.string.row_because, last.name);
        Api.list(Api.tmdb(last.type + "/" + last.id + "/recommendations"), last.type, found -> {
            if (found == null || found.isEmpty()) {
                removeRow(HOME, becauseRow);
                return;
            }
            ((Cards.PosterAdapter) becauseRow.adapter).set(found);
            rows.changed(becauseRow);
        });
    }

    // -------------------------------------------------------------- library

    /** Puts Continue Watching right after the hero when there is something to resume. */
    private void syncResumeRow(List<Rows.Row> r) {
        List<Progress.Item> items = Progress.items(this);
        resumeAdapter.set(items);
        boolean has = r.contains(resumeRow);
        if (!items.isEmpty() && !has) {
            int at = r.contains(heroRow) ? 1 : (r.contains(myListRow) ? 1 : 0);
            r.add(Math.min(at, r.size()), resumeRow);
        } else if (items.isEmpty() && has) {
            r.remove(resumeRow);
        }
    }

    /** After playing or changing My List: Continue Watching and My List are up to date. */
    private void refreshLibrary() {
        List<Rows.Row> home = tabRows.get(HOME);
        if (home == null) return;
        boolean hadResume = home.contains(resumeRow);
        syncResumeRow(home);
        myListAdapter.set(Store.myList(this));
        if (tab == HOME && hadResume != home.contains(resumeRow)) {
            int selected = list.getSelectedPosition();
            rows.set(home);
            list.setSelectedPosition(Math.min(selected, home.size() - 1));
        } else if (tab == HOME) {
            rows.changed(resumeRow);
        }
        if (becauseRow != null) {
            loadBecause();
        }
        if (tab == MY_LIST) {
            showTab(MY_LIST);
        }
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Up from the first row goes to the tabs (Leanback grids keep focus by default).
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && list.hasFocus() && list.getSelectedPosition() == 0) {
            tabs[tab].requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (resumeAdapter.editing) {
            resumeAdapter.setEditing(false);
            return;
        }
        if (list.hasFocus() && list.getSelectedPosition() > 0) {
            list.setSelectedPositionSmooth(0);   // back to the top of this tab
            return;
        }
        if (list.hasFocus()) {
            tabs[tab].requestFocus();
            return;
        }
        if (tab != HOME) {
            showTab(HOME);
            tabs[HOME].requestFocus();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAt < EXIT_WINDOW_MS) {
            finish();
        } else {
            lastBackAt = now;
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setItems(new String[]{getString(R.string.menu_website), getString(R.string.menu_exit)},
                        (d, which) -> {
                            if (which == 0) {
                                Nav.website(this);
                            } else {
                                finish();
                            }
                        })
                .show();
    }
}
