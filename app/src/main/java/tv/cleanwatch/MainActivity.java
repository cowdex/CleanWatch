package tv.cleanwatch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One full-screen WebView showing {@link BuildConfig#HOME_URL}, with ads blocked and the
 * remote's D-pad turned into focus moves, taps and video controls (see assets/cleanwatch.js).
 */
public class MainActivity extends Activity {
    /** A page to open instead of the site's start page (the app's own screens use it for Play). */
    static final String EXTRA_URL = "tv.cleanwatch.URL";
    /** Subtitle language to switch on in cinejoy's player ("en", "fr", "ar"). */
    static final String EXTRA_SUBTITLES = "tv.cleanwatch.SUBTITLES";
    private static final double SUBTITLE_STEP = 0.5;

    private static final String TAG = "CleanWatch";
    private static final String[] BLOCKLISTS = {"blocklist.txt", "blocklist-pgl.txt"};

    /** Page layout widths in CSS pixels; wider means smaller content. */
    private static final int[] ZOOM_WIDTHS = {1024, 1280, 1600, 1920};
    private static final String[] ZOOM_LABELS = {"Large", "Normal", "Small", "Smallest"};
    private static final int ZOOM_DEFAULT = 1;
    private static final float[] MARGINS = {0f, 0.025f, 0.05f};
    private static final String[] MARGIN_LABELS = {"None", "Small", "Large"};

    private static final long LONG_PRESS_MS = 600;
    private static final long EXIT_WINDOW_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AdBlocker blocker = new AdBlocker();
    private final Set<String> allowedHosts = new HashSet<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView web;
    private PointerOverlay pointer;
    private ProgressBar progress;
    private TextView errorView;

    private String pageScript = "";
    private ScriptHandler scriptHandler;
    private boolean documentStartSupported;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private boolean pointerMode;
    private boolean okHeld;
    private int okSeq;
    private boolean touching;
    private long touchDownTime;
    private float touchX;
    private float touchY;
    private boolean hovering;
    private float hoverX;
    private float hoverY;
    private boolean arrowPending;
    private boolean backLongHandled;
    private long lastBackAtRoot;
    /** Opened to play one title: leaving that page returns to the app's own screens. */
    private boolean playerMode;
    private String subtitleLanguage;
    private final Runnable backLongPress = () -> {
        backLongHandled = true;
        showMenu();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("cleanwatch", MODE_PRIVATE);
        pointerMode = prefs.getBoolean("pointer", false);

        for (String host : BuildConfig.ALLOWED_HOSTS.split(",")) {
            if (!host.trim().isEmpty()) {
                allowedHosts.add(host.trim().toLowerCase(Locale.ROOT));
            }
        }
        String homeHost = Uri.parse(BuildConfig.HOME_URL).getHost();
        if (homeHost != null) {
            allowedHosts.add(homeHost.toLowerCase(Locale.ROOT));
        }
        for (String list : BLOCKLISTS) {
            try (InputStream in = getAssets().open(list)) {
                blocker.load(in);
            } catch (IOException e) {
                Log.w(TAG, "Blocklist not loaded: " + list);
            }
        }
        Log.i(TAG, blocker.size() + " blocked hosts loaded");

        String startUrl = getIntent().getStringExtra(EXTRA_URL);
        playerMode = startUrl != null;
        subtitleLanguage = getIntent().getStringExtra(EXTRA_SUBTITLES);

        buildViews();
        configureWebView();
        applyMargin();
        installPageScript();
        hideSystemBars();

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(playerMode ? startUrl : BuildConfig.HOME_URL);
        }
        if (pointerMode) {
            root.post(() -> setPointerMode(true, false));
        }
    }

    // ------------------------------------------------------------------ views

    private void buildViews() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pointer = new PointerOverlay(this);
        root.addView(pointer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));

        errorView = new TextView(this);
        errorView.setTextColor(Color.WHITE);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        errorView.setGravity(Gravity.CENTER);
        errorView.setBackgroundColor(Color.BLACK);
        errorView.setPadding(dp(48), dp(48), dp(48), dp(48));
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        // No extra windows: a pop-up becomes a navigation of this view, which the host
        // check in allowNavigation() then refuses.
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The page is the whole app: keep its process at full priority.
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        web.addJavascriptInterface(new Bridge(), "CleanWatchBridge");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                Log.i(TAG, "Download ignored: " + url));

        // The site's service worker fetches on its own thread; filter those requests too.
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                return filter(request.getUrl());
            }
        });
    }

    private void installPageScript() {
        String config = "window.__cwConfig={viewportWidth:" + ZOOM_WIDTHS[zoomIndex()]
                + ",lite:" + prefs.getBoolean("lite", false)
                + ",ring:" + prefs.getBoolean("ring", false)
                + (subtitleLanguage != null && subtitleLanguage.matches("[a-z]{2,3}")
                        ? ",subLang:'" + subtitleLanguage + "'" : "")
                + "};";
        pageScript = config + readAsset("cleanwatch.js");
        documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (documentStartSupported) {
            if (scriptHandler != null) {
                scriptHandler.remove();
            }
            scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    web, pageScript, Collections.singleton("*"));
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing asset " + name, e);
        }
    }

    private int zoomIndex() {
        int i = prefs.getInt("zoom", ZOOM_DEFAULT);
        return i >= 0 && i < ZOOM_WIDTHS.length ? i : ZOOM_DEFAULT;
    }

    private int marginIndex() {
        int i = prefs.getInt("margin", 0);
        return i >= 0 && i < MARGINS.length ? i : 0;
    }

    /** Overscan compensation for TVs that crop the picture edges. */
    private void applyMargin() {
        float m = MARGINS[marginIndex()];
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        root.setPadding(Math.round(w * m), Math.round(h * m), Math.round(w * m), Math.round(h * m));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // ------------------------------------------------------------- ad blocking

    private boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if (h.equals(allowed) || h.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /** Returns an empty response for a request to a blocked host, or null to let it through. */
    private WebResourceResponse filter(Uri url) {
        String host = url.getHost();
        if (host == null || isAllowedHost(host) || !blocker.matches(host)) {
            return null;
        }
        blocker.countBlocked();
        Log.d(TAG, "Blocked: " + host);
        return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    /** The top-level page stays on the allowed hosts; frames may load anything not blocked. */
    private boolean allowNavigation(Uri url, boolean mainFrame) {
        String scheme = url.getScheme();
        boolean http = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        if (!mainFrame) {
            // about:blank, blob: and data: frames are normal page plumbing.
            return http ? !blocker.matches(url.getHost()) : !"intent".equalsIgnoreCase(scheme);
        }
        if (http && isAllowedHost(url.getHost())) {
            return true;
        }
        blocker.countBlocked();
        Log.i(TAG, "Navigation refused: " + url);
        return false;
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return filter(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !allowNavigation(request.getUrl(), request.isForMainFrame());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            errorView.setVisibility(View.GONE);
            if (!documentStartSupported) {
                view.evaluateJavascript(pageScript, null);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!documentStartSupported) {
                view.evaluateJavascript(pageScript, null);
            }
            if (pointerMode) {
                view.evaluateJavascript("window.__cw&&__cw.setPointerMode&&__cw.setPointerMode(true)", null);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showError(String.valueOf(error.getDescription()));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The page's process died (usually low memory). Start over instead of crashing;
            // onDestroy() disposes of this WebView.
            Log.w(TAG, "Render process gone; recreating");
            recreate();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pointer.bringToFront();
            hideSystemBars();
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) {
                return;
            }
            root.removeView(customView);
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
            web.requestFocus();
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                android.os.Message resultMsg) {
            blocker.countBlocked();
            return false;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            result.confirm(); // never trap the viewer in a "leave this page?" prompt
            return true;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // no grey play icon
        }
    }

    /** Called by the page script. Every frame can reach it, so nothing here may do harm. */
    private final class Bridge {
        @JavascriptInterface
        public void setPlaying(final boolean playing) {
            runOnUiThread(() -> {
                if (playing) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        /** Parks the invisible mouse at a point (fractions of the page), so the site shows its hover effect. */
        @JavascriptInterface
        public void hover(final double fx, final double fy) {
            if (Double.isNaN(fx) || Double.isNaN(fy)) {
                return;
            }
            runOnUiThread(() -> {
                if (pointerMode) {
                    return;
                }
                View target = touchTarget();
                hoverX = (float) Math.max(0, Math.min(1, fx)) * (target.getWidth() - 1);
                hoverY = (float) Math.max(0, Math.min(1, fy)) * (target.getHeight() - 1);
                hovering = true;
                sendMouse(MotionEvent.ACTION_HOVER_MOVE, hoverX, hoverY, 0, 0);
            });
        }

        /** cinejoy's watch progress, for the app's Continue Watching row. */
        @JavascriptInterface
        public void saveProgress(final String json) {
            if (json != null && json.length() < 2_000_000) {
                Progress.save(MainActivity.this, json);
            }
        }

        /** Result of a subtitle timing change, shown as a short message. */
        @JavascriptInterface
        public void subtitleStatus(final String status, final String value) {
            runOnUiThread(() -> showSubtitleStatus(status, value));
        }

        /** Takes the invisible mouse off the page. */
        @JavascriptInterface
        public void unhover() {
            runOnUiThread(() -> {
                if (hovering && !pointerMode) {
                    sendMouse(MotionEvent.ACTION_HOVER_EXIT, hoverX, hoverY, 0, 0);
                }
                hovering = false;
            });
        }
    }

    private void showError(String description) {
        errorView.setText(getString(R.string.error_title) + "\n\n" + description + "\n\n"
                + getString(R.string.error_retry));
        errorView.setVisibility(View.VISIBLE);
    }

    // -------------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_BACK) {
            return handleBack(event);
        }
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (!down) {
                showMenu();
            }
            return true;
        }
        if (errorView.getVisibility() == View.VISIBLE) {
            if (isOk(code)) {
                if (!down) {
                    errorView.setVisibility(View.GONE);
                    web.reload();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) arrow("up", 0, -1, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) arrow("down", 0, 1, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) arrow("left", -1, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) arrow("right", 1, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (down && event.getRepeatCount() == 0) pageKey("playpause", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (down && event.getRepeatCount() == 0) pageKey("play", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (down && event.getRepeatCount() == 0) pageKey("pause", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) pageKey("ff", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) pageKey("rw", null);
                return true;
            default:
                if (isOk(code)) {
                    handleOk(event);
                    return true;
                }
                return super.dispatchKeyEvent(event);
        }
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    /** Sends a key to the page script; the callback gets its answer ('' / 'tap:x,y' / 'back'). */
    private void pageKey(String name, ValueCallback<String> callback) {
        web.evaluateJavascript("window.__cw?__cw.key('" + name + "'):'nojs'", value -> {
            String result = unquote(value);
            if ("nojs".equals(result)) {
                // Script not in this page yet (old WebView without document-start support).
                web.evaluateJavascript(pageScript, null);
            }
            if (callback != null) {
                callback.onReceiveValue(result);
            }
        });
    }

    private static String unquote(String json) {
        if (json == null || "null".equals(json)) {
            return "";
        }
        if (json.length() >= 2 && json.charAt(0) == '"') {
            return json.substring(1, json.length() - 1);
        }
        return json;
    }

    private void arrow(String name, int dx, int dy, int repeat) {
        if (!pointerMode) {
            // While a key is held, skip repeats the page has not caught up with, so the
            // selection stops when the key is released instead of running on.
            if (repeat > 0 && arrowPending) {
                return;
            }
            arrowPending = true;
            pageKey(name, result -> arrowPending = false);
            return;
        }
        int[] edge = pointer.move(dx, dy, repeat);
        float x = pointer.pointerX();
        float y = pointer.pointerY();
        if (touching) {
            sendTouch(MotionEvent.ACTION_MOVE, x, y);
            return;
        }
        sendMouse(MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0);
        if (edge[0] != 0 || edge[1] != 0) {
            // Pushing against a screen edge scrolls whatever is under the pointer.
            sendMouse(MotionEvent.ACTION_SCROLL, x, y, edge[0], -edge[1]);
        }
    }

    private void handleOk(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return;
            }
            okHeld = true;
            if (pointerMode) {
                sendTouch(MotionEvent.ACTION_DOWN, pointer.pointerX(), pointer.pointerY());
                return;
            }
            final int seq = ++okSeq;
            pageKey("ok", result -> {
                if (seq == okSeq && touchDownAt(result) && !okHeld) {
                    // Key already released: finish the tap after a beat so it registers.
                    handler.postDelayed(this::releaseTouch, 50);
                }
            });
        } else {
            okHeld = false;
            releaseTouch();
        }
    }

    /** Starts a touch where the page script asked for one ("tap:x,y", fractions of the page). */
    private boolean touchDownAt(String result) {
        if (!result.startsWith("tap:")) {
            return false;
        }
        String[] xy = result.substring(4).split(",");
        if (xy.length != 2) {
            return false;
        }
        try {
            View target = touchTarget();
            sendTouch(MotionEvent.ACTION_DOWN,
                    Float.parseFloat(xy[0]) * target.getWidth(),
                    Float.parseFloat(xy[1]) * target.getHeight());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void releaseTouch() {
        if (touching) {
            sendTouch(MotionEvent.ACTION_UP, touchX, touchY);
        }
    }

    /** While a video is in HTML fullscreen its view receives the input instead. */
    private View touchTarget() {
        return customView != null ? customView : web;
    }

    private void sendTouch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = now;
            touching = true;
        } else if (action == MotionEvent.ACTION_UP) {
            touching = false;
        }
        touchX = x;
        touchY = y;
        MotionEvent e = MotionEvent.obtain(touchDownTime, now, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        touchTarget().dispatchTouchEvent(e);
        e.recycle();
    }

    private void sendMouse(int action, float x, float y, float hscroll, float vscroll) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, hscroll);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll);
        MotionEvent e = MotionEvent.obtain(now, now, action, 1,
                new MotionEvent.PointerProperties[] {props}, new MotionEvent.PointerCoords[] {coords},
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        touchTarget().dispatchGenericMotionEvent(e);
        e.recycle();
    }

    // -------------------------------------------------------------------- back

    /** Short press: back. Held: the options menu (remotes rarely have a menu key). */
    private boolean handleBack(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                backLongHandled = false;
                // A timer rather than key repeats: not every remote sends repeats for BACK.
                handler.removeCallbacks(backLongPress);
                handler.postDelayed(backLongPress, LONG_PRESS_MS);
            } else if (!backLongHandled && event.isLongPress()) {
                handler.removeCallbacks(backLongPress);
                backLongPress.run();
            }
            return true;
        }
        handler.removeCallbacks(backLongPress);
        if (!backLongHandled && !event.isCanceled()) {
            goBack();
        }
        backLongHandled = false;
        return true;
    }

    private void goBack() {
        if (errorView.getVisibility() == View.VISIBLE) {
            historyBack();
            return;
        }
        // The page gets first refusal: close a dialog, leave fullscreen, drop the player controls.
        pageKey("back", result -> {
            if ("back".equals(result) || "nojs".equals(result)) {
                historyBack();
            } else if (touchDownAt(result)) {
                // A pop-up menu closes on a tap beside it.
                handler.postDelayed(this::releaseTouch, 50);
            }
        });
    }

    private void historyBack() {
        if (web.canGoBack()) {
            errorView.setVisibility(View.GONE);
            web.goBack();
            return;
        }
        if (playerMode) {
            finish();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAtRoot < EXIT_WINDOW_MS) {
            finish();
        } else {
            lastBackAtRoot = now;
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------- menu

    private void showMenu() {
        if (isFinishing()) {
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String url = web.getUrl();
        if (playerMode || (url != null && url.contains("/watch/"))) {
            // Subtitle timing, for subtitle files cut for a different release of the video.
            labels.add(getString(R.string.menu_subs_later));
            actions.add(() -> {
                subtitleCommand("shift", SUBTITLE_STEP);
                showMenu();
            });
            labels.add(getString(R.string.menu_subs_sooner));
            actions.add(() -> {
                subtitleCommand("shift", -SUBTITLE_STEP);
                showMenu();
            });
            labels.add(getString(R.string.menu_subs_reset));
            actions.add(() -> subtitleCommand("reset", 0));
        }
        labels.add(getString(R.string.menu_pointer, getString(pointerMode ? R.string.on : R.string.off)));
        actions.add(() -> setPointerMode(!pointerMode, true));
        labels.add(getString(R.string.menu_zoom, ZOOM_LABELS[zoomIndex()]));
        actions.add(() -> {
            prefs.edit().putInt("zoom", (zoomIndex() + 1) % ZOOM_WIDTHS.length).apply();
            installPageScript();
            web.reload();
            showMenu();
        });
        labels.add(getString(R.string.menu_margin, MARGIN_LABELS[marginIndex()]));
        actions.add(() -> {
            prefs.edit().putInt("margin", (marginIndex() + 1) % MARGINS.length).apply();
            applyMargin();
            showMenu();
        });
        labels.add(getString(R.string.menu_lite,
                getString(prefs.getBoolean("lite", false) ? R.string.on : R.string.off)));
        actions.add(() -> {
            prefs.edit().putBoolean("lite", !prefs.getBoolean("lite", false)).apply();
            installPageScript();
            web.reload();
            showMenu();
        });
        labels.add(getString(R.string.menu_ring,
                getString(prefs.getBoolean("ring", false) ? R.string.on : R.string.off)));
        actions.add(() -> {
            boolean ring = !prefs.getBoolean("ring", false);
            prefs.edit().putBoolean("ring", ring).apply();
            installPageScript();   // for the pages opened from now on
            web.evaluateJavascript("window.__cw&&__cw.setRing&&__cw.setRing(" + ring + ")", null);
            showMenu();
        });
        labels.add(getString(R.string.menu_reload));
        actions.add(web::reload);
        labels.add(getString(playerMode ? R.string.menu_back_to_app : R.string.menu_home));
        actions.add(() -> {
            if (playerMode) {
                finish();   // back to the app's own screens
            } else {
                web.loadUrl(BuildConfig.HOME_URL);
            }
        });
        labels.add(getString(R.string.menu_exit));
        actions.add(this::finishAffinity);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name) + "  ·  "
                        + getString(R.string.menu_blocked, blocker.blockedCount()))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setOnDismissListener(dialog -> hideSystemBars())
                .show();
    }

    private void subtitleCommand(String cmd, double arg) {
        web.evaluateJavascript("window.__cw&&__cw.subtitles&&__cw.subtitles('" + cmd + "'," + arg + ")", null);
    }

    private void showSubtitleStatus(String status, String value) {
        String text;
        switch (status == null ? "" : status) {
            case "ok":
                text = getString(R.string.subs_offset, signed(value));
                break;
            default:
                text = getString(R.string.subs_no_controls);
                break;
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** "1.5" to "+1.5 s", "-0.5" to "−0.5 s". */
    private static String signed(String value) {
        try {
            double v = Double.parseDouble(value);
            String n = String.format(Locale.US, "%.1f", Math.abs(v));
            return (v > 0 ? "+" : v < 0 ? "−" : "") + n + " s";
        } catch (Exception e) {
            return value + " s";
        }
    }

    private void setPointerMode(boolean on, boolean announce) {
        pointerMode = on;
        prefs.edit().putBoolean("pointer", on).apply();
        pointer.setActive(on);
        web.evaluateJavascript(
                "window.__cw&&__cw.setPointerMode&&__cw.setPointerMode(" + on + ")", null);
        if (announce) {
            Toast.makeText(this, on ? R.string.pointer_on : R.string.pointer_off, Toast.LENGTH_LONG).show();
        }
    }

    // --------------------------------------------------------------- lifecycle

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        // Keep the latest watch progress for Continue Watching.
        web.evaluateJavascript("(function(){try{return localStorage.getItem('Cinejoy::Progress')}catch(e){return null}})()",
                value -> {
                    try {
                        Object v = new org.json.JSONTokener(value).nextValue();
                        if (v instanceof String) {
                            Progress.save(this, (String) v);
                        }
                    } catch (Exception ignored) {
                    }
                });
        // Leaving the app (Home, another input): stop the film rather than play on unseen.
        web.evaluateJavascript("window.__cw&&__cw.pauseAll&&__cw.pauseAll()", null);
        web.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        root.removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
