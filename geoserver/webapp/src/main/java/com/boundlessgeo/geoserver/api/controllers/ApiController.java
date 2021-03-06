/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerDataDirectory;
import org.geotools.feature.NameImpl;
import com.boundlessgeo.geoserver.api.exceptions.NotFoundException;

import javax.servlet.http.HttpServletRequest;

/**
 * Base class for api controllers.
 */
public abstract class ApiController {

    public static final int DEFAULT_PAGESIZE = 25;

    protected GeoServer geoServer;

    public ApiController(GeoServer geoServer) {
        this.geoServer = geoServer;
    }

    public Catalog catalog() {
        return geoServer.getCatalog();
    }

    protected GeoServerDataDirectory dataDir() {
        return new GeoServerDataDirectory(geoServer.getCatalog().getResourceLoader());
    }

    protected Integer page(HttpServletRequest req) {
        String page = req.getParameter("page");
        return page != null ? Integer.parseInt(page) : null;
    }

    protected Integer pageSize(HttpServletRequest req, Integer def) {
        String size = req.getParameter("pagesize");
        return size != null ? Integer.parseInt(size) : def;
    }

    protected Integer offset(HttpServletRequest req) {
        Integer page = page(req);
        return page != null ? page * count(req) : null;
    }

    protected Integer count(HttpServletRequest req) {
        return pageSize(req, 25);
    }

    protected WorkspaceInfo findWorkspace(String wsName, Catalog cat) {
        WorkspaceInfo ws = cat.getWorkspaceByName(wsName);
        if (ws == null) {
            throw new NotFoundException(String.format("No such workspace %s", wsName));
        }
        return ws;
    }

    protected LayerInfo findLayer(String wsName, String name, Catalog cat) {
        LayerInfo l = cat.getLayerByName(new NameImpl(wsName, name));
        if (l == null) {
            throw new NotFoundException(String.format("No such layer %s:%s", wsName, name));
        }
        return l;
    }

    protected StoreInfo findStore(String wsName, String name, Catalog cat) {
        StoreInfo s = cat.getStoreByName(wsName, name, StoreInfo.class);
        if (s == null) {
            throw new NotFoundException(String.format("No such store %s:%s", wsName, name));
        }
        return s;
    }

    /**
     * Returns the namespace associated with the specified workspace.
     */
    protected NamespaceInfo namespaceFor(WorkspaceInfo ws) {
        return geoServer.getCatalog().getNamespaceByPrefix(ws.getName());
    }
}
