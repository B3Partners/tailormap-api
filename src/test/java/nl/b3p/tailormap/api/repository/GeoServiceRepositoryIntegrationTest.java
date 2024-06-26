/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.GeoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresIntegrationTest
class GeoServiceRepositoryIntegrationTest {

  @Autowired private GeoServiceRepository geoServiceRepository;

  @Test
  void it_should_find_service_using_findByIndexId_with_valid_ID() {
    final GeoService geoService = geoServiceRepository.findByIndexId(2L).get(0);
    assertNotNull(geoService);
    assertEquals("snapshot-geoserver", geoService.getId());
  }

  @Test
  void it_should_not_find_services_findByIndexId_with_invalid_ID() {
    final List<GeoService> geoServices = geoServiceRepository.findByIndexId(-2L);
    assertTrue(geoServices.isEmpty());
  }
}
