/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;

/**
 * setup testdata from property file.
 *
 * @author mprins
 */
public abstract class StaticTestData {
  protected static final Properties testData = new Properties();

  @BeforeAll
  static void readTestData() throws IOException {
    testData.load(StaticTestData.class.getResourceAsStream("/StaticTestData.properties"));
  }
}
