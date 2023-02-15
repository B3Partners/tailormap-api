/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.FeatureSource;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "feature-sources",
    collectionResourceRel = "feature-sources",
    itemResourceRel = "feature-source")
public interface FeatureSourceRepository extends JpaRepository<FeatureSource, Long> {
  FeatureSource findByUrl(String url);
}
