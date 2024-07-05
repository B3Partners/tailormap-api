/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Application;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresIntegrationTest
class ApplicationRepositoryIntegrationTest {
  @Autowired private ApplicationRepository applicationRepository;

  @Test
  void should_find_application_by_name() {
    Application a = applicationRepository.findByName("default");
    assertNotNull(a);
    assertEquals("default", a.getName(), "application name is incorrect");
  }

  @Test
  void should_not_find_application_by_nonexistent_name() {
    Application a = applicationRepository.findByName("does-not-exist");
    assertNull(a);
  }

  @Test
  void it_should_find_application_using_findByIndexId_with_valid_ID() {
    final Application application = applicationRepository.findByIndexId(2L).get(0);
    Assertions.assertNotNull(application);
    assertEquals("default", application.getName());
  }

  @Test
  void it_should_not_find_applications_findByIndexId_with_invalid_ID() {
    final List<Application> applications = applicationRepository.findByIndexId(-2L);
    assertTrue(applications.isEmpty());
  }
}
