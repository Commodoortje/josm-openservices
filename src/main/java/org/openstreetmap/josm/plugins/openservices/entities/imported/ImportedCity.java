package org.openstreetmap.josm.plugins.openservices.entities.imported;

import java.util.HashMap;
import java.util.Map;

import org.openstreetmap.josm.plugins.openservices.PrimitiveBuilder;
import org.openstreetmap.josm.plugins.openservices.entities.builtenvironment.Place;

import com.vividsolutions.jts.geom.MultiPolygon;

public abstract class ImportedCity extends ImportedEntity implements Place {
    protected MultiPolygon geometry;
    protected String name;
    
    @Override
    public String getNamespace() {
        return Place.NAMESPACE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MultiPolygon getGeometry() {
        return geometry;
    }

    @Override
    public void createPrimitives(PrimitiveBuilder builder) {
        if (getPrimitives() == null) {
            setPrimitives(builder.build(geometry, getKeys()));
        }
    }

    @Override
    protected Map<String, String> getKeys() {
        Map<String, String> keys = new HashMap<String, String>();
        keys.put("name", name);
        return keys;
    }    
}