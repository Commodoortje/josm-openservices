package org.openstreetmap.josm.plugins.ods.entities;

import org.openstreetmap.josm.plugins.ods.metadata.MetaData;

public interface EntityFactory<T> {
    public Entity buildEntity(T data, MetaData metaData) throws BuildException;
}
