package com.devsurf.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

/** Shared appearance for every app-owned menu, input, list and confirmation. */
final class Dialogs {
    private Dialogs() {}

    static AlertDialog.Builder builder(Activity activity, boolean light) {
        return new AlertDialog.Builder(activity, light ? R.style.DevSurfDialogLight : R.style.DevSurfDialog) {
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create();
                Window window = dialog.getWindow();
                if (window != null) {
                    window.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View view) {
                            view.post(() -> {
                                if (!dialog.isShowing()) return;
                                int available = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 40);
                                window.setLayout(Math.min(available, dp(activity, 420)), ViewGroup.LayoutParams.WRAP_CONTENT);
                                window.setBackgroundDrawableResource(light ? R.drawable.dialog_panel_light : R.drawable.dialog_panel);
                                window.getDecorView().setClipToOutline(true);
                                styleTree(activity, window.getDecorView(), light);
                                ListView list = dialog.getListView();
                                if (list != null) {
                                    list.setSelector(R.drawable.tap_feedback);
                                    list.setDivider(null);
                                    list.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 12));
                                    list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                                        @Override public void onChildViewAdded(View parent, View child) { styleTree(activity, child, light); }
                                        @Override public void onChildViewRemoved(View parent, View child) {}
                                    });
                                }
                            });
                        }
                        @Override public void onViewDetachedFromWindow(View view) { view.removeOnAttachStateChangeListener(this); }
                    });
                }
                return dialog;
            }
        };
    }

    private static int dp(Activity a, int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    private static void styleTree(Activity activity, View view, boolean light) {
        int ink = Color.parseColor(light ? "#161616" : "#f5f5f5");
        int muted = Color.parseColor(light ? "#656565" : "#8d8d8d");
        int border = Color.parseColor(light ? "#d5d5d5" : "#292929");
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            text.setTextColor(ink);
            if (view instanceof Button) {
                Button button = (Button)view;
                button.setAllCaps(false); button.setTextSize(14);
                button.setTypeface(null, Typeface.NORMAL);
                button.setMinHeight(dp(activity, 44));
                button.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
                GradientDrawable surface = new GradientDrawable();
                surface.setColor(Color.parseColor(light ? "#eeeeee" : "#202020"));
                surface.setCornerRadius(dp(activity, 8)); surface.setStroke(dp(activity, 1), border);
                button.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33808080), surface, null));
                if (button.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)button.getLayoutParams();
                    params.setMarginStart(dp(activity, 6)); params.bottomMargin = dp(activity, 8); button.setLayoutParams(params);
                }
            } else if (view instanceof EditText) {
                text.setTextSize(15); text.setHintTextColor(muted);
                text.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
                GradientDrawable surface = new GradientDrawable();
                surface.setColor(Color.parseColor(light ? "#fafafa" : "#080808"));
                surface.setCornerRadius(dp(activity, 10)); surface.setStroke(dp(activity, 1), border);
                text.setBackground(surface);
            } else if (view.getId() == android.R.id.message) {
                text.setTextSize(14); text.setTextColor(muted); text.setLineSpacing(dp(activity, 3), 1f);
            } else if (view.getId() == android.R.id.text1) {
                text.setTextSize(15); text.setMinHeight(dp(activity, 50));
                text.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
                text.setBackgroundResource(R.drawable.tap_feedback);
            } else if ("alertTitle".equals(resourceName(activity, view.getId()))) {
                text.setTextSize(20); text.setTypeface(null, Typeface.BOLD);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) styleTree(activity, group.getChildAt(i), light);
        }
    }
    private static String resourceName(Activity activity, int id) {
        try { return activity.getResources().getResourceEntryName(id); }
        catch (android.content.res.Resources.NotFoundException e) { return ""; }
    }
}
