package com.excellentmindblow.navos.ui;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.enums.ThemeUsageContext;
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter;
import net.osmand.plus.widgets.ctxmenu.callback.ItemClickListener;
import net.osmand.plus.widgets.ctxmenu.callback.OnDataChangeUiAdapter;
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem;

import java.util.List;

/**
 * The main menu as a bottom sheet instead of OsmAnd's side drawer: profile
 * chips on top, then the same menu items as a grid of tiles. Items, their
 * order and what they do still come from OsmAnd's own main menu.
 *
 * MapActivity.openDrawer() calls {@link #show}.
 */
public final class NavMenuSheet {

    private static final int BLUE = 0xFF1278D8, NIGHT_BLUE = 0xFF4DA3FF;
    private static final int INK = 0xFF1B2433, NIGHT_INK = 0xFFFFFFFF;
    private static final int QUIET = 0xFF8A94A6, NIGHT_QUIET = 0xFF9FB0CC;
    private static final int SHEET = 0xFFFFFFFF, NIGHT_SHEET = 0xFF11203D;
    private static final int FIELD = 0xFFF0F3F8, NIGHT_FIELD = 0xFF1A2C4D;
    private static final int SELECTED = 0xFFE4F0FC, NIGHT_SELECTED = 0xFF17345C;
    private static final int HANDLE = 0xFFDCE3EE, NIGHT_HANDLE = 0xFF24395E;
    private static final int COLUMNS = 4;

    private final MapActivity activity;
    private final OsmandApplication app;
    private final boolean night;
    private final float density;
    private BottomSheetDialog dialog;
    private LinearLayout chips;

    private NavMenuSheet(MapActivity activity) {
        this.activity = activity;
        this.app = (OsmandApplication) activity.getApplication();
        this.night = app.getDaynightHelper().isNightMode(ThemeUsageContext.APP);
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    public static void show(MapActivity activity) {
        if (activity.isFinishing()) {
            return;
        }
        new NavMenuSheet(activity).open();
    }

    private void open() {
        // The app's shrunk Material copy only keeps (Context, int); 0 means its default sheet theme.
        dialog = new BottomSheetDialog(activity, 0);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(buildContent());
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            int id = activity.getResources().getIdentifier("design_bottom_sheet", "id", activity.getPackageName());
            View sheet = id != 0 ? dialog.findViewById(id) : null;
            if (sheet != null) {
                sheet.setBackgroundColor(0);  // the content draws the rounded sheet
            }
        });
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(3 /* STATE_EXPANDED */);
        dialog.show();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(24));
        root.setBackground(roundedTop(night ? NIGHT_SHEET : SHEET, dp(24)));

        View handle = new View(activity);
        handle.setBackground(rounded(night ? NIGHT_HANDLE : HANDLE, dp(2)));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(14);
        root.addView(handle, handleLp);

        TextView title = text(activity.getString(activity.getApplicationInfo().labelRes), 20, ink(), true);
        root.addView(title);

        chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(activity);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dp(12);
        chipLp.bottomMargin = dp(8);
        root.addView(chipScroll, chipLp);
        fillChips();

        ContextMenuAdapter adapter = activity.getMapActions().createMainOptionsMenu();
        List<ContextMenuItem> items = adapter.getVisibleItems();
        GridLayout grid = null;
        for (ContextMenuItem item : items) {
            if (item.isCategory() || TextUtils.isEmpty(item.getTitle())) {
                grid = null;  // a divider in the drawer starts a new group here
                continue;
            }
            if (grid == null) {
                grid = new GridLayout(activity);
                grid.setColumnCount(COLUMNS);
                LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gridLp.topMargin = dp(12);
                root.addView(grid, gridLp);
            }
            grid.addView(tile(item), tileParams());
        }
        return root;
    }

    private void fillChips() {
        chips.removeAllViews();
        OsmandSettings settings = app.getSettings();
        ApplicationMode current = settings.getApplicationMode();
        for (Object o : ApplicationMode.values(app)) {
            ApplicationMode mode = (ApplicationMode) o;
            boolean selected = mode == current;
            LinearLayout chip = new LinearLayout(activity);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), 0, dp(14), 0);
            GradientDrawable bg = rounded(selected ? (night ? NIGHT_SELECTED : SELECTED) : (night ? NIGHT_FIELD : FIELD), dp(12));
            if (selected) {
                bg.setStroke(dp(1), accent());
            }
            chip.setBackground(bg);
            ImageView icon = new ImageView(activity);
            Drawable d = drawable(mode.getIconRes());
            if (d != null) {
                d.setTint(selected ? accent() : quiet());
                icon.setImageDrawable(d);
            }
            chip.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = text(mode.toHumanString(), 14, selected ? accent() : ink(), selected);
            label.setPadding(dp(6), 0, 0, 0);
            chip.addView(label);
            chip.setOnClickListener(v -> {
                settings.setApplicationMode(mode);
                fillChips();
            });
            pressable(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.rightMargin = dp(8);
            chips.addView(chip, lp);
        }
    }

    private View tile(ContextMenuItem item) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(0, dp(6), 0, dp(6));

        FrameLayout well = new FrameLayout(activity);
        well.setBackground(rounded(night ? NIGHT_FIELD : FIELD, dp(16)));
        ImageView icon = new ImageView(activity);
        Drawable d = drawable(item.getIcon());
        if (d != null) {
            d.setTint(accent());
            icon.setImageDrawable(d);
        }
        well.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        tile.addView(well, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView label = text(item.getTitle(), 12, ink(), false);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(2), dp(6), dp(2), 0);
        tile.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        tile.setOnClickListener(v -> {
            ItemClickListener listener = item.getItemClickListener();
            if (listener == null) {
                return;
            }
            // Same contract as the drawer: true means the item is done, close the menu.
            if (listener.onContextMenuClick(NO_UI_ADAPTER, v, item, false)) {
                dialog.dismiss();
            }
        });
        pressable(tile);
        return tile;
    }

    private GridLayout.LayoutParams tileParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.bottomMargin = dp(8);
        return lp;
    }

    /** Scales down a touch on press, so the tap feels received. */
    private static void pressable(View view) {
        DecelerateInterpolator easeOut = new DecelerateInterpolator(2f);
        view.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).setInterpolator(easeOut).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(easeOut).start();
            }
            return false;
        });
    }

    private TextView text(String value, int sp, int color, boolean strong) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (strong) {
            Typeface base = tv.getTypeface();
            tv.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, 600, false)
                    : Typeface.create(base, Typeface.BOLD));
        }
        return tv;
    }

    private Drawable drawable(int res) {
        if (res == 0 || res == -1) {
            return null;
        }
        try {
            Drawable d = activity.getDrawable(res);
            return d != null ? d.mutate() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundedTop(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        return g;
    }

    private int accent() {
        return night ? NIGHT_BLUE : BLUE;
    }

    private int ink() {
        return night ? NIGHT_INK : INK;
    }

    private int quiet() {
        return night ? NIGHT_QUIET : QUIET;
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    /** The drawer's list refreshes itself through this; the sheet has nothing to refresh. */
    private static final OnDataChangeUiAdapter NO_UI_ADAPTER = new OnDataChangeUiAdapter() {
        @Override
        public void onDataSetChanged() {
        }

        @Override
        public void onDataSetInvalidated() {
        }

        @Override
        public void onRefreshItem(String id) {
        }
    };
}
