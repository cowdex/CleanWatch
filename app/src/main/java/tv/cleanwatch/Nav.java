package tv.cleanwatch;

import android.app.Activity;
import android.content.Intent;

/** Moving between the screens, and handing a title to cinejoy's player. */
final class Nav {
    static final String EXTRA_TITLE = "tv.cleanwatch.TITLE";

    private Nav() {
    }

    static void details(Activity a, Title t) {
        a.startActivity(new Intent(a, DetailsActivity.class).putExtra(EXTRA_TITLE, t.toJson().toString()));
    }

    /** Plays a film, or a show's given episode (season and episode 0 mean "where I left off"). */
    static void play(Activity a, Title t, int season, int episode) {
        Title p = Title.fromJson(t.toJson());
        p.logo = t.logo;
        if (p.lang == null) p.lang = t.lang;
        if (p.isTv()) {
            if (season <= 0) {
                Title last = Store.lastPlayed(a, t);
                season = last != null ? last.season : 1;
                episode = last != null ? last.episode : 1;
            }
            p.season = season;
            p.episode = Math.max(1, episode);
        }
        if (p.lang != null) {
            open(a, p);
        } else {
            // Lists from cinejoy's server don't say the language; TMDB does (usually cached already).
            Api.details(p, d -> open(a, p));
        }
    }

    private static void open(Activity a, Title p) {
        if (a.isFinishing()) {
            return;
        }
        Store.played(a, p);
        Intent i = new Intent(a, MainActivity.class).putExtra(MainActivity.EXTRA_URL, p.watchUrl());
        String subs = p.subtitleLanguage();
        if (subs != null) {
            i.putExtra(MainActivity.EXTRA_SUBTITLES, subs);
        }
        // For the loading screen.
        i.putExtra(MainActivity.EXTRA_NAME, p.name)
                .putExtra(MainActivity.EXTRA_LINE, p.isTv() ? "S" + p.season + " · E" + p.episode : p.year)
                .putExtra(MainActivity.EXTRA_BACKDROP, p.backdrop)
                .putExtra(MainActivity.EXTRA_LOGO, p.logo);
        a.startActivity(i);
    }

    /** The whole cinejoy site in the browser view, as before. */
    static void website(Activity a) {
        a.startActivity(new Intent(a, MainActivity.class));
    }
}
