package org.openstreetmap.josm.plugins.ods.osm;

import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.ods.OdsModule;
import org.openstreetmap.josm.plugins.ods.entities.EntityStore;
import org.openstreetmap.josm.plugins.ods.entities.actual.Building;

public class BuildingAligner {
    private NodeDWithin dWithin;
    private boolean undoable;
    private EntityStore<Building> buildingStore;
    
    public BuildingAligner(OdsModule module, EntityStore<Building> buildingStore) {
        this.buildingStore = buildingStore;
//        this.tolerance = module.getTolerance();
//        this.tolerance = 0.1; // Tolerance in meters
        this.dWithin = new NodeDWithinLatLon(0.1);
    }

    public void align(Building building) {
        for (Building candidate : buildingStore.getGeoIndex().intersection(building.getGeometry())) {
            if (candidate == building) {
                continue;
            }
            if (building.getNeighbours().contains(candidate)) continue;
            building.getNeighbours().add(candidate);
            candidate.getNeighbours().add(building);
            align(building, candidate);
        }
    }
    
    public void align(Building b1, Building b2) {
        align(b1.getPrimitive(), b2.getPrimitive());
    }
    
    public void align(OsmPrimitive osm1, OsmPrimitive osm2) {
        if (osm1 == null || osm2 == null) return;
        Way outerWay1 = getOuterWay(osm1);
        Way outerWay2 = getOuterWay(osm2);
        if (outerWay1 != null && outerWay2 != null) {
            WayAligner wayAligner = new WayAligner(outerWay1, outerWay2, dWithin, undoable);
            wayAligner.run();            
        }
    }
    
    private Way getOuterWay(OsmPrimitive osm) {
        if (osm.getType() == OsmPrimitiveType.WAY) {
            Way way = (Way)osm;
            if (way.isClosed()) return way;
            return null;
        }
        if (osm.getType() == OsmPrimitiveType.RELATION) {
            List<Way> outerWays = new LinkedList<>();
            for (RelationMember member : ((Relation)osm).getMembers()) {
                if ("outer".equals(member.getRole()) && member.getDisplayType() == OsmPrimitiveType.CLOSEDWAY) {
                    outerWays.add(member.getWay());
                }
            }
            if (outerWays.size() == 1) {
                Way outerWay = outerWays.get(0);
                if (outerWay.isClosed()) {
                    return outerWay;
                }
            }
        }
        return null;
    }
}
