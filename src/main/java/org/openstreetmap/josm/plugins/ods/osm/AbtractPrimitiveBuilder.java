package org.openstreetmap.josm.plugins.ods.osm;

import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.ods.entities.Entity;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * The AbtractPrimitiveBuilder provides methods to create josm primitives from JTS
 * geometries and add them to a josm dataset. The JTS geometries must be in the
 * josm crs (epsg:4326) The methods take care of removal of duplicate nodes in
 * ways, and merging of nodes that refer to the same point.
 * 
 * @author Gertjan Idema
 * 
 */
public abstract class AbtractPrimitiveBuilder<T extends Entity> {
    // private static final Long JOSM_SRID = 4326L;
    // private JTSCoordinateTransform crsTransform;
    // private final JTSCoordinateTransformFactory crsTransformFactory = new
    // Proj4jCRSTransformFactory();
    private final DataSet dataSet;

    /**
     * Create a JosmSourceManager with the specified source crs
     * 
     * @param sourceCrs
     */
    public AbtractPrimitiveBuilder(DataSet targetDataSet) {
        this.dataSet = targetDataSet;
    }

    public OsmPrimitive[] build(Geometry geometry) {
        switch (geometry.getGeometryType()) {
        case "Polygon":
            return build((Polygon)geometry);
        case "MultiPolygon":
            return build((MultiPolygon)geometry);
        case "Point":
            return build((Point)geometry);
        }
        return new OsmPrimitive[0];
    }

    public OsmPrimitive[] build(Polygon polygon) {
        return new OsmPrimitive[] {buildArea(polygon)};
    }

    public OsmPrimitive[] build(MultiPolygon mpg) {
        return new OsmPrimitive[] {buildArea(mpg)};
    }

    public OsmPrimitive[] build(Point point) {
        OsmPrimitive node = buildNode(point, false);
        return new OsmPrimitive[] {node};
    }

    /**
     * Create a josm Object from a MultiPolygon object The resulting Object depends
     * on whether the input Multipolygon consists of multiple polygons. If so, the result will be a
     * Relation of type Multipolyon. Otherwise the single polygon will be built.
     */
    public OsmPrimitive buildArea(MultiPolygon mpg) {
        OsmPrimitive primitive;
        if (mpg.getNumGeometries() > 1) {
            primitive = buildMultiPolygon(mpg);
            primitive.put("type", "multipolygon");
        } else {
            primitive = buildArea((Polygon) mpg.getGeometryN(0));
        }
        return primitive;
    }

    /**
     * Create a josm Object from a Polygon object The resulting Object depends
     * on whether the input polygon has inner rings. If so, the result will be a
     * Relation of type Multipolyon. Otherwise the result will be a Way
     */
    public OsmPrimitive buildArea(Polygon polygon) {
        OsmPrimitive primitive;
        if (polygon.getNumInteriorRing() > 0) {
            primitive = buildMultiPolygon(polygon);
            primitive.put("type", "multipolygon");
        }
        else {
            primitive = buildWay(polygon);
        }
        return primitive;
    }

    /**
     * Create a josm MultiPolygon relation from a Polygon object.
     * 
     * @param polygon
     * @return the relation
     */
    public Relation buildMultiPolygon(Polygon polygon) {
        MultiPolygon multiPolygon = polygon.getFactory().createMultiPolygon(
                new Polygon[] { polygon });
        return buildMultiPolygon(multiPolygon);
    }

    /**
     * Create a josm MultiPolygon relation from a MultiPolygon object.
     * 
     * @param mpg
     * @return the relation
     */
    public Relation buildMultiPolygon(MultiPolygon mpg) {
        Relation relation = new Relation();
        for (int i = 0; i < mpg.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) mpg.getGeometryN(i);
            Way way = buildWay(polygon.getExteriorRing());
            relation.addMember(new RelationMember("outer", way));
            for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                way = buildWay(polygon.getInteriorRingN(j));
                relation.addMember(new RelationMember("inner", way));
            }
        }
        dataSet.addPrimitive(relation);
        return relation;
    }

    /**
     * Create a josm Way from the exterior ring of a Polygon object
     * 
     * @param polygon
     * @return the way
     */
    public Way buildWay(Polygon polygon) {
        return buildWay(polygon.getExteriorRing());
    }

    /**
     * Create a josm way from a LineString object
     * 
     * @param line
     * @return
     */
    public Way buildWay(LineString line) {
        return buildWay(line.getCoordinateSequence());
    }

    private Way buildWay(CoordinateSequence points) {
        Way way = new Way();
        Node previousNode = null;
        for (int i = 0; i < points.size(); i++) {
            Node node = buildNode(points.getCoordinate(i), true);
            // Remove duplicate nodes in ways
            if (node != previousNode) {
                way.addNode(node);
            }
            previousNode = node;
        }
        dataSet.addPrimitive(way);
        return way;
    }

    /**
     * Create a josm Node from a Coordinate object. Optionally merge with
     * existing nodes.
     * 
     * @param coordinate
     * @param merge
     *            if true, merge this node with an existing node
     * @return the node
     */
    public Node buildNode(Coordinate coordinate, boolean merge) {
        LatLon latlon = new LatLon(coordinate.y, coordinate.x);
        Node node = new Node(latlon);
        if (merge) {
            BBox bbox = new BBox(node);
            List<Node> existingNodes = dataSet.searchNodes(bbox);
            if (existingNodes.size() > 0) {
                node = existingNodes.get(0);
                return node;
            }
        }
        dataSet.addPrimitive(node);
        return node;
    }

    /**
     * Create a josm Node from a Point object. Optionally merge with existing
     * nodes.
     * 
     * @param point
     * @param merge
     * @return
     */
    public Node buildNode(Point point, boolean merge) {
        if (point == null)
            return null;
        return buildNode(point.getCoordinate(), merge);
    }

    public OsmPrimitive[] createPrimitives(T entity) {
        OsmPrimitive[] primitives = null;
        if (entity.isIncomplete() || entity.getGeometry() == null ) {
            return null;
        }
        primitives = build(entity.getGeometry());
        for (OsmPrimitive primitive : primitives) {
            buildTags(entity, primitive);
        }
        return primitives;
    }

    public abstract void buildTags(T entity, OsmPrimitive primitive);
}
