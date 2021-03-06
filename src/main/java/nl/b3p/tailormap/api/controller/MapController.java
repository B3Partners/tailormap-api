/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import nl.b3p.tailormap.api.geotools.referencing.ReferencingHelper;
import nl.b3p.tailormap.api.model.AppLayer;
import nl.b3p.tailormap.api.model.Bounds;
import nl.b3p.tailormap.api.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.LayerTreeNode;
import nl.b3p.tailormap.api.model.MapResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.model.Service;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.LayerRepository;
import nl.b3p.tailormap.api.repository.LevelRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.b3p.tailormap.api.util.ParseUtil;
import nl.tailormap.viewer.config.ClobElement;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.app.Level;
import nl.tailormap.viewer.config.app.StartLayer;
import nl.tailormap.viewer.config.app.StartLevel;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.TileService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.EntityNotFoundException;
import javax.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping(path = "/app/{appId}/map", produces = MediaType.APPLICATION_JSON_VALUE)
public class MapController {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ApplicationLayerRepository applicationLayerRepository;
    @Autowired private LevelRepository levelRepository;
    @Autowired private LayerRepository layerRepository;

    @Operation(
            summary = "",
            tags = {},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = MapResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = RedirectResponse.class)))
            })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Timed(value = "get_map", description = "time spent to process get the map of an application")
    public ResponseEntity<Serializable> get(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId) {
        logger.trace("Requesting 'map' for application id: " + appId);

        Application application = applicationRepository.findWithGeoservicesById(appId);
        if (application == null) {
            throw new EntityNotFoundException();
        } else if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            MapResponse mapResponse = new MapResponse();
            getApplicationParams(application, mapResponse);
            getLayers(application, mapResponse);

            return ResponseEntity.status(HttpStatus.OK).body(mapResponse);
        }
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(
            value =
                    HttpStatus
                            .NOT_FOUND /*,reason = "Not Found" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleEntityNotFoundException(EntityNotFoundException exception) {
        logger.warn(
                "Requested an application that does not exist. Message: " + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application that does not exist")
                .code(HttpStatus.NOT_FOUND.value());
    }

    private void getApplicationParams(@NotNull Application a, @NotNull MapResponse mapResponse) {
        final String pCode = a.getProjectionCode();

        CoordinateReferenceSystem c = new CoordinateReferenceSystem();
        if (null != pCode) {
            c.code(ParseUtil.parseEpsgCode(pCode)).definition(ParseUtil.parseProjDefintion(pCode));
        }
        Bounds maxExtent = new Bounds();
        if (null != a.getMaxExtent()) {
            maxExtent
                    .minx(a.getMaxExtent().getMinx())
                    .miny(a.getMaxExtent().getMiny())
                    .maxx(a.getMaxExtent().getMaxx())
                    .maxy(a.getMaxExtent().getMaxy())
                    .crs(a.getMaxExtent().getCrs().getName());
        } else {
            maxExtent = ReferencingHelper.crsBoundsExtractor(c.getCode());
        }
        Bounds initialExtent = new Bounds();
        if (null != a.getStartExtent()) {
            initialExtent
                    .minx(a.getStartExtent().getMinx())
                    .miny(a.getStartExtent().getMiny())
                    .maxx(a.getStartExtent().getMaxx())
                    .maxy(a.getStartExtent().getMaxy())
                    .crs(a.getStartExtent().getCrs().getName());
        } else {
            initialExtent = maxExtent;
        }

        mapResponse.crs(c).maxExtent(maxExtent).initialExtent(initialExtent);
    }

    private String getNameForAppLayer(
            @NotNull ApplicationLayer layer, @NotNull List<Layer> layers) {
        if (ClobElement.isNotBlank(layer.getDetails().get("titleAlias"))) {
            return layer.getDetails().get("titleAlias").getValue();
        } else {
            Layer serviceLayer = null;
            for (Layer possibleLayer : layers) {
                if (possibleLayer.getService().equals(layer.getService())
                        && Objects.equals(possibleLayer.getName(), layer.getLayerName())) {
                    serviceLayer = possibleLayer;
                    break;
                }
            }

            if (serviceLayer != null) {
                return serviceLayer.getDisplayName();
            } else {
                return layer.getLayerName();
            }
        }
    }

    private static boolean isAuthorized(Set<String> readers, Authentication auth) {
        if (readers == null || readers.isEmpty()) {
            return true;
        }

        for (String reader : readers) {
            if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(reader))) {
                return true;
            }
        }

        return false;
    }

    private void getLayers(@NotNull Application a, @NotNull MapResponse mapResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        LayerTreeNode rootNode = new LayerTreeNode().id("root").root(true).name("Foreground");
        mapResponse.addLayerTreeNodesItem(rootNode);

        LayerTreeNode rootBackgroundNode =
                new LayerTreeNode().id("rootbg").root(true).name("Background");
        mapResponse.addBaseLayerTreeNodesItem(rootBackgroundNode);

        List<Long> ids = new ArrayList<>();
        Map<Long, List<Level>> levelChildrenMap = new HashMap<>();
        for (Level level : levelRepository.findByLevelTree(a.getRoot().getId())) {
            if (level.getParent() != null) {
                levelChildrenMap
                        .computeIfAbsent(level.getParent().getId(), (Long t) -> new ArrayList<>())
                        .add(level);
            }
            ids.add(level.getId());
        }

        // Preload the list of levels, layers, and their readers into the repository's cache.
        // Note that the result of this is not used directly; this merely ensures that
        // `level.getLayers()` doesn't need another roundtrip later.
        levelRepository.findWithAuthorizationDataByIdIn(ids);

        // List of used ApplicationLayer IDs in this map.
        Set<Long> applicationLayerIds = new HashSet<>();

        List<StartLayer> startLayers = a.getStartLayers();
        for (StartLayer startLayer : startLayers) {
            if (startLayer.isRemoved()) {
                continue;
            }

            applicationLayerIds.add(startLayer.getApplicationLayer().getId());
        }

        // As above, preload the list of ApplicationLayers, their authorization, and details (used
        // for display name). We already have handles to ApplicationLayers in the StartLayer
        // objects.
        applicationLayerRepository.findWithReadersAndDetailsByIdIn(applicationLayerIds);

        // Map of (authorized) ApplicationLayer ID to StartLayer objects.
        Map<Long, StartLayer> layerMap = new HashMap<>(startLayers.size());
        for (StartLayer startLayer : startLayers) {
            if (startLayer.isRemoved()) {
                continue;
            }

            ApplicationLayer appLayer = startLayer.getApplicationLayer();
            if (!isAuthorized(appLayer.getReaders(), authentication)) {
                continue;
            }
            layerMap.put(appLayer.getId(), startLayer);
        }

        // Fetch the list of possible StartLevels, and filter them down to ones that aren't removed
        // and are assigned an index.
        List<StartLevel> startLevels = a.getStartLevels();
        startLevels.removeIf((StartLevel t) -> t.isRemoved() || t.getSelectedIndex() == null);
        startLevels.sort(Comparator.comparingLong(StartLevel::getSelectedIndex));

        // Authorized StartLevel IDs
        Set<Long> visibleLevels = new HashSet<>();
        List<StartLayer> visibleStartLayers = new ArrayList<>();

        // Iterate over startLevels twice. Once to figure out visibility and authentication, once to
        // actually build the returned structure.
        Deque<Level> levelQueue = new ArrayDeque<>();
        for (StartLevel l : startLevels) {
            levelQueue.add(l.getLevel());
            while (!levelQueue.isEmpty()) {
                Level level = levelQueue.pop();
                if (visibleLevels.contains(level.getId())) {
                    continue;
                }

                if (!isAuthorized(level.getReaders(), authentication)) {
                    continue;
                }

                visibleLevels.add(level.getId());
                levelQueue.addAll(levelChildrenMap.getOrDefault(level.getId(), List.of()));
                for (ApplicationLayer layer : level.getLayers()) {
                    StartLayer startLayer = layerMap.get(layer.getId());
                    if (startLayer == null) {
                        continue;
                    }

                    visibleStartLayers.add(startLayer);
                }
            }
        }

        // To check for visibility on the GeoService, we need each Layer and their parents. This
        // could be done with a native query, however, we also need each layer's readers, which
        // would require another roundtrip. Take the bandwidth hit and fetch all the used
        // GeoServices' layers.
        Set<Long> neededServiceIds = new HashSet<>();
        for (StartLayer l : visibleStartLayers) {
            neededServiceIds.add(l.getApplicationLayer().getService().getId());
        }
        List<Layer> layers = layerRepository.findByServiceIdIn(neededServiceIds);

        // Check the visibility of each visible StartLayer's corresponding Layer.
        for (StartLayer startLayer : visibleStartLayers) {
            ApplicationLayer applicationLayer = startLayer.getApplicationLayer();
            Layer serviceVisibilityLayer = null;
            for (Layer l : layers) {
                if (l.getService().equals(applicationLayer.getService())
                        && Objects.equals(l.getName(), applicationLayer.getLayerName())) {
                    serviceVisibilityLayer = l;
                    break;
                }
            }

            boolean isLayerVisible =
                    isAuthorized(serviceVisibilityLayer.getService().getReaders(), authentication);
            while (isLayerVisible && serviceVisibilityLayer != null) {
                if (!isAuthorized(serviceVisibilityLayer.getReaders(), authentication)) {
                    isLayerVisible = false;
                    break;
                }

                serviceVisibilityLayer = serviceVisibilityLayer.getParent();
            }

            // If the Layer is not visible, remove the ApplicationLayer from the layerMap.
            if (!isLayerVisible) {
                layerMap.remove(applicationLayer.getId());
            }
        }

        // Repopulate the visible StartLayer list while iterating over StartLevels, this time taking
        // each Layer's visibility in account.
        visibleStartLayers.clear();

        Map<Long, LayerTreeNode> treeNodeMap = new HashMap<>();
        for (StartLevel l : startLevels) {
            // Check if this level is a child of a background level. In the API background levels
            // are returned in a separate tree. Only children of the Level with isBackground() set
            // to true can be a StartLevel, so we need to check all parents only (not the Level of
            // the StartLevel itself).
            boolean isBackground = false;
            Level parentLevel = l.getLevel();
            while (parentLevel != null && !isBackground) {
                isBackground = parentLevel.isBackground();
                parentLevel = parentLevel.getParent();
            }

            Level startLevel = l.getLevel();
            List<LayerTreeNode> treeNodeList;
            LayerTreeNode chosenRoot;
            if (isBackground) {
                treeNodeList = mapResponse.getBaseLayerTreeNodes();
                chosenRoot = rootBackgroundNode;
            } else {
                treeNodeList = mapResponse.getLayerTreeNodes();
                chosenRoot = rootNode;
            }

            levelQueue.add(startLevel);
            while (!levelQueue.isEmpty()) {
                Level level = levelQueue.pop();
                if (treeNodeMap.containsKey(level.getId())
                        || !visibleLevels.contains(level.getId())) {
                    continue;
                }

                // Use a prefix to make the LayerTreeNode ids in the tree containing both Level and
                // ApplicationLayer nodes unique
                LayerTreeNode childNode =
                        new LayerTreeNode()
                                .id(String.format("lvl_%d", level.getId()))
                                .name(level.getName())
                                .root(false)
                                .childrenIds(new ArrayList<>());

                treeNodeList.add(childNode);
                treeNodeMap.put(level.getId(), childNode);

                LayerTreeNode parentNode;
                if (level == startLevel) {
                    parentNode = chosenRoot;
                } else {
                    parentNode = treeNodeMap.get(level.getParent().getId());
                }
                parentNode.addChildrenIdsItem(childNode.getId());

                levelQueue.addAll(levelChildrenMap.getOrDefault(level.getId(), List.of()));
                for (ApplicationLayer layer : level.getLayers()) {
                    StartLayer startLayer = layerMap.get(layer.getId());
                    if (startLayer == null) {
                        continue;
                    }

                    visibleStartLayers.add(startLayer);

                    LayerTreeNode layerNode =
                            new LayerTreeNode()
                                    .id(String.format("lyr_%d", layer.getId()))
                                    .name(getNameForAppLayer(layer, layers))
                                    .appLayerId((int) (long) layer.getId())
                                    .root(false)
                                    .childrenIds(new ArrayList<>());

                    treeNodeList.add(layerNode);
                    childNode.addChildrenIdsItem(layerNode.getId());
                }
            }
        }

        // Only add ApplicationLayers visible in the LayerTreeNode graph to the response.
        for (StartLayer l : visibleStartLayers) {
            ApplicationLayer applicationLayer = l.getApplicationLayer();
            Layer serviceLayer = null;
            for (Layer layer : layers) {
                if (layer.getService().equals(applicationLayer.getService())
                        && Objects.equals(layer.getName(), applicationLayer.getLayerName())) {
                    serviceLayer = layer;
                    break;
                }
            }

            AppLayer.HiDpiModeEnum hiDpiMode = null;
            String hiDpiSubstituteLayer = null;

            if (serviceLayer != null) {
                ClobElement ce = serviceLayer.getDetails().get("hidpi.mode");
                if (ce != null) {
                    try {
                        hiDpiMode = AppLayer.HiDpiModeEnum.fromValue(ce.getValue());
                    } catch (IllegalArgumentException e) {
                        logger.warn(
                                String.format(
                                        "App #%s (%s): invalid hidpi.mode enum value for app layer #%s, service layer #%s (%s)",
                                        a.getId(),
                                        a.getNameWithVersion(),
                                        l.getId(),
                                        serviceLayer.getId(),
                                        serviceLayer.getName()));
                    }
                }
                ce = serviceLayer.getDetails().get("hidpi.substitute_layer");
                if (ce != null) {
                    hiDpiSubstituteLayer = ce.getValue();
                }
            }

            AppLayer appLayer =
                    new AppLayer()
                            .id(applicationLayer.getId())
                            .layerName(serviceLayer.getName())
                            .title(getNameForAppLayer(applicationLayer, layers))
                            .serviceId(applicationLayer.getService().getId())
                            .hiDpiMode(hiDpiMode)
                            .hiDpiSubstituteLayer(hiDpiSubstituteLayer)
                            .visible(l.isChecked())
                            .hasAttributes(!l.getApplicationLayer().getAttributes().isEmpty());

            mapResponse.addAppLayersItem(appLayer);

            GeoService geoService = applicationLayer.getService();

            // Use this default if saved before the form default was added in admin
            Service.HiDpiModeEnum serviceHiDpiMode = Service.HiDpiModeEnum.AUTO;
            ClobElement ce = geoService.getDetails().get("hidpi.mode");
            if (ce != null) {
                try {
                    serviceHiDpiMode = Service.HiDpiModeEnum.fromValue(ce.getValue());
                } catch (IllegalArgumentException e) {
                    logger.warn(
                            String.format(
                                    "App #%s (%s): invalid hidpi.mode enum value for service #%s (%s)",
                                    a.getId(),
                                    a.getNameWithVersion(),
                                    geoService.getId(),
                                    geoService.getName()));
                }
            }
            Integer tilingGutter = null;
            ce = geoService.getDetails().get("tiling.gutter");
            if (ce != null) {
                try {
                    tilingGutter = Integer.parseInt(ce.getValue());
                } catch (NumberFormatException ignored) {
                    // ignored
                }
            }

            Service s =
                    new Service()
                            .url(geoService.getUrl())
                            .id(geoService.getId())
                            .name(geoService.getName())
                            .protocol(Service.ProtocolEnum.fromValue(geoService.getProtocol()))
                            .hiDpiMode(serviceHiDpiMode)
                            .tilingDisabled(
                                    "true"
                                            .equals(
                                                    geoService
                                                            .getDetails()
                                                            .getOrDefault(
                                                                    "tiling.disable",
                                                                    new ClobElement("false)"))
                                                            .getValue()))
                            .tilingGutter(tilingGutter)
                            .capabilities(geoService.getCapabilitiesDoc());
            if (geoService.getProtocol().equalsIgnoreCase(TileService.PROTOCOL)) {
                s.tilingProtocol(
                        Service.TilingProtocolEnum.fromValue(
                                ((TileService) geoService).getTilingProtocol()));
            }
            mapResponse.addServicesItem(s);
        }
    }
}
