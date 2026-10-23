package net.osmand.plus;
public class OsmandApplication extends android.app.Application {
    public net.osmand.plus.settings.backend.OsmandSettings getSettings() { return null; }
    public net.osmand.plus.helpers.DayNightHelper getDaynightHelper() { return null; }
}
