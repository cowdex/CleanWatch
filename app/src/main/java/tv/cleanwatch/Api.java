package tv.cleanwatch;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The film data, from the same places cinejoy's own pages get it: TMDB for titles, posters
 * and episodes, and cinejoy's list server for the Top 10 and its themed lists.
 *
 * Every call answers on the main thread, once, with null on failure. Answers are kept in
 * memory, and requests already on their way are shared, so going back to a screen is instant.
 */
final class Api {
    interface Cb<T> {
        void done(T result);
    }

    private static final String TAG = "CleanWatch";
    private static final String TMDB = "https://api.themoviedb.org/3/";
    private static final String TOP10 = "https://server2.wing.st/api/insights/top10?type=all";
    private static final String LISTS = "https://lists2.wing.st/joy";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, JSONObject> CACHE = new LruCache<>(300);
    private static final Map<String, List<Cb<JSONObject>>> IN_FLIGHT = new HashMap<>();

    private Api() {
    }

    // ------------------------------------------------------------- transport

    static void json(String url, Cb<JSONObject> cb) {
        JSONObject hit = CACHE.get(url);
        if (hit != null) {
            cb.done(hit);
            return;
        }
        List<Cb<JSONObject>> waiting = IN_FLIGHT.get(url);
        if (waiting != null) {
            waiting.add(cb);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(cb);
        IN_FLIGHT.put(url, waiting);
        POOL.execute(() -> {
            JSONObject result = fetch(url);
            MAIN.post(() -> {
                if (result != null) {
                    CACHE.put(url, result);
                }
                List<Cb<JSONObject>> all = IN_FLIGHT.remove(url);
                if (all != null) {
                    for (Cb<JSONObject> c : all) {
                        c.done(result);
                    }
                }
            });
        });
    }

    private static JSONObject fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Accept", "application/json");
            if (url.startsWith(TMDB)) {
                c.setRequestProperty("Authorization", "Bearer " + BuildConfig.TMDB_TOKEN);
            }
            int code = c.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " for " + redact(url));
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Request failed: " + redact(url) + " (" + e.getClass().getSimpleName() + ")");
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String redact(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    // ------------------------------------------------------------------ TMDB

    /** A TMDB address; extra query parameters come in name, value pairs. */
    static String tmdb(String path, String... params) {
        Uri.Builder b = Uri.parse(TMDB + path).buildUpon().appendQueryParameter("language", "en-US");
        for (int i = 0; i + 1 < params.length; i += 2) {
            b.appendQueryParameter(params[i], params[i + 1]);
        }
        return b.build().toString();
    }

    /** A TMDB list ("results"); type is used when the answer doesn't say. */
    static void list(String url, String type, Cb<List<Title>> cb) {
        json(url, o -> {
            if (o == null) {
                cb.done(null);
                return;
            }
            List<Title> out = new ArrayList<>();
            JSONArray rs = o.optJSONArray("results");
            for (int i = 0; rs != null && i < rs.length(); i++) {
                Title t = Title.fromTmdb(rs.optJSONObject(i), type);
                if (t != null && t.poster != null) {
                    out.add(t);
                }
            }
            cb.done(out);
        });
    }

    static String detailsUrl(String type, int id) {
        return tmdb(type + "/" + id,
                "append_to_response", "images,recommendations",
                "include_image_language", "en,null");
    }

    /** Fills in logo, genres, runtime or seasons, and returns the raw answer too. */
    static void details(Title t, Cb<JSONObject> cb) {
        json(detailsUrl(t.type, t.id), d -> {
            if (d != null && !t.detailed) {
                t.fill(d);
            }
            cb.done(d);
        });
    }

    static void season(int tvId, int season, Cb<JSONObject> cb) {
        json(tmdb("tv/" + tvId + "/season/" + season), cb);
    }

    static void search(String query, Cb<List<Title>> cb) {
        list(tmdb("search/multi", "query", query, "include_adult", "false"), null, cb);
    }

    // ------------------------------------------------------- cinejoy's lists

    /** Today's Top 10, in order, with details from TMDB. */
    static void top10(Cb<List<Title>> cb) {
        json(TOP10, o -> {
            JSONArray items = o == null ? null : o.optJSONArray("items");
            if (items == null || items.length() == 0) {
                cb.done(null);
                return;
            }
            int n = Math.min(10, items.length());
            Title[] slots = new Title[n];
            int[] left = {n};
            for (int i = 0; i < n; i++) {
                JSONObject it = items.optJSONObject(i);
                Title t = new Title();
                t.type = "tv".equals(it.optString("mediaType")) ? Title.TV : Title.MOVIE;
                t.id = it.optInt("tmdbId", 0);   // sent as a string; optInt converts it
                t.rank = it.optInt("rank", i + 1);
                slots[i] = t;
                details(t, d -> {
                    if (--left[0] == 0) {
                        List<Title> out = new ArrayList<>();
                        for (Title s : slots) {
                            if (s.poster != null) {
                                out.add(s);
                            }
                        }
                        cb.done(out);
                    }
                });
            }
        });
    }

    /** The themed lists cinejoy shows on its start page: [id, title] pairs. */
    static void collections(Cb<List<String[]>> cb) {
        json(LISTS, o -> {
            JSONArray cs = o == null ? null : o.optJSONArray("collections");
            if (cs == null) {
                cb.done(null);
                return;
            }
            List<String[]> out = new ArrayList<>();
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.optJSONObject(i);
                out.add(new String[]{c.optString("id"), c.optString("title")});
            }
            cb.done(out);
        });
    }

    static void collection(String id, Cb<List<Title>> cb) {
        json(LISTS + "/" + Uri.encode(id) + "?limit=30", o -> {
            JSONArray items = o == null ? null : o.optJSONArray("items");
            if (items == null) {
                cb.done(null);
                return;
            }
            List<Title> out = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                JSONObject ids = it.optJSONObject("ids");
                int tmdbId = ids == null ? 0 : ids.optInt("tmdb", 0);
                if (tmdbId <= 0) {
                    continue;
                }
                Title t = new Title();
                String type = it.optString("type");
                t.type = "show".equals(type) || "tv".equals(type) || "series".equals(type) ? Title.TV : Title.MOVIE;
                t.id = tmdbId;
                t.name = it.optString("title");
                t.overview = it.optString("description");
                t.year = it.optInt("year", 0) > 0 ? String.valueOf(it.optInt("year")) : "";
                t.rating = it.optDouble("score", 0);
                t.poster = tmdbPath(it.optString("poster", null));
                if (t.poster != null) {
                    out.add(t);
                }
            }
            cb.done(out);
        });
    }

    /** "https://image.tmdb.org/t/p/w200/abc.jpg" to "/abc.jpg", so any size can be asked for. */
    private static String tmdbPath(String url) {
        if (url == null) {
            return null;
        }
        int i = url.indexOf("/t/p/");
        if (i < 0) {
            return null;
        }
        int slash = url.indexOf('/', i + 5);
        return slash < 0 ? null : url.substring(slash);
    }
}
