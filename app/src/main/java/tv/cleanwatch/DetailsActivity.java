package tv.cleanwatch;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One film or show: picture, facts, Play, My List; for shows, seasons and episodes. */
public class DetailsActivity extends Activity {
    private Title title;
    private Billboard billboard;
    private VerticalGridView list;
    private final Rows.Adapter rows = new Rows.Adapter();
    private TextView playButton;
    private TextView listButton;

    private Rows.Row seasonsRow;
    private Rows.Row episodesRow;
    private Cards.ChipAdapter seasonsAdapter;
    private Cards.EpisodeAdapter episodesAdapter;
    private final List<Integer> seasonNumbers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        try {
            title = Title.fromJson(new JSONObject(getIntent().getStringExtra(Nav.EXTRA_TITLE)));
        } catch (Exception e) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        billboard = new Billboard(this, root, 5);

        list = Rows.list(this, 0);
        list.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        list.setWindowAlignmentOffset(Ui.dp(150));
        list.setPadding(0, Ui.dp(64), 0, Ui.dp(40));
        list.setAdapter(rows);
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        getWindow().setBackgroundDrawable(null);   // the billboard paints the background

        List<Rows.Row> r = new ArrayList<>();
        r.add(Rows.Row.view(header()));
        rows.set(r);
        billboard.show(title, true);
        list.requestFocus();

        Api.details(title, this::loaded);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playButton != null) {
            playButton.setText(playLabel());
            listButton.setText(listLabel());
        }
    }

    @Override
    protected void onDestroy() {
        if (billboard != null) {
            billboard.release();
        }
        super.onDestroy();
    }

    private LinearLayout header() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(Ui.EDGE), 0, Ui.dp(Ui.EDGE), Ui.dp(10));
        box.setClipChildren(false);
        box.addView(billboard.info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        playButton = Buttons.make(this, playLabel(), true, v -> Nav.play(this, title, 0, 0), null);
        listButton = Buttons.make(this, listLabel(), false, v -> {
            Store.toggleMyList(this, title);
            listButton.setText(listLabel());
        }, null);
        buttons.addView(playButton);
        buttons.addView(listButton);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(22);
        box.addView(buttons, lp);
        box.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
        return box;
    }

    private String playLabel() {
        if (!title.isTv()) {
            return "▶  Play";
        }
        Title last = Store.lastPlayed(this, title);
        if (last != null && last.season > 0) {
            return "▶  Resume  S" + last.season + " · E" + Math.max(1, last.episode);
        }
        return "▶  Play  S1 · E1";
    }

    private String listLabel() {
        return Store.inMyList(this, title) ? "✓  In My List" : "+  My List";
    }

    // ------------------------------------------------------------- content

    private void loaded(JSONObject d) {
        if (d == null || isFinishing()) {
            return;
        }
        billboard.show(title, true);
        if (title.isTv()) {
            addSeasons(d);
        }
        JSONObject rec = d.optJSONObject("recommendations");
        JSONArray rs = rec == null ? null : rec.optJSONArray("results");
        List<Title> more = new ArrayList<>();
        for (int i = 0; rs != null && i < rs.length(); i++) {
            Title t = Title.fromTmdb(rs.optJSONObject(i), title.type);
            if (t != null && t.poster != null) {
                more.add(t);
            }
        }
        if (!more.isEmpty()) {
            Cards.PosterAdapter a = new Cards.PosterAdapter(new Cards.OnTitle() {
                @Override
                public void focused(Title t) {
                }

                @Override
                public void clicked(Title t) {
                    Nav.details(DetailsActivity.this, t);
                }
            }, false);
            a.set(more);
            rows.rows.add(Rows.Row.cards("More Like This", a, Cards.POSTER_H));
            rows.notifyItemInserted(rows.rows.size() - 1);
        }
    }

    private void addSeasons(JSONObject d) {
        JSONArray ss = d.optJSONArray("seasons");
        List<String> labels = new ArrayList<>();
        for (int i = 0; ss != null && i < ss.length(); i++) {
            JSONObject s = ss.optJSONObject(i);
            int n = s.optInt("season_number", 0);
            if (n <= 0 || s.optInt("episode_count", 0) == 0) {
                continue;   // specials and empty seasons
            }
            seasonNumbers.add(n);
            labels.add("Season " + n);
        }
        if (seasonNumbers.isEmpty()) {
            return;
        }
        Title last = Store.lastPlayed(this, title);
        int startSeason = last != null && seasonNumbers.contains(last.season) ? last.season : seasonNumbers.get(0);
        int startIndex = seasonNumbers.indexOf(startSeason);

        seasonsAdapter = new Cards.ChipAdapter(i -> loadSeason(seasonNumbers.get(i), 0));
        seasonsAdapter.set(labels, startIndex);
        seasonsRow = Rows.Row.cards(null, seasonsAdapter, 40);
        seasonsRow.selected = startIndex;
        episodesAdapter = new Cards.EpisodeAdapter((season, episode) -> Nav.play(this, title, season, episode));
        episodesRow = Rows.Row.cards("Episodes", episodesAdapter, Cards.EpisodeAdapter.H + 44);

        int at = 1;
        rows.rows.add(at, seasonsRow);
        rows.rows.add(at + 1, episodesRow);
        rows.notifyItemRangeInserted(at, 2);
        loadSeason(startSeason, last != null && last.season == startSeason ? last.episode : 0);
    }

    private void loadSeason(int season, int focusEpisode) {
        Api.season(title.id, season, s -> {
            if (s == null || isFinishing()) {
                return;
            }
            JSONArray es = s.optJSONArray("episodes");
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; es != null && i < es.length(); i++) {
                list.add(es.optJSONObject(i));
            }
            episodesRow.selected = Math.max(0, Math.min(list.size() - 1, focusEpisode - 1));
            episodesAdapter.set(season, list, title.backdrop);
            rows.changed(episodesRow);
        });
    }
}
