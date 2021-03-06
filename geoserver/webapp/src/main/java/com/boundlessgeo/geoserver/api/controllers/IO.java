/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.util.DateUtil;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.wms.WMSInfo;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.feature.FeatureTypes;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.geotools.geometry.jts.Geometries;
import org.geotools.referencing.CRS;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.util.logging.Logging;
import org.ocpsoft.pretty.time.PrettyTime;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AssociationDescriptor;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.PropertyName;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ProjectedCRS;

import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.google.common.base.Throwables;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Helper for encoding/decoding objects to/from JSON.
 */
public class IO {

    static Logger LOG = Logging.getLogger(IO.class);

    /**
     * Encodes a projection within the specified object.
     *
     * @return The object passed in.
     */
    public static JSONObj proj(JSONObj obj, CoordinateReferenceSystem crs, String srs) {
        if (srs == null && crs != null) {
            try {
                srs = CRS.lookupIdentifier(crs, false);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine srs from crs: " + crs, e);
            }
        }

        if (srs != null) {
            obj.put("srs", srs);
        }

        if (crs == null && srs != null) {
            try {
                crs = CRS.decode(srs);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine crs from srs: " + srs, e);
            }
        }

        if (crs != null) {
            // type
            obj.put("type",
                    crs instanceof ProjectedCRS ? "projected" : crs instanceof GeographicCRS ? "geographic" : "other");

            // units
            String units = null;
            try {
                // try to determine from actual crs
                String unit = crs.getCoordinateSystem().getAxis(0).getUnit().toString();
                if ("ft".equals(unit) || "feets".equals(unit))
                    units = "ft";
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine units from crs", e);
            }
            if (units == null) {
                // fallback: meters for projected, otherwise degrees
                units = crs instanceof ProjectedCRS ? "m" : "degrees";
            }
            obj.put("unit", units);
        }

        return obj;
    }

    /**
     * Encodes a bounding box within the specified object.
     *
     * @return The object passed in.
     */
    public static JSONObj bounds(JSONObj obj, Envelope bbox) {
        Coordinate center = bbox.centre();
        obj.put("west", bbox.getMinX())
                .put("south", bbox.getMinY())
                .put("east", bbox.getMaxX())
                .put("north", bbox.getMaxY())
                .putArray("center").add(center.x).add(center.y);
        return obj;
    }

    /**
     * Decodes a bounding box within the specified object.
     *
     * @return The object passed in.
     */
    public static Envelope bounds(JSONObj obj) {
        return new Envelope(obj.doub("west"), obj.doub("east"), obj.doub("south"), obj.doub("north"));
    }
    
    public static JSONArr arr( Collection<String> strings ){
        JSONArr l = new JSONArr();
        for( String s : strings ){
            l.add(s);
        }
        return l;
    }

    /**
     * Encodes a workspace within the specified object.
     *
     * @param obj The object to encode within.
     * @param workspace The workspace to encode.
     * @param namespace The namespace corresponding to the workspace.
     * @param isDefault Flag indicating whether the workspace is the default.
     *
     * @return The object passed in.
     */
    public static JSONObj workspace(JSONObj obj, WorkspaceInfo workspace, NamespaceInfo namespace, boolean isDefault) {
        obj.put("name", workspace.getName());
        if (namespace != null) {
            obj.put("uri", namespace.getURI());
        }
        obj.put("default", isDefault);
        return obj;
    }

    /**
     * Encodes a layer within the specified object.
     *
     * @return The object passed in.
     */
    public static JSONObj layer(JSONObj obj, LayerInfo layer) {
        String wsName = layer.getResource().getNamespace().getPrefix();

        ResourceInfo r = layer.getResource();
        obj.put("name", layer.getName())
                .put("workspace", wsName)
                .put("title", layer.getTitle() != null ? layer.getTitle() : r.getTitle())
                .put("description", layer.getAbstract() != null ? layer.getAbstract() : r.getAbstract())
                .put("type", type(r));

        if (r instanceof FeatureTypeInfo) {
            FeatureTypeInfo ft = (FeatureTypeInfo) r;
            FeatureType schema;
            try {
                schema = ft.getFeatureType();
                obj.put("geometry", geometry(schema));
                IO.schema(obj.putObject("schema"), schema, true );
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error looking up schema "+ft.getNativeName(), e);
            }
        }
        else if( r instanceof CoverageInfo) {
            obj.put("geometry", "raster");
            IO.schemaGrid(obj.putObject("schema"), ((CoverageInfo)r), true );
        }
        else if( r instanceof WMSInfo) {
            obj.put("geometry", "layer");
        }

        proj(obj.putObject("proj"), r.getCRS(), r.getSRS());
        bbox( obj.putObject("bbox"), r );

        return metadata(obj, layer);
    }

    static String type(ResourceInfo r)  {
        if (r instanceof CoverageInfo) {
            return "raster";
        }
        else if (r instanceof FeatureTypeInfo){
            return "vector";
        }
        else if (r instanceof WMSLayerInfo){
            return "wms";
        }
        else {
            return "resource";
        }
    }

    static String geometry(FeatureType ft) {
        GeometryDescriptor gd = ft.getGeometryDescriptor();
        if (gd == null) {
            return "Vector";
        }
        @SuppressWarnings("unchecked")
        Geometries geomType = Geometries.getForBinding((Class<? extends Geometry>) gd.getType().getBinding());
        return geomType.getName();
    }
    
