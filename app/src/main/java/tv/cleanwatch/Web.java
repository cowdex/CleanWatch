package tv.cleanwatch;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * The one browser view, kept alive between films so Play starts like on the website.
 *
 * Opening cinejoy from nothing costs the TV about 15 seconds: setting up a browser view, then
 * loading and running the site's code (90 script files) before the player even asks for the
 * video. So while you browse the app's own screens, a light cinejoy page (its player settings
 * page) is loaded here in the background with the player code fetched in advance, then paused.
 * Play attaches this view to the player screen and moves inside the already-running site to the
 * film, the way a click on cinejoy's own page does. Closing the player hands the view back.
 *
 * The view is created with a context it can switch: the app while it waits, the player screen
 * while it is on screen (pickers and full screen need a screen to belong to).
 */
final class Web {
    private static final String TAG = "CleanWatch";
    /** A light cinejoy page to wait on (few elements, no autoplaying trailers). */
    static final String WARM_PATH = "/settings/player";
    /** How long the page may settle (and fetch the player's code) before it is paused. */
    private static final long PARK_MS = 20000;

    static WebView view;
    static MainActivity host;
    /** cinejoy's code is running in the view, so moving to a film needs no page load. */
    static boolean ready;

    private static MutableContextWrapper context;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable PARK = () -> {
        if (host == null && view != null) {
            // Pauses animations and media only; the page's code keeps working, so nothing it was
            // still fetching is cut off.
            view.onPause();
            Log.i(TAG, "Warm page paused in the background");
        }
    };

    private Web() {
    }

    static String site() {
        return BuildConfig.HOME_URL.replaceAll("/+$", "");
    }

    /** Called by the start screen: get cinejoy running in the background. */
    static void warm(Context c) {
        if (view != null) {
            return;
        }
        create(c.getApplicationContext());
        MainActivity.installPageScript(c, null);
        view.loadUrl(site() + WARM_PATH);
        Log.i(TAG, "Warming cinejoy in the background");
    }

    private static void create(Context base) {
        MainActivity.ensureLists(base);
        context = new MutableContextWrapper(base);
        view = new WebView(context);
        ready = false;
        MainActivity.configure(view);
    }

    /**
     * Puts the view into the player screen.
     *
     * @return true when cinejoy is already running in it
     */
    static boolean attach(MainActivity a, FrameLayout root) {
        HANDLER.removeCallbacks(PARK);
        host = a;
        if (view == null) {
            create(a);
            root.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));
            return false;
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
        context.setBaseContext(a);
        root.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));
        view.resumeTimers();
        view.onResume();
        return ready;
    }

    /** Takes the view back when the player screen closes, and leaves the film for the light page. */
    static void detach(MainActivity a) {
        if (host != a || view == null) {
            return;
        }
        host = null;
        view.evaluateJavascript("window.__cw&&__cw.leave&&__cw.leave()", null);
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
        context.setBaseContext(a.getApplicationContext());
        HANDLER.postDelayed(PARK, PARK_MS);
    }

    static void pageFinished(String url) {
        if (url != null && url.startsWith(site())) {
            ready = true;
        }
        if (host == null && view != null) {
            // Fetch the player's code now, so the first film doesn't wait for it.
            view.evaluateJavascript("window.__cw&&__cw.warm&&__cw.warm()", null);
            HANDLER.removeCallbacks(PARK);
            HANDLER.postDelayed(PARK, PARK_MS);
        }
    }

    /** The page's process died (usually low memory): start again next time. */
    static void lost() {
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeView(view);
            }
            view.destroy();
        }
        view = null;
        ready = false;
    }

    /** The app is closing: free the browser. */
    static void release() {
        if (host != null) {
            return;
        }
        HANDLER.removeCallbacks(PARK);
        lost();
    }
}
