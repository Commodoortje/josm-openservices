package org.openstreetmap.josm.plugins.ods;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import nl.gertjanidema.conversion.valuemapper.ValueMapper;
import nl.gertjanidema.conversion.valuemapper.ValueMapperException;
import nl.gertjanidema.conversion.valuemapper.ValueMapperFactory;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.openstreetmap.josm.plugins.ods.entities.EntityFactory;
import org.openstreetmap.josm.plugins.ods.geotools.GTDataLayer;
import org.openstreetmap.josm.plugins.ods.metadata.HttpMetaDataLoader;
import org.openstreetmap.josm.plugins.ods.metadata.MetaDataAttribute;
import org.openstreetmap.josm.plugins.ods.metadata.MetaDataLoader;
import org.openstreetmap.josm.plugins.ods.osm.OdsOsmDataLayer;
import org.openstreetmap.josm.plugins.ods.tags.DefaultFeatureMapper;
import org.openstreetmap.josm.plugins.ods.tags.DefaultGeometryMapper;
import org.openstreetmap.josm.tools.I18n;

public class ConfigurationReader {
    private final ClassLoader classLoader;
    private final OdsModule module;

    public ConfigurationReader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.module = null;
    }

    public ConfigurationReader(ClassLoader classLoader, OdsModule module) {
        this.classLoader = classLoader;
        this.module = module;
    }

    public void read(URL configFile) throws ConfigurationException {
        try {
            XMLConfiguration conf = new XMLConfiguration();
            conf.setDelimiterParsingDisabled(true);
            conf.setAttributeSplittingDisabled(true);
            conf.load(configFile);
            configureImports(conf);
            configureHosts(conf);
            if (module != null) {
                configureModule(conf);
            }
        } catch (NoSuchElementException e) {
            throw new ConfigurationException(e.getMessage(), e.getCause());
        }
    }

    private void configureImports(HierarchicalConfiguration conf)
            throws ConfigurationException {
        List<HierarchicalConfiguration> confs = conf.configurationsAt("import");
        for (HierarchicalConfiguration c : confs) {
            configureImport(c);
        }
    }

    private void configureImport(HierarchicalConfiguration conf)
            throws ConfigurationException {
        conf.setThrowExceptionOnMissing(true);
        String name = conf.getString("[@name]");
        String type = conf.getString("[@type]");
        String className = conf.getString("[@class]");
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(className);
            ODS.registerImport(type, name, clazz);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(I18n.tr(
                    "A class named {0} could not be found", className));
        }
    }

    private void configureHosts(HierarchicalConfiguration conf)
            throws ConfigurationException {
        List<HierarchicalConfiguration> confs = conf.configurationsAt("host");
        for (HierarchicalConfiguration c : confs) {
            configureHost(c);
        }
    }

    private void configureHost(HierarchicalConfiguration conf)
            throws ConfigurationException {
        Integer maxFeatures = null;
        String s = conf.getString("[@maxFeatures]");
        if (s!=null) {
            try {
                maxFeatures = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        conf.setThrowExceptionOnMissing(true);
        String name = conf.getString("[@name]");
        String type = conf.getString("[@type]");
        String url = conf.getString("[@url]", "");
        Host host = ODS.registerHost(type, name, url, maxFeatures);
        for (MetaDataLoader metaDataLoader : parseMetaDataLoaders(conf)) {
            host.addMetaDataLoader(metaDataLoader);
        }
    }

    private void configureDataSource(GTDataLayer layer, HierarchicalConfiguration conf) throws ConfigurationException {
        OdsFeatureSource odsFeatureSource = configureOdsFeatureSource(conf);
        OdsDataSource dataSource = odsFeatureSource.newDataSource();
        String entityType = conf.getString("[@entitytype]", null);
        boolean required = conf.getBoolean("[@required]", true);
        dataSource.setEntityType(entityType);
        dataSource.setRequired(required);
        String filter = conf.getString("filter", null);
        if (filter != null) {
            configureFilter(dataSource, filter);
        }
        String idAttribute = conf.getString("id[@attribute]", null);
        configureIdFactory(dataSource, idAttribute);
        layer.addDataSource(dataSource);
    }

    private void configureFilter(OdsDataSource dataSource, String filter)
            throws ConfigurationException {
        try {
            dataSource.setFilter(CQL.toFilter(filter));
        } catch (CQLException e) {
            throw new ConfigurationException(tr("Error in filter"), e);
        }
    }

    private void configureIdFactory(OdsDataSource dataSource, String idAttribute)
            throws ConfigurationException {
        DefaultIdFactory idFactory = new DefaultIdFactory(dataSource);
        idFactory.setKeyAttribute(idAttribute);
        dataSource.setIdFactory(idFactory);
    }

    private OdsFeatureSource configureOdsFeatureSource(
            HierarchicalConfiguration conf) throws ConfigurationException {
        conf.setThrowExceptionOnMissing(true);
        String hostName = conf.getString("[@host]");
        String feature = conf.getString("[@feature]");
        try {
            Host host = ODS.getHost(hostName);
            if (host == null) {
                throw new ConfigurationException(I18n.tr("Unknown host: {0}",
                        hostName));
            }
            OdsFeatureSource featureSource = host.getOdsFeatureSource(feature);
            return featureSource;
        } catch (ServiceException e) {
            throw new ConfigurationException(e);
        }
    }

    private void configureModule(HierarchicalConfiguration conf)
            throws ConfigurationException {
        OdsWorkingSet workingSet = module.getWorkingSet();
        configureWorkingSet(workingSet, conf);
    }
    
    private void configureWorkingSet(OdsWorkingSet workingSet, HierarchicalConfiguration conf) throws ConfigurationException {
        // TODO configure OSM layer
        // String osmQuery = c.getString("osm_query");
        // workingSet.setOsmQuery(osmQuery);
        OdsOsmDataLayer internalLayer = new OdsOsmDataLayer(workingSet.getName());
        workingSet.setInternalDataLayer(internalLayer);

        GTDataLayer externalLayer = new GTDataLayer(workingSet.getName());
        HierarchicalConfiguration c = conf.configurationAt("layer");
        configureExternalLayer(externalLayer, c);
        workingSet.setExternalDataLayer(externalLayer);
//      String name = conf.getString("[@name]");
//      String description = conf.getString("[@description]", "");        
    }
    
    private void configureExternalLayer(GTDataLayer layer, HierarchicalConfiguration conf) throws ConfigurationException {
        for (HierarchicalConfiguration c : conf.configurationsAt("datasource")) {
            configureDataSource(layer, c);
        }
    }

//    private void configureEntityFactory(OdsWorkingSet ows,
//            HierarchicalConfiguration conf) throws ConfigurationException {
//        EntityFactory entityFactory;
//        HierarchicalConfiguration factoryConf =conf.configurationAt("factory");
//        if (factoryConf != null) {
//            entityFactory = createEntityFactory(factoryConf);
//        }
//        else {
//            entityFactory = new SimpleExternalEntityFactory();
//        }
//        ows.setEntityFactory(entityFactory);
//    }

//    private EntityFactory createEntityFactory(
//            HierarchicalConfiguration conf) throws ConfigurationException {
//        String factoryName = conf.getString("[@name]");
//        if (factoryName == null) {
//            throw new ConfigurationException("No name attribute supplied for the factory element");
//        }
//        Class<?> factoryClass = ODS.getClass("entityFactory", factoryName);
//        try {
//            return (EntityFactory) factoryClass.newInstance();
//        } catch (ClassCastException | InstantiationException | IllegalAccessException e) {
//            throw new ConfigurationException("Unable to create a class of type " + factoryClass.getName());
//        }
//    }

//    private void configureActions(OdsWorkingSet layer,
//            HierarchicalConfiguration conf) throws ConfigurationException {
//        for (HierarchicalConfiguration c : conf.configurationsAt("action")) {
//            configureAction(layer, c);
//        }
//    }

//    private void configureAction(OdsWorkingSet layer,
//            HierarchicalConfiguration conf) throws ConfigurationException {
//        String name = conf.getString("[@name]", null);
//        String type = conf.getString("[@type]");
//        String iconName = conf.getString("[@icon]");
//        try {
//            OldOdsAction action = (OldOdsAction) ODS.createObject(
//                    "action", type);
//            if (name != null) {
//                action.setName(name);
//            }
//            if (iconName != null) {
//                ImageIcon icon = ImageProvider.getIfAvailable(iconName);
//                if (icon == null) {
//                    throw new ConfigurationException(tr(
//                            "No icon found named {0}", iconName));
//                }
//                action.setIcon(icon);
//            }
//            layer.addAction(action);
//        } catch (Exception e) {
//            throw new ConfigurationException(e);
//        }
//    }

    // private synchronized void configureMenu(Action action, String menu) {
    // String[] menuParts = menu.split("\\.");
    // String baseMenuName = menuParts[0];
    // JMenu parent = getBaseMenu(baseMenuName, 4);
    // for (int i=1; i<menuParts.length; i++) {
    // parent = getChildMenu(parent, menuParts[i]);
    // }
    // parent.add(action);
    // }

    // private void configureEntityBuilders(HierarchicalConfiguration conf)
    // throws ConfigurationException {
    // for (HierarchicalConfiguration c : conf.configurationsAt("entity")) {
    // configureEntityBuilder(c);
    // }
    // }

    // private void configureEntityBuilder(HierarchicalConfiguration conf)
    // throws ConfigurationException {
    // String className = conf.getString("[@builder]");
    // if (className != null) {
    // try {
    // ExternalEntityBuilder<?> builder = (ExternalEntityBuilder<?>)
    // classLoader.loadClass(className).newInstance();
    // ODS.registerEntityBuilder(builder);
    // } catch (Exception e) {
    // throw new
    // ConfigurationException(tr("Could not configure Entity builder"), e);
    // }
    // }
    // }

//    private void configureFeatureMappers(HierarchicalConfiguration conf)
//            throws ConfigurationException {
//        for (HierarchicalConfiguration c : conf.configurationsAt("map")) {
//            configureFeatureMapper(c);
//        }
//    }
//
//    private void configureFeatureMapper(HierarchicalConfiguration conf)
//            throws ConfigurationException {
//        DefaultFeatureMapper mapper = new DefaultFeatureMapper();
//        mapper.setFeatureName(conf.getString("[@feature]"));
//        for (HierarchicalConfiguration c : conf.configurationsAt("tag")) {
//            String value = c.getString("[@value]");
//            String property = c.getString("[@property]");
//            String format = c.getString("[@format]");
//            String expression = c.getString("[@expression]");
//            String meta = c.getString("[@meta]");
//            c.setThrowExceptionOnMissing(true);
//            String key = c.getString("[@key]");
//            if (value != null) {
//                mapper.addTagBuilder(new FixedTagBuilder(key, value));
//            } else if (meta != null) {
//                mapper.addTagBuilder(new MetaTagBuilder(key, meta, format));
//            } else if (property != null) {
//                mapper.addTagBuilder(new PropertyTagBuilder(key, property,
//                        format));
//            } else if (expression != null) {
//                mapper.addTagBuilder(new ExpressionTagBuilder(key, expression));
//            }
//        }
//        configureGeometryMapper(mapper, conf.configurationAt("geometry"));
//        ODS.registerFeatureMapper(mapper);
//    }

    private void configureGeometryMapper(DefaultFeatureMapper mapper,
            SubnodeConfiguration conf) throws ConfigurationException {
        String className = conf.getString("[@mapper]");
        String mapTo = conf.getString("[@map-to]");
        Boolean merge = Boolean.valueOf(conf.getString("[@merge]", "false"));
        if (className == null) {
            DefaultGeometryMapper geometryMapper = new DefaultGeometryMapper();
            geometryMapper.setTargetPrimitive(mapTo);
            mapper.setGeometryMapper(geometryMapper);
        } else {
            try {
                DefaultGeometryMapper geometryMapper = (DefaultGeometryMapper) classLoader
                        .loadClass(className).newInstance();
                geometryMapper.setTargetPrimitive(mapTo);
                mapper.setGeometryMapper(geometryMapper);
            } catch (Exception e) {
                throw new ConfigurationException(tr(
                        "Could not configure Geometry mapper:\n{0}",
                        e.getMessage()), e);
            }
        }
    }

    private List<MetaDataLoader> parseMetaDataLoaders(
            HierarchicalConfiguration conf) throws ConfigurationException {
        List<MetaDataLoader> loaders = new LinkedList<MetaDataLoader>();
        for (HierarchicalConfiguration c : conf.configurationsAt("meta")) {
            loaders.add(parseMetaDataLoader(c));
        }
        return loaders;
    }

    private MetaDataLoader parseMetaDataLoader(HierarchicalConfiguration conf)
            throws ConfigurationException {
        String url = conf.getString("[@url]", null);
        // TODO implement POST
        // String method = c.getString("method", "GET");
        if (url == null) {
            throw new ConfigurationException(
                    tr("Required parameter 'url' is missing"));
        }
        HttpMetaDataLoader metaDataLoader = new HttpMetaDataLoader(url);
        configureMetaDataProperties(conf, metaDataLoader);
        return metaDataLoader;
    }

    private void configureMetaDataProperties(HierarchicalConfiguration conf,
            HttpMetaDataLoader metaDataLoader) throws ConfigurationException {
        for (HierarchicalConfiguration c : conf.configurationsAt("property")) {
            configureMetaDataProperty(c, metaDataLoader);
        }
    }

    private void configureMetaDataProperty(HierarchicalConfiguration conf,
            HttpMetaDataLoader metaDataLoader) throws ConfigurationException {
        String name = conf.getString("[@name]");
        String type = conf.getString("[@type]", "string");
        String query = conf.getString("[@query]", null);
        String pattern = conf.getString("[@pattern]", null);
        Properties properties = new Properties();
        if (pattern != null) {
            properties.put("pattern", pattern);
        }
        ValueMapperFactory vmFactory = new ValueMapperFactory();
        ValueMapper<?> valueMapper;
        try {
            valueMapper = vmFactory.createValueMapper(type, properties);
            MetaDataAttribute attr = new MetaDataAttribute(name, query,
                    valueMapper);
            metaDataLoader.addAttribute(attr);
        } catch (ValueMapperException e) {
            throw new ConfigurationException(e);
        }
    }
    
//    @SuppressWarnings("unchecked")
//    private Class<? extends Entity> getEntityClass(String entityType) throws ConfigurationException {
//        if (entityType == null) {
//            return SimpleExternalEntity.class;
//        }
//        Class<? extends Entity> entityClass = null;
//        try {
//            entityClass = (Class<? extends Entity>) ODS.getClass("entity", entityType);
//            if (entityClass == null) {
//                throw new ConfigurationException(I18n.tr("Unknown entity: {0}",
//                    entityType));
//            }
//        }
//        catch (ClassCastException e) {
//            throw new ConfigurationException(I18n.tr("Invalid entity type: {0}",
//                    entityClass));
//            
//        }
//        return entityClass;
//    }
}
