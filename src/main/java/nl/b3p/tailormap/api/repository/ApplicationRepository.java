/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.List;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
public interface ApplicationRepository extends JpaRepository<Application, Long> {
  @PreAuthorize("permitAll()")
  Application findByName(String name);

  @PreAuthorize("permitAll()")
  @Override
  @NonNull
  Optional<Application> findById(@NonNull Long aLong);

  /**
   * Find all applications that have a layer that is linked to a specific (Solr) index.
   *
   * @param indexId The index id to search for
   */
  @NonNull
  @PreAuthorize("permitAll()")
  @Query(
      value =
          "select * from application app, lateral jsonb_path_query(app.settings, ('$.layerSettings.**{1}.searchIndexId ? (@ == '||:indexId||')')::jsonpath)",
      nativeQuery = true)
  List<Application> findByIndexId(@Param("indexId") @NonNull Long indexId);
}
