/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base.ddl;

import java.lang.invoke.MethodHandles;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("ddl")
public class ShutdownAfterCreatingDDL {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ApplicationContext appContext;

  public ShutdownAfterCreatingDDL(ApplicationContext appContext) {
    this.appContext = appContext;
  }

  @PostConstruct
  public void exit() {
    logger.info("Created DDL, exiting Spring application");
    SpringApplication.exit(appContext, () -> 0);
    logger.info("Exiting from JVM");
    System.exit(0);
  }
}
