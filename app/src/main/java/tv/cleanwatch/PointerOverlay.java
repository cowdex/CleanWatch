package tv.cleanwatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** An on-screen pointer moved with the D-pad, for anything focus navigation can't reach. */
final class PointerOverlay extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float dp;
    private float x = -1;
    private float y = -1;

    PointerOverlay(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFF000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * dp);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        // classic arrow, tip at (0, 0)
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 22 * dp);
        arrow.lineTo(6 * dp, 16.5f * dp);
        arrow.lineTo(10.5f * dp, 26 * dp);
        arrow.lineTo(14 * dp, 24.5f * dp);
        arrow.lineTo(9.5f * dp, 15 * dp);
        arrow.lineTo(17 * dp, 15 * dp);
        arrow.close();
        // INVISIBLE, not GONE: the view must be measured so the pointer can start in the centre.
        setVisibility(INVISIBLE);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (x < 0 || y < 0 || x >= w || y >= h) {
            x = w / 2f;
            y = h / 2f;
        }
    }

    void setActive(boolean active) {
        setVisibility(active ? VISIBLE : INVISIBLE);
        invalidate();
    }

    float pointerX() {
        return x;
    }

    float pointerY() {
        return y;
    }

    /**
     * Moves the pointer one step; held keys speed up.
     *
     * @return which edge it is pushing against, as {dx, dy} with values -1, 0 or 1
     */
    int[] move(int dx, int dy, int repeat) {
        float step = (12 + Math.min(repeat, 24) * 2.2f) * dp;
        float nx = x + dx * step;
        float ny = y + dy * step;
        int[] edge = {0, 0};
        float maxX = Math.max(0, getWidth() - 1);
        float maxY = Math.max(0, getHeight() - 1);
        if (nx < 0) {
            nx = 0;
            edge[0] = -1;
        } else if (nx > maxX) {
            nx = maxX;
            edge[0] = 1;
        }
        if (ny < 0) {
            ny = 0;
            edge[1] = -1;
        } else if (ny > maxY) {
            ny = maxY;
            edge[1] = 1;
        }
        x = nx;
        y = ny;
        invalidate();
        return edge;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, stroke);
        canvas.drawPath(arrow, fill);
        canvas.restore();
    }
}
