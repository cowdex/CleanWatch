package tv.cleanwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Continue Watching, from cinejoy's own record of what was watched on this TV. cinejoy's player
 * keeps it in its page storage ("Cinejoy::Progress"); the player screen copies it here
 * whenever a film pauses or the player closes.
 */
final class Progress {
    private static final String PREFS = "cinejoy";
    private static final String KEY = "progress";
    private static final String HIDDEN = "hidden";

    /** One film or show in progress. */
    static final class Item {
        final Title title = new Title();
        String backdropUrl;
        double watched;
        double duration;
        long updatedAt;

        /** "48m left", "1hr 33m left", or for shows "S1 E2 · 23m left". */
        String left() {
            String time = "";
            if (duration > 0) {
                int min = (int) Math.max(1, Math.round((duration - watched) / 60));
                time = (min >= 60 ? (min / 60) + "hr " + (min % 60) + "m" : min + "m") + " left";
            }
            if (title.isTv() && title.season > 0) {
                return "S" + title.season + " E" + title.episode + (time.isEmpty() ? "" : " · " + time);
            }
            return time;
        }

        float fraction() {
            return duration > 0 ? (float) Math.min(1, Math.max(0, watched / duration)) : 0;
        }
    }

    private Progress() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, String json) {
        if (json != null && json.startsWith("{")) {
            prefs(c).edit().putString(KEY, json).apply();
        }
    }

    /** Hides a title from Continue Watching until it is watched again. */
    static void hide(Context c, Item item) {
        try {
            JSONObject h = new JSONObject(prefs(c).getString(HIDDEN, "{}"));
            h.put(item.title.key(), item.updatedAt);
            prefs(c).edit().putString(HIDDEN, h.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Most recent first; finished films and hidden titles left out. */
    static List<Item> items(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(prefs(c).getString(KEY, "{}"));
            JSONObject hidden = new JSONObject(prefs(c).getString(HIDDEN, "{}"));
            JSONObject state = root.optJSONObject("state");
            JSONObject items = state == null ? null : state.optJSONObject("items");
            if (items == null) {
                return out;
            }
            Iterator<String> keys = items.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                Item it = parse(id, items.optJSONObject(id));
                if (it == null) {
                    continue;
                }
                if (hidden.optLong(it.title.key(), -1) >= it.updatedAt) {
                    continue;   // removed with the pencil, and not watched since
                }
                if (!it.title.isTv() && it.duration > 0 && it.watched / it.duration > 0.95) {
                    continue;   // finished
                }
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return out;
    }

    private static Item parse(String id, JSONObject o) {
        if (o == null) {
            return null;
        }
        Item it = new Item();
        it.title.id = parseInt(id);
        if (it.title.id <= 0) {
            return null;
        }
        it.title.type = "show".equals(o.optString("type")) || "tv".equals(o.optString("type")) ? Title.TV : Title.MOVIE;
        it.title.name = o.optString("title");
        it.title.year = o.optInt("year", 0) > 0 ? String.valueOf(o.optInt("year")) : "";
        it.title.poster = tmdbPath(o.optString("poster", null));
        it.title.backdrop = tmdbPath(o.optString("backdrop", null));
        it.backdropUrl = o.optString("backdrop", null);
        it.updatedAt = o.optLong("updatedAt", 0);
        JSONObject p = o.optJSONObject("progress");
        if (p != null) {
            it.watched = p.optDouble("watched", 0);
            it.duration = p.optDouble("duration", 0);
        }
        JSONObject episodes = o.optJSONObject("episodes");
        if (episodes != null) {
            JSONObject seasons = o.optJSONObject("seasons");
            JSONObject last = null;
            Iterator<String> ek = episodes.keys();
            while (ek.hasNext()) {
                JSONObject e = episodes.optJSONObject(ek.next());
                if (e != null && (last == null || e.optLong("updatedAt") > last.optLong("updatedAt"))) {
                    last = e;
                }
            }
            if (last != null) {
                it.title.episode = last.optInt("number", 1);
                JSONObject s = seasons == null ? null : seasons.optJSONObject(last.optString("seasonId"));
                it.title.season = s != null ? s.optInt("number", 1) : parseInt(last.optString("seasonId").replaceAll("\\D", ""));
                JSONObject ep = last.optJSONObject("progress");
                if (ep != null) {
                    it.watched = ep.optDouble("watched", 0);
                    it.duration = ep.optDouble("duration", 0);
                }
                it.updatedAt = Math.max(it.updatedAt, last.optLong("updatedAt", 0));
            }
        }
        return it;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String tmdbPath(String url) {
        if (url == null) {
            return null;
        }
        int i = url.indexOf("/t/p/");
        int slash = i < 0 ? -1 : url.indexOf('/', i + 5);
        return slash < 0 ? null : url.substring(slash);
    }
}
