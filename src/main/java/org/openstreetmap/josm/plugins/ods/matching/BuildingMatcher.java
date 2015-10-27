package org.openstreetmap.josm.plugins.ods.matching;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.plugins.ods.entities.EntityStore;
import org.openstreetmap.josm.plugins.ods.entities.actual.Building;
import org.openstreetmap.josm.plugins.ods.entities.managers.DataManager;

public class BuildingMatcher {
    private Map<Long, Match<Building>> buildingMatches = new HashMap<>();
    private EntityStore<Building> odBuildingStore;
    private EntityStore<Building> osmBuildingStore;
    private List<Building> unidentifiedOsmBuildings = new LinkedList<>();
    private List<Building> unmatchedOpenDataBuildings = new LinkedList<>();
    private List<Building> unmatchedOsmBuildings = new LinkedList<>();

    public BuildingMatcher(DataManager dataManager) {
        super();
        odBuildingStore = dataManager.getOpenDataEntityStore(Building.class);
        osmBuildingStore = dataManager.getOsmEntityStore(Building.class);
    }

    public void run() {
        for (Building building : odBuildingStore) {
            processOpenDataBuilding(building);
        }
        for (Building building : osmBuildingStore) {
            processOsmBuilding(building);
        }
    }

    private void processOpenDataBuilding(Building odBuilding) {
        Long id = (Long) odBuilding.getReferenceId();
        Match<Building> match = buildingMatches.get(id);
        if (match != null) {
            match.addOpenDataEntity(odBuilding);
            odBuilding.setMatch(match);
            return;
        }
        List<Building> osmBuildings = osmBuildingStore.getById(id);
        if (osmBuildings.size() > 0) {
            match = new MatchImpl<>(osmBuildings.get(0), odBuilding);
            for (int i=1; i<osmBuildings.size() ; i++) {
                Building osmBuilding = osmBuildings.get(i);
                osmBuilding.setMatch(match);
                match.addOsmEntity(osmBuilding);
            }
            buildingMatches.put(id, match);
        } else {
            unmatchedOpenDataBuildings.add(odBuilding);
        }
    }

    private void processOsmBuilding(Building osmBuilding) {
        Object id = osmBuilding.getReferenceId();
        if (id == null) {
            unidentifiedOsmBuildings.add(osmBuilding);
            return;
        }
        Long l;
        try {
            l = (Long)id;
        }
        catch (@SuppressWarnings("unused") Exception e) {
            unidentifiedOsmBuildings.add(osmBuilding);
            return;
        }
        List<Building> odBuildings = odBuildingStore.getById(l);
        if (odBuildings.size() > 0) {
            Match<Building> match = new MatchImpl<>(osmBuilding, odBuildings.get(0));
            for (int i=1; i<odBuildings.size(); i++) {
                match.addOpenDataEntity(odBuildings.get(i));
            }
            buildingMatches.put(l, match);
        } else {
            unmatchedOsmBuildings.add(osmBuilding);
        }
    }
}