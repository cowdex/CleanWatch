package tv.cleanwatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** A film or a show, as far as the screens need to know it. */
final class Title {
    static final String MOVIE = "movie";
    static final String TV = "tv";

    String type = MOVIE;
    int id;
    String name = "";
    String overview = "";
    String poster;     // TMDB image path, e.g. "/abc.jpg"
    String backdrop;
    String year = "";
    double rating;
    int rank;          // position in the Top 10, 0 otherwise
    String lang;       // original language, e.g. "en", "fr"; null when unknown

    // From the details request.
    boolean detailed;
    String logo;
    String genres = "";
    int runtime;       // minutes, films only
    int seasons;       // shows only

    // Where to resume a show (Continue Watching).
    int season;
    int episode;

    boolean isTv() {
        return TV.equals(type);
    }

    String key() {
        return type + ":" + id;
    }

    /** The cinejoy player page for this title. */
    String watchUrl() {
        String site = BuildConfig.HOME_URL.replaceAll("/+$", "");
        if (isTv()) {
            return site + "/watch/tv/" + id + "/" + Math.max(1, season) + "/" + Math.max(1, episode);
        }
        return site + "/watch/movie/" + id;
    }

    /**
     * The subtitles to turn on: English for English titles, French for French ones, Arabic for
     * everything else. Null when the language isn't known yet.
     */
    String subtitleLanguage() {
        if (lang == null) {
            return null;
        }
        switch (lang) {
            case "en":
                return "en";
            case "fr":
                return "fr";
            default:
                return "ar";
        }
    }

    /** "★ 7.4  ·  2026  ·  Action, Drama  ·  2h 10m" */
    String meta() {
        StringBuilder b = new StringBuilder();
        if (rating > 0) {
            b.append("★ ").append(String.format(Locale.US, "%.1f", rating));
        }
        add(b, year);
        add(b, genres);
        if (runtime > 0) {
            add(b, runtime >= 60 ? (runtime / 60) + "h " + (runtime % 60) + "m" : runtime + "m");
        }
        if (seasons > 0) {
            add(b, seasons == 1 ? "1 Season" : seasons + " Seasons");
        }
        return b.toString();
    }

    private static void add(StringBuilder b, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (b.length() > 0) {
            b.append("  ·  ");
        }
        b.append(part);
    }

    /** A result from a TMDB list, search or details request. */
    static Title fromTmdb(JSONObject o, String fallbackType) {
        Title t = new Title();
        t.type = o.optString("media_type", fallbackType);
        if (!MOVIE.equals(t.type) && !TV.equals(t.type)) {
            return null;   // people in search results
        }
        t.id = o.optInt("id");
        t.name = o.optString(t.isTv() ? "name" : "title", o.optString("name", o.optString("title")));
        t.overview = o.optString("overview");
        t.poster = path(o.optString("poster_path", null));
        t.backdrop = path(o.optString("backdrop_path", null));
        String date = o.optString(t.isTv() ? "first_air_date" : "release_date", "");
        t.year = date.length() >= 4 ? date.substring(0, 4) : "";
        t.rating = o.optDouble("vote_average", 0);
        t.lang = path(o.optString("original_language", null));
        return t.id > 0 ? t : null;
    }

    /** Adds what only the details request has: logo, genres, runtime, seasons. */
    void fill(JSONObject d) {
        Title base = fromTmdb(d, type);
        if (base != null) {
            if (!base.name.isEmpty()) name = base.name;
            if (!base.overview.isEmpty()) overview = base.overview;
            if (base.poster != null) poster = base.poster;
            if (base.backdrop != null) backdrop = base.backdrop;
            if (!base.year.isEmpty()) year = base.year;
            if (base.rating > 0) rating = base.rating;
            if (base.lang != null) lang = base.lang;
        }
        JSONArray g = d.optJSONArray("genres");
        if (g != null) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < g.length() && i < 2; i++) {
                if (b.length() > 0) b.append(", ");
                b.append(g.optJSONObject(i).optString("name"));
            }
            genres = b.toString();
        }
        runtime = d.optInt("runtime", 0);
        seasons = d.optInt("number_of_seasons", 0);
        JSONObject images = d.optJSONObject("images");
        JSONArray logos = images == null ? null : images.optJSONArray("logos");
        if (logos != null) {
            // Prefer an English logo; TMDB lists the best-voted first.
            for (int pass = 0; pass < 2 && logo == null; pass++) {
                for (int i = 0; i < logos.length(); i++) {
                    JSONObject l = logos.optJSONObject(i);
                    String lang = l.optString("iso_639_1", "");
                    if (pass == 0 ? "en".equals(lang) : (lang.isEmpty() || "null".equals(lang))) {
                        logo = path(l.optString("file_path", null));
                        break;
                    }
                }
            }
        }
        detailed = true;
    }

    static String path(String p) {
        return p == null || p.isEmpty() || "null".equals(p) ? null : p;
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type).put("id", id).put("name", name).put("year", year)
                    .put("rating", rating).put("season", season).put("episode", episode);
            if (poster != null) o.put("poster", poster);
            if (backdrop != null) o.put("backdrop", backdrop);
            if (lang != null) o.put("lang", lang);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Title fromJson(JSONObject o) {
        Title t = new Title();
        t.type = o.optString("type", MOVIE);
        t.id = o.optInt("id");
        t.name = o.optString("name");
        t.year = o.optString("year");
        t.rating = o.optDouble("rating", 0);
        t.season = o.optInt("season");
        t.episode = o.optInt("episode");
        t.poster = path(o.optString("poster", null));
        t.backdrop = path(o.optString("backdrop", null));
        t.lang = path(o.optString("lang", null));
        return t;
    }

    static final class Img {
        private static final String BASE = "https://image.tmdb.org/t/p/";

        private Img() {
        }

        static String poster(String p) {
            return p == null ? null : BASE + "w342" + p;
        }

        static String backdrop(String p) {
            return p == null ? null : BASE + "w1280" + p;
        }

        static String still(String p) {
            return p == null ? null : BASE + "w300" + p;
        }

        static String logo(String p) {
            return p == null ? null : BASE + "w500" + p;
        }
    }
}
