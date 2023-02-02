/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Collection;
import java.util.List;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Easy to use methods to access {@link ApplicationLayer}.
 *
 * @since 0.1
 */
public interface ApplicationLayerRepository extends JpaRepository<ApplicationLayer, Long> {
  @EntityGraph(
      attributePaths = {"readers", "details"},
      type = EntityGraphType.LOAD)
  List<ApplicationLayer> findWithReadersAndDetailsByIdIn(Collection<Long> id);
}
