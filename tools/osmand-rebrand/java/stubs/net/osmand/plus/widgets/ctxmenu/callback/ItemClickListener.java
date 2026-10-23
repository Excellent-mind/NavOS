package net.osmand.plus.widgets.ctxmenu.callback;
public interface ItemClickListener {
    boolean onContextMenuClick(OnDataChangeUiAdapter uiAdapter, android.view.View view,
            net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem item, boolean isChecked);
}
