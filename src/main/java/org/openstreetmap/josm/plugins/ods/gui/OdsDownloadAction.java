package org.openstreetmap.josm.plugins.ods.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.ods.OdsModule;
import org.openstreetmap.josm.plugins.ods.io.DownloadRequest;
import org.openstreetmap.josm.plugins.ods.io.MainDownloader;
import org.openstreetmap.josm.plugins.ods.jts.Boundary;
import org.openstreetmap.josm.tools.ImageProvider;
import org.xml.sax.SAXException;

public class OdsDownloadAction extends OdsAction {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private MainDownloader downloader;
    private Date startDate;
    private boolean cancelled = false;
    private Boundary boundary;
    private boolean downloadOsm;
    private boolean downloadOds;
    private SlippyMapDownloadDialog slippyDialog;
    private FixedBoundsDownloadDialog fixedDialog;
    
    public OdsDownloadAction(OdsModule module) {
        super(module, "Download", ImageProvider.get("download"));
        slippyDialog = new SlippyMapDownloadDialog(module);
        fixedDialog = new FixedBoundsDownloadDialog(module);
        this.downloader = module.getDownloader();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        run();
    }
    
    public void run() {
        cancelled = false;
        boundary = getBoundary();
        if (!cancelled) {
            DownloadTask task = new DownloadTask();
            Main.worker.submit(task);

        }
    }

    private Boundary getBoundary() {
        Boundary boundary = getPolygonBoundary();
        boolean selectArea = (boundary == null);
        AbstractDownloadDialog dialog;
        if (selectArea) {
            dialog = slippyDialog;
        }
        else {
            dialog = fixedDialog;
        }
        dialog.restoreSettings();
        dialog.setVisible(true);
        if (dialog.isCanceled()) {
            cancelled = true;
            return null;
        }
        dialog.rememberSettings();
        downloadOsm = dialog.cbDownloadOSM.isSelected();
        downloadOds = dialog.cbDownloadODS.isSelected();
        if (selectArea) {
            boundary = new Boundary(dialog.getSelectedDownloadArea());
        }
        return boundary;
    }
    
    private Boundary getPolygonBoundary() {
        if (Main.map == null) {
            return null;
        }
        Layer activeLayer = Main.map.mapView.getActiveLayer();
        if (!(activeLayer instanceof OsmDataLayer)) {
            return null;
        }
        OsmDataLayer layer = (OsmDataLayer) activeLayer;
        if (layer.data.getAllSelected().size() != 1) {
            return null;
        }
        OsmPrimitive primitive = layer.data.getAllSelected().iterator().next();
        if (primitive.getDisplayType() != OsmPrimitiveType.CLOSEDWAY) {
            return null;
        }
        return new Boundary((Way)primitive);
    }
    
    private class DownloadTask extends PleaseWaitRunnable {
        
        public DownloadTask() {
            super(tr("Downloading data"));
        }

        @Override
        protected void cancel() {
            downloader.cancel();
        }

        @Override
        protected void realRun() throws SAXException, IOException,
                OsmTransferException {
            try {
                DownloadRequest request = new DownloadRequest(startDate, boundary,
                    downloadOsm, downloadOds);
//                Context ctx = new Context();
//                ctx.put("entitySource", entitySource);
                downloader.run(getProgressMonitor(), request);
            } catch (ExecutionException|InterruptedException e) {
                throw new OsmTransferException(e);
            }
        }

        @Override
        protected void finish() {
            if (downloadOsm) {
                Main.map.mapView.setActiveLayer(getModule().getInternalDataLayer().getOsmDataLayer());
            }
            else {
                Main.map.mapView.setActiveLayer(getModule().getExternalDataLayer().getOsmDataLayer());
            }
        }
    }
}
