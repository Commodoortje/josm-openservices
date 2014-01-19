package org.openstreetmap.josm.plugins.ods;

//import static nl.gertjanidema.josm.bag.BAGDataType.ADRES;
//import static nl.gertjanidema.josm.bag.BAGDataType.LIGPLAATS;
//import static nl.gertjanidema.josm.bag.BAGDataType.PAND;
//import static nl.gertjanidema.josm.bag.BAGDataType.STANDPLAATS;
//import static nl.gertjanidema.josm.bag.BAGDataType.WEGVAK;
import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.marktr;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import javax.swing.JMenu;

import org.apache.commons.configuration.ConfigurationException;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class OpenDataServicesPlugin extends Plugin {
    private static JMenu menu;

    public OpenDataServicesPlugin(PluginInformation info) {
        super(info);
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL configFile = classLoader.getResource("config.xml");
            try {
                ConfigurationReader configurationReader = new ConfigurationReader(
                        classLoader);
                configurationReader.read(configFile);
            } catch (ConfigurationException e) {
                Main.info("An error occured trying to registrate the odsFeatureSource types.");
                Main.info(e.getMessage());
            }
            getMenu();
//            configureSources();
            addDownloadDialogListener();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void configureSources() {
        File pluginDir = new File(getPluginDir());
        createPluginClassLoader(pluginDir);
        if (pluginDir.isDirectory()) {
            configureJarSources(pluginDir);
        }
    }

    private void createPluginClassLoader(File pluginDir) {
        FilenameFilter jarFileFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        };
        String[] jars = pluginDir.list(jarFileFilter);
        URL[] urls = new URL[jars.length];
        try {
            for (int i = 0; i < jars.length; i++) {
                File file = new File(pluginDir, jars[i]);
                urls[i] = file.toURI().toURL();
            }
            URLClassLoader classLoader = new URLClassLoader(urls, getClass()
                    .getClassLoader());
            ODS.setClassLoader(classLoader);
        } catch (MalformedURLException e) {
            // I don't expect this to happen. Throw a runtime exception just in
            // case
            throw new RuntimeException(e);
        }

    }

    private void configureJarSources(File pluginDir) {
        FilenameFilter jarFileFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        };
        for (String jarFile : pluginDir.list(jarFileFilter)) {
            configureJarSource(new File(pluginDir, jarFile));
        }
    }

    public void configureJarSource(File jarFile) {
        try {
            URL url = jarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[] { url },
                    null);
            URL configFile = classLoader.getResource("config.xml");
            try {
                classLoader.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (configFile == null) {
                Main.warn("Warning: {0} should contain a config.xml file",
                        jarFile);
                return;
            }
            // Now we need the classLoader to include the ODS plugin
            classLoader = new URLClassLoader(new URL[] { url }, getClass()
                    .getClassLoader());
            ConfigurationReader configurationReader = new ConfigurationReader(
                    classLoader);
            configurationReader.read(configFile);
            try {
                classLoader.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("An unexpected exception occurred", e);
        } catch (ConfigurationException e) {
            Main.warn("A problem occurred when reading {0}", jarFile);
            Main.warn(e.getMessage());
            if (e.getCause() instanceof NullPointerException) {
                e.getCause().printStackTrace();
            }
        }
    }

    public static JMenu getMenu() {
        if (menu == null) {
            menu = Main.main.menu.addMenu(marktr("ODS"), KeyEvent.VK_UNDEFINED,
                    4, ht("/Plugin/ODS"));
        }
        return menu;
    }

    /*
     * When Josm's default download is called, the results shouldn't end up in
     * one of the OpenService layers. To achieve this, we intercept the
     * OdsDownloadDialog and make sure an OsmData layer is active before
     * continuing;
     */
    private void addDownloadDialogListener() {
        DownloadDialog.getInstance().addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentShown(ComponentEvent e) {
                        if (!Main.isDisplayingMapView())
                            return;
                        Layer activeLayer = Main.main.getActiveLayer();
                        if (activeLayer.getName().startsWith("ODS")
                                || activeLayer.getName().startsWith("OSM")) {
                            for (Layer layer : Main.map.mapView
                                    .getAllLayersAsList()) {
                                if (layer instanceof OsmDataLayer
                                        && !(layer.getName().startsWith("ODS"))
                                        && !(layer.getName().startsWith("OSM"))) {
                                    Main.map.mapView.setActiveLayer(layer);
                                    return;
                                }
                            }
                        } else if (activeLayer instanceof OsmDataLayer) {
                            return;
                        }
                        Layer newLayer = new OsmDataLayer(new DataSet(),
                                OsmDataLayer.createNewName(), null);
                        Main.map.mapView.addLayer(newLayer);
                        Main.map.mapView.setActiveLayer(newLayer);
                    }
                });
    }
}
