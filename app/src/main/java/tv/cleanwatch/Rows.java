package tv.cleanwatch;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen is a vertical list of rows; each row is a titled horizontal list of cards, or a
 * plain view (the hero, buttons, a message). The lists are Leanback grids: they recycle their
 * views, keep the focused card at a fixed place and remember where each row was left.
 */
final class Rows {
    static final int CARDS = 0;
    static final int VIEW = 1;

    private Rows() {
    }

    static final class Row {
        final int kind;
        String title;
        boolean chevron;
        int actionIcon;
        View.OnClickListener action;
        RecyclerView.Adapter<?> adapter;
        View view;
        int selected;
        int height;   // of the cards, in dp

        private Row(int kind) {
            this.kind = kind;
        }

        static Row cards(String title, RecyclerView.Adapter<?> adapter, int cardHeightDp) {
            Row r = new Row(CARDS);
            r.title = title;
            r.adapter = adapter;
            r.height = cardHeightDp;
            return r;
        }

        static Row view(View view) {
            Row r = new Row(VIEW);
            r.view = view;
            return r;
        }
    }

    static final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Row> rows = new ArrayList<>();
        private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

        Adapter() {
            pool.setMaxRecycledViews(10, 40);
            pool.setMaxRecycledViews(11, 12);
        }

        void set(List<Row> list) {
            rows.clear();
            rows.addAll(list);
            notifyDataSetChanged();
        }

        void changed(Row r) {
            int i = rows.indexOf(r);
            if (i >= 0) {
                notifyItemChanged(i);
            }
        }

        void remove(Row r) {
            int i = rows.indexOf(r);
            if (i >= 0) {
                rows.remove(i);
                notifyItemRemoved(i);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).kind;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == VIEW) {
                FrameLayout box = new FrameLayout(c);
                box.setClipChildren(false);
                box.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(box) {
                };
            }
            return new CardsHolder(c, pool);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Row row = rows.get(position);
            if (h instanceof CardsHolder) {
                ((CardsHolder) h).bind(row);
                return;
            }
            FrameLayout box = (FrameLayout) h.itemView;
            if (row.view.getParent() != box) {
                box.removeAllViews();
                ViewParent p = row.view.getParent();
                if (p instanceof ViewGroup) {
                    ((ViewGroup) p).removeView(row.view);
                }
                box.addView(row.view);
            }
        }
    }

    static final class CardsHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView chevron;
        final ImageView action;
        final HorizontalGridView grid;
        Row row;

        CardsHolder(Context c, RecyclerView.RecycledViewPool pool) {
            super(new LinearLayout(c));
            LinearLayout box = (LinearLayout) itemView;
            box.setOrientation(LinearLayout.VERTICAL);
            box.setClipChildren(false);
            box.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));

            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ui.dp(Ui.EDGE), 0, Ui.dp(Ui.EDGE), 0);
            title = Ui.text(c, 14.5f, Ui.TEXT, true);
            head.addView(title, new LinearLayout.LayoutParams(-2, -2));
            chevron = Cards.icon(c, R.drawable.ic_chevron_right, 12, Ui.TEXT_2);
            ((LinearLayout.LayoutParams) chevron.getLayoutParams()).leftMargin = Ui.dp(5);
            head.addView(chevron);
            head.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
            action = new ImageView(c);
            action.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            action.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
            action.setFocusable(true);
            action.setOnFocusChangeListener((v, has) -> {
                action.setBackground(has ? Ui.pill(0xFFFFFFFF, 14) : null);
                action.setColorFilter(has ? 0xFF000000 : 0xFFFFFFFF);
            });
            action.setOnClickListener(v -> {
                if (row != null && row.action != null) row.action.onClick(v);
            });
            head.addView(action, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
            box.addView(head, new LinearLayout.LayoutParams(-1, Ui.dp(26)));

            grid = new HorizontalGridView(c);
            grid.setHorizontalSpacing(Ui.dp(10));
            // Room around the cards for the focus zoom.
            grid.setPadding(Ui.dp(Ui.EDGE), Ui.dp(14), Ui.dp(Ui.EDGE), Ui.dp(16));
            grid.setClipToPadding(false);
            grid.setClipChildren(false);
            // Keep the focused card at the left edge; the row slides under it.
            grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
            grid.setWindowAlignmentOffset(Ui.dp(Ui.EDGE));
            grid.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            grid.setItemAlignmentOffsetPercent(0);
            grid.setRecycledViewPool(pool);
            grid.setHasFixedSize(true);
            grid.setOnChildSelectedListener((parent, view, pos, id) -> {
                if (row != null) {
                    row.selected = pos;
                }
            });
            box.addView(grid, new LinearLayout.LayoutParams(-1, -2));

            // A row with a button beside its title (Continue Watching's pencil): Up from the
            // cards goes to the button, Down from the button back to the cards.
            grid.setOnKeyInterceptListener(e -> {
                if (e.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && e.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP
                        && action.getVisibility() == View.VISIBLE) {
                    action.requestFocus();
                    return true;
                }
                return false;
            });
            action.setOnKeyListener((v, code, e) -> {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    grid.requestFocus();
                    return true;
                }
                return false;
            });
        }

        void bind(Row r) {
            row = r;
            title.setText(r.title);
            boolean hasTitle = r.title != null && !r.title.isEmpty();
            ((View) title.getParent()).setVisibility(hasTitle ? View.VISIBLE : View.GONE);
            chevron.setVisibility(r.chevron ? View.VISIBLE : View.GONE);
            if (r.actionIcon != 0) {
                action.setImageResource(r.actionIcon);
                action.setVisibility(View.VISIBLE);
            } else {
                action.setVisibility(View.GONE);
            }
            ViewGroup.LayoutParams lp = grid.getLayoutParams();
            lp.height = Ui.dp(r.height + 30);
            grid.setLayoutParams(lp);
            grid.setHorizontalSpacing(Ui.dp(r.adapter instanceof Cards.ProviderAdapter ? 4 : 10));
            if (grid.getAdapter() != r.adapter) {
                grid.setAdapter(r.adapter);
            }
            grid.setSelectedPosition(Math.min(r.selected, Math.max(0, r.adapter.getItemCount() - 1)));
        }
    }

    /** A vertical list of rows that keeps the focused row at the given distance from its top. */
    static VerticalGridView list(Context c, int focusTopDp) {
        VerticalGridView list = new VerticalGridView(c);
        list.setVerticalSpacing(Ui.dp(14));
        list.setClipToPadding(false);
        list.setClipChildren(false);
        list.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        list.setWindowAlignmentOffset(Ui.dp(focusTopDp));
        list.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        list.setItemAlignmentOffsetPercent(0);
        list.setItemViewCacheSize(4);
        return list;
    }
}