    public static JSONObj bbox( JSONObj bbox, ResourceInfo r ){
        if (r.getNativeBoundingBox() != null) {
            bounds(bbox.putObject("native"), r.getNativeBoundingBox());
        }
        else {
            // check if the crs is geographic, if so use lat lon
            if (r.getCRS() instanceof GeographicCRS) {
                bounds(bbox.putObject("native"), r.getLatLonBoundingBox());
            }
        }
        bounds(bbox.putObject("lonlat"), r.getLatLonBoundingBox());       
        return bbox;
    }
    
    public static JSONObj schema( JSONObj schema, FeatureType type, boolean details){
        if( type != null ){
            schema.put("name", type.getName().getLocalPart() );
            schema.put("namespace", type.getName().getNamespaceURI() );
            schema.put("simple", type instanceof SimpleFeatureType );
            JSONArr attributes = schema.putArray("attributes");
            for( PropertyDescriptor d : type.getDescriptors() ){
                PropertyType t = d.getType();
                final String NAME = d.getName().getLocalPart();
                String kind;
                if (d instanceof GeometryDescriptor){
                    kind = "geometry";
                }
                else if( d instanceof AttributeDescriptor){
                    kind = "attribute";
                }
                else if (d instanceof AssociationDescriptor){
                    kind = "association";
                }
                else {
                    kind = "property";
                }
                JSONObj property = attributes.addObject()
                    .put("name", NAME )
                    .put("property", kind )
                    .put("type", t.getBinding().getSimpleName() );
                
                if( d instanceof GeometryDescriptor){
                    GeometryDescriptor g = (GeometryDescriptor) d;                    
                    proj( property.putObject("proj"), g.getCoordinateReferenceSystem(), null );
                }

                if( details){
                    property
                        .put("namespace", d.getName().getNamespaceURI() )
                        .put("description", t.getDescription() )
                        .put("min-occurs",d.getMinOccurs() )
                        .put("max-occurs",d.getMaxOccurs() )
                        .put("nillable",d.isNillable());
                
                    int length = FeatureTypes.getFieldLength(d);
                    if( length != FeatureTypes.ANY_LENGTH ){
                        property.put("length", length );
                    }
                    
                    if( d instanceof AttributeDescriptor){
                        AttributeDescriptor a = (AttributeDescriptor) d;
                        property.put("default-value", a.getDefaultValue() );
                    }
                    if( !t.getRestrictions().isEmpty() ){
                        JSONArr validate = property.putArray("validate");
                        for( Filter f : t.getRestrictions() ){
                            String cql;
                            try {
                                Filter clean = (Filter) f.accept( new DuplicatingFilterVisitor(){
                                    public PropertyName visit(PropertyName e, Object extraData ){
                                        String n = e.getPropertyName();
                                        return getFactory(extraData).property(
                                                ".".equals(n) ? NAME : n,
                                                e.getNamespaceContext());
                                    }
                                }, null );
                                cql = ECQL.toCQL(clean);
                            }
                            catch (Throwable ignore ){
                                ignore.printStackTrace();
                                cql = f.toString();
                            }
                            validate.add( cql );
                        }                    
                    }
                }
            }
        }
        return schema;
    }
    
    /**
     * Generate schema for GridCoverageSchema (see {@link FeatureUtilities#wrapGridCoverage}).
     */
    public static JSONObj schemaGrid( JSONObj schema, CoverageInfo info, boolean details ){
        if( info != null ){
            CoordinateReferenceSystem crs = info.getCRS() != null
                    ? info.getCRS()
                    : info.getNativeCRS();
            schemaGrid( schema, crs, details );
        }
        return schema;
    }
    public static JSONObj schemaGrid( JSONObj schema, CoordinateReferenceSystem crs, boolean details){
        schema.put("name", "GridCoverage" );
        schema.put("simple", true );
        JSONArr attributes = schema.putArray("attributes");
        JSONObj geom = attributes.addObject()
            .put("name", "geom" )
            .put("property", "geometry" )
            .put("type", "Polygon" );

        if( crs != null ){
            proj( geom.putObject("proj"), crs, null );
        }
        
        if( details ){
            geom
                .put("min-occurs",0)
                .put("max-occurs",1)
                .put("nillable",true)
                .put("default-value",null);   
        
        }
        JSONObj grid = attributes.addObject()
            .put("name", "grid" )
            .put("property", "attribute" )
            .put("type", "grid" );
        
        if( details ){
            grid
                .put("binding", "GridCoverage" )
                .put("min-occurs",0)
                .put("max-occurs",1)
                .put("nillable",true)
                .put("default-value",null);
        }
        return schema;
    }
    
    private static PrettyTime PRETTY_TIME = new PrettyTime();

    static JSONObj date(JSONObj obj, Date date) {
        String timestamp = DateUtil.formatDate( date );
        return obj.put("timestamp", timestamp).put("pretty", PRETTY_TIME.format(date));
    }

    static JSONObj metadata(JSONObj obj, Info i) {
        Date date = Metadata.created(i);
        if (date != null) {
            date(obj.putObject("created"), date);
        }

        date = Metadata.modified(i);
        if (date != null) {
            date(obj.putObject("modified"), date);
        }

        return obj;
    }

    public static JSONObj error(JSONObj json, Throwable error) {
        if (error != null) {
            String message = null;
            JSONArr cause = new JSONArr();
            for (Throwable t : Throwables.getCausalChain(error)) {
                if (message == null && t.getMessage() != null) {
                    message = t.getMessage();
                }
                cause.add(t.toString());
            }
            json.put("message", message != null ? message : error.toString())
                .put("cause", cause)
                .put("trace", Throwables.getStackTraceAsString(error));
        }
        else {
            json.put("message", "Unknown error");
        }
        return json;
    }
}
