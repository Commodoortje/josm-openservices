package org.openstreetmap.josm.plugins.ods.osm;

import java.util.function.Consumer;

import org.openstreetmap.josm.plugins.ods.entities.actual.Building;
import org.openstreetmap.josm.plugins.ods.entities.actual.impl.foreign.OpenDataBuildingStore;
import org.openstreetmap.josm.plugins.ods.entities.builtenvironment.CrossingBuildingFixer;
import org.openstreetmap.josm.plugins.ods.jts.GeoUtil;

import com.vividsolutions.jts.geom.Geometry;


/**
 * This tasks verifies if there are adjacent buildings in
 * the down loaded data. If so, the shared nodes will be aligned.
 * 
 * TODO consider running over all buildings, not just the new ones.
 * 
 * @author gertjan
 *
 */
public class BuildingAligner implements Consumer<Building> {
    private final OpenDataBuildingStore buildingStore;
    private final Double tolerance;
    private final CrossingBuildingFixer fixer;
    
    public BuildingAligner(OpenDataBuildingStore buildingStore, GeoUtil geoUtil, Double tolerance) {
        super();
        this.buildingStore = buildingStore;
        this.tolerance = tolerance;
        this.fixer = new CrossingBuildingFixer(geoUtil, tolerance);
    }

    @Override
    public void accept(Building building) {
        for (Building candidate : buildingStore.getGeoIndex().intersection(building.getGeometry())) {
            if (candidate == building) continue;
            if (building.getNeighbours().contains(candidate)) continue;
            building.getNeighbours().add(candidate);
            candidate.getNeighbours().add(building);
            analyzeCrossing(building, candidate);
        }
    }
    
    private void analyzeCrossing(Building building1, Building building2) {
        Geometry geom1 = building1.getGeometry();
        Geometry geom2 = building2.getGeometry();
        if (geom1.isWithinDistance(geom2, tolerance)) {
            fixer.setBuildings(building1, building2);
            fixer.fix();
        }
    }
}
