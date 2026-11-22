package com.agroshift.core.ui;

import android.content.Context;
import androidx.core.content.ContextCompat;
import java.io.File;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;

/** Shared OpenStreetMap setup. Map tiles are cached in app-private storage. */
public final class ZameenMap {
    private static final long TILE_CACHE_BYTES = 40L * 1024 * 1024;
    private static final OnlineTileSourceBase SATELLITE = new XYTileSource("EsriWorldImagery", 1, 18, 256,
            "", new String[] {"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"},
            "Tiles © Esri, Maxar, Earthstar Geographics") {
        @Override
        public String getTileURLString(long index) {
            return getBaseUrl() + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/"
                    + MapTileIndex.getX(index);
        }
    };

    private ZameenMap() {
    }

    public static void configure(Context context) {
        Context app = context.getApplicationContext();
        Configuration.getInstance().setUserAgentValue(app.getPackageName());
        File base = new File(app.getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(TILE_CACHE_BYTES);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(TILE_CACHE_BYTES * 3 / 4);
    }

    public static void prepare(MapView map, boolean interactive) {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(interactive);
        map.setClickable(interactive);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(18.0);
        map.getOverlays().add(new CopyrightOverlay(map.getContext()));
    }

    public static void useSatellite(MapView map, boolean satellite) {
        map.setTileSource(satellite ? SATELLITE : TileSourceFactory.MAPNIK);
        map.invalidate();
    }

    public static Marker pin(MapView map, GeoPoint point) {
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(map.getContext(), R.drawable.ic_field_pin));
        marker.setInfoWindow(null);
        marker.setOnMarkerClickListener((clicked, view) -> true);
        return marker;
    }
}
