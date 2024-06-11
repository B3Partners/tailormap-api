/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.SearchIndex;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.repository.SearchIndexRepository;
import nl.b3p.tailormap.api.solr.SolrHelper;
import nl.b3p.tailormap.api.solr.SolrService;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;

/** Event handler for Solr indexes when a {@code TMFeatureType} is updated or deleted. */
@RepositoryEventHandler
public class SolrTMFeatureTypeEventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SearchIndexRepository searchIndexRepository;
  private final SolrService solrService;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final GeoServiceRepository geoServiceRepository;

  public SolrTMFeatureTypeEventHandler(
      SearchIndexRepository searchIndexRepository,
      SolrService solrService,
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      GeoServiceRepository geoServiceRepository) {
    this.searchIndexRepository = searchIndexRepository;
    this.solrService = solrService;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.geoServiceRepository = geoServiceRepository;
  }

  /**
   * Handle the update of a TMFeatureType.
   *
   * @param tmFeatureType the TMFeatureType to handle
   */
  @HandleBeforeSave
  public void handleTMFeatureTypeUpdate(TMFeatureType tmFeatureType) {
    logger.debug("Handling TMFeatureType save event for: {}", tmFeatureType);
    // determine if it is a new FT or an update
    if (null == tmFeatureType.getId()) {
      // do nothing as there is no index defined for a new feature type
      logger.debug("New TMFeatureType: {}", tmFeatureType);
    } else {
      logger.debug("Updated TMFeatureType: {}", tmFeatureType);
      searchIndexRepository.findByFeatureTypeId(tmFeatureType.getId()).stream()
          .findAny()
          .ifPresent(
              searchIndex -> {
                logger.debug(
                    "Updating search index {} for feature type: {}",
                    searchIndex.getName(),
                    searchIndex);

                try (SolrHelper solrHelper =
                    new SolrHelper(solrService.getSolrClientForIndexing())) {
                  solrHelper.addFeatureTypeIndex(
                      searchIndex, tmFeatureType, featureSourceFactoryHelper);
                } catch (UnsupportedOperationException
                    | IOException
                    | SolrServerException
                    | SolrException e) {
                  logger.error("Error re-indexing", e);
                  searchIndex.setStatus(SearchIndex.Status.ERROR);
                  searchIndexRepository.save(searchIndex);
                }
              });
    }
  }

  /**
   * Handle the deletion of a TMFeatureType.
   *
   * @param tmFeatureType the TMFeatureType to handle
   */
  @HandleAfterDelete
  public void handleTMFeatureTypeDeleteForSolr(TMFeatureType tmFeatureType) {
    logger.debug("Handling TMFeatureType delete event for: {}", tmFeatureType);
    searchIndexRepository.findByFeatureTypeId(tmFeatureType.getId()).stream()
        .findAny()
        .ifPresent(
            searchIndex -> {
              logger.info(
                  "Deleting search index {} for feature type: {}",
                  searchIndex.getName(),
                  searchIndex);

              try (SolrHelper solrHelper = new SolrHelper(solrService.getSolrClientForIndexing())) {
                solrHelper.clearIndexForLayer(searchIndex.getId());
                searchIndexRepository.delete(searchIndex);
                // find layers that use this index in the layer settings and clear them
                geoServiceRepository
                    .findByIndexId(searchIndex.getId())
                    .forEach(
                        geoService ->
                            geoService
                                .getLayers()
                                .forEach(
                                    layer -> {
                                      logger.debug(
                                          "Checking layer {} for search index {}",
                                          layer.getName(),
                                          searchIndex.getName());
                                      geoService
                                          .findSearchIndexForLayer(layer, searchIndexRepository)
                                          .ifPresent(
                                              searchIndex1 -> {
                                                if (searchIndex1
                                                    .getId()
                                                    .equals(searchIndex.getId())) {
                                                  logger.debug(
                                                      "Clearing search index for layer {}",
                                                      layer.getName());
                                                  geoService
                                                      .getLayerSettings(layer.getName())
                                                      .setSearchIndex(null);
                                                  geoServiceRepository.save(geoService);
                                                }
                                              });
                                    }));
              } catch (UnsupportedOperationException
                  | IOException
                  | SolrServerException
                  | SolrException e) {
                logger.error("Error deleting index for {}", searchIndex, e);
              }
            });
  }
}
