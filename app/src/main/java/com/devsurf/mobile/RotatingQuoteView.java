package com.devsurf.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Small upward-sliding home quotes; work stops when the home page is hidden. */
final class RotatingQuoteView extends FrameLayout {
    private static final String[] QUOTES = {
        "Your space to explore.",
        "Stay curious. Keep exploring.",
        "A new tab. A new possibility.",
        "Discover something worth saving.",
        "Big ideas start with curiosity."
    };
    private final TextView quote;
    private int index;
    private boolean attached;
    private final Runnable advance = this::slideUp;

    RotatingQuoteView(Context context, int color) {
        super(context); setClipChildren(true); setClipToPadding(true);
        quote = new TextView(context); quote.setText(QUOTES[0]); quote.setTextColor(color); quote.setTextSize(15);
        quote.setGravity(Gravity.CENTER); quote.setIncludeFontPadding(false); quote.setMaxLines(2);
        addView(quote, new LayoutParams(-1, -1));
        // Reserve two text lines at the user's font size so changing quotes never shifts the page.
        setMinimumHeight(Math.round(quote.getLineHeight() * 2f));
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; schedule(); }
    @Override protected void onDetachedFromWindow() { attached = false; stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (quote == null) return;
        stop(); if (visibility == VISIBLE) schedule();
    }
    private void schedule() {
        removeCallbacks(advance);
        if (attached && getWindowVisibility() == View.VISIBLE && ValueAnimator.areAnimatorsEnabled()) postDelayed(advance, 4500);
    }
    private void stop() {
        removeCallbacks(advance); quote.animate().cancel(); quote.setAlpha(1f); quote.setTranslationY(0f);
    }
    private void slideUp() {
        if (!attached || getWindowVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) return;
        float distance = Math.max(getHeight(), quote.getLineHeight());
        quote.animate().translationY(-distance).alpha(0f).setDuration(220)
            .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
                if (!attached || getWindowVisibility() != VISIBLE) return;
                index = (index + 1) % QUOTES.length; quote.setText(QUOTES[index]); quote.setTranslationY(distance);
                quote.animate().translationY(0f).alpha(1f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).withEndAction(this::schedule).start();
            }).start();
    }
}
