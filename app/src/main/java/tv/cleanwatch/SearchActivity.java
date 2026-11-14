package tv.cleanwatch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;

import java.util.List;

/** Search films and shows by name with the TV keyboard; results appear as you type. */
public class SearchActivity extends Activity {
    private static final long TYPING_PAUSE_MS = 450;
    private static final int COLUMNS = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText field;
    private TextView label;
    private VerticalGridView grid;
    private Cards.PosterAdapter results;
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(R.string.search_hint);
        field.setHintTextColor(Ui.TEXT_3);
        field.setTextColor(Ui.TEXT);
        field.setTextSize(15);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setOnFocusChangeListener((v, has) ->
                field.setBackground(Ui.pill(has ? 0x40FFFFFF : Ui.SURFACE, 22, has ? 0xFFFFFFFF : Ui.LINE)));
        field.setBackground(Ui.pill(Ui.SURFACE, 22, Ui.LINE));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(520), Ui.dp(44));
        fp.leftMargin = Ui.dp(Ui.EDGE);
        fp.topMargin = Ui.dp(26);
        root.addView(field, fp);

        label = Ui.lines(this, 14, Ui.TEXT_2, 1);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = Ui.dp(Ui.EDGE);
        lp.rightMargin = Ui.dp(Ui.EDGE);
        lp.topMargin = Ui.dp(86);
        root.addView(label, lp);

        results = new Cards.PosterAdapter(new Cards.OnTitle() {
            @Override
            public void focused(Title t) {
                label.setText(t.name + (t.year.isEmpty() ? "" : "  ·  " + t.year)
                        + (t.isTv() ? "  ·  Show" : "  ·  Film"));
            }

            @Override
            public void clicked(Title t) {
                Nav.details(SearchActivity.this, t);
            }
        }, false);
        grid = new VerticalGridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setHorizontalSpacing(Ui.dp(22));
        grid.setVerticalSpacing(Ui.dp(22));
        grid.setPadding(Ui.dp(Ui.EDGE), Ui.dp(14), Ui.dp(Ui.EDGE), Ui.dp(30));
        grid.setClipToPadding(false);
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        grid.setWindowAlignmentOffsetPercent(35);
        grid.setAdapter(results);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -1);
        gp.topMargin = Ui.dp(108);
        root.addView(grid, gp);
        setContentView(root);

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> run(s.toString().trim()), TYPING_PAUSE_MS);
            }
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handler.removeCallbacksAndMessages(null);
                run(field.getText().toString().trim());
                hideKeyboard();
                if (results.getItemCount() > 0) {
                    grid.requestFocus();
                }
                return true;
            }
            return false;
        });

        run("");
        field.requestFocus();
        field.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Up from the first row of results goes back to the search box.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && grid.hasFocus() && grid.getSelectedPosition() < COLUMNS) {
            field.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void run(String query) {
        if (query.equals(lastQuery) && results.getItemCount() > 0) {
            return;
        }
        lastQuery = query;
        Api.Cb<List<Title>> show = items -> {
            if (!query.equals(lastQuery)) {
                return;   // a newer search has started
            }
            results.set(items);
            grid.setSelectedPosition(0);
            if (items == null || items.isEmpty()) {
                label.setText(query.isEmpty() ? "" : getString(R.string.search_none, query));
            } else {
                label.setText(query.isEmpty() ? getString(R.string.search_trending) : "");
            }
        };
        if (query.isEmpty()) {
            Api.list(Api.tmdb("trending/all/week"), null, show);
        } else {
            Api.search(query, show);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }
}
