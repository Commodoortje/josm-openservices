package org.openstreetmap.josm.plugins.ods;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.openstreetmap.josm.plugins.ods.entities.BuildException;
import org.openstreetmap.josm.plugins.ods.entities.imported.ImportedEntityBuilder;
import org.openstreetmap.josm.plugins.ods.metadata.MetaData;
import org.openstreetmap.josm.plugins.ods.metadata.MetaDataException;
import org.openstreetmap.josm.plugins.ods.tags.FeatureMapper;

public abstract class AbstractOdsDataSource implements OdsDataSource {
	protected OdsFeatureSource odsFeatureSource;
	private FeatureMapper mapper;
	private Filter filter;
	private IdFactory idFactory;
	private boolean initialized;
	private final Map<Serializable, SimpleFeature> featureStore = new HashMap<>();
	private final List<FeatureListener> featureListeners = new LinkedList<FeatureListener>();
	private String entityType;
	private ImportedEntityBuilder entityBuilder;

	protected AbstractOdsDataSource(OdsFeatureSource odsFeatureSource) {
		super();
		this.odsFeatureSource = odsFeatureSource;
	}

	@Override
	public void addFeatureListener(FeatureListener listener) {
		featureListeners.add(listener);
	}

	@Override
	public final OdsFeatureSource getOdsFeatureSource() {
		return odsFeatureSource;
	}

	public void initialize() throws InitializationException {
		if (!initialized) {
			odsFeatureSource.initialize();
			createFeatureMapper();
			initialized = true;
		}
	}

	@Override
	public void setEntityType(String entityType) {
	    this.entityType = entityType;
	}
	
	@Override
	public void setFilter(Filter filter) throws ConfigurationException {
		this.filter = filter;
	}

	@Override
	public Filter getFilter() {
		return filter;
	}

	@Override
	public void setIdFactory(DefaultIdFactory idFactory) {
		this.idFactory = idFactory;
	}

	@Override
	public IdFactory getIdFactory() {
		if (idFactory == null) {
			idFactory = new DefaultIdFactory(this);
		}
		return idFactory;
	}

	@Override
	public String getFeatureType() {
		return odsFeatureSource.getFeatureName();
	}

	@Override
	public FeatureMapper getFeatureMapper() {
		return mapper;
	}

	@Override
	public ImportedEntityBuilder getEntityBuilder() {
		if (entityBuilder == null) {
			try {
				entityBuilder = new ImportedEntityBuilder();
				if (entityBuilder == null) {
				    // TODO create default entity builder
				}
				entityBuilder.setEntityClass(entityType);
				entityBuilder.setContext(odsFeatureSource.getMetaData());
			} catch (Exception e) {
				entityBuilder = null;
			}
		}
		return entityBuilder;
	}

	private void createFeatureMapper() throws InitializationException {
		// odsFeatureSource.init();
		String typeName = getFeatureType();

		try {
			// TODO move to configuration fase?
			mapper = ODS.getFeatureMapper(typeName);
		} catch (ConfigurationException e) {
			throw new InitializationException(e);
		}
//		mapper.setObjectFactory(new JosmObjectFactory(odsFeatureSource
//				.getSRID()));
		try {
			mapper.setContext(odsFeatureSource.getMetaData());
		} catch (MetaDataException e) {
			throw new InitializationException(e);
		}
	}

	@Override
	public MetaData getMetaData() {
		return getOdsFeatureSource().getMetaData();
	}

	public void addFeatures(List<SimpleFeature> features) {
		ImportedEntityBuilder builder = getEntityBuilder();
		if (builder != null) {
			for (SimpleFeature feature : features) {
				try {
					builder.build(feature);
					System.out.println();
				} catch (BuildException e) {
					throw new RuntimeException(e);
				}
			}
		}
//		for (SimpleFeature feature : features) {
//			Serializable id = idFactory.getId(feature);
//			if (!featureStore.containsKey(id)) {
//				featureStore.put(id, feature);
//				for (FeatureListener listener : featureListeners) {
//					listener.featureAdded(feature);
//				}
//			}
//		}

	}
}