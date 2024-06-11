/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.solr;

import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SolrService {
  @Value("${tailormap-api.solr-url}")
  private String solrUrl;

  @Value("${tailormap-api.solr-core-name:tailormap}")
  private String solrCoreName;

  /**
   * Get a concurrent update Solr client for bulk operations.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForIndexing() {
    return new ConcurrentUpdateHttp2SolrClient.Builder(
            solrUrl + solrCoreName,
            new Http2SolrClient.Builder()
                .withFollowRedirects(true)
                .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                .withRequestTimeout(60000, TimeUnit.MILLISECONDS)
                .build())
        .withQueueSize(SolrHelper.SOLR_BATCH_SIZE * 2)
        .withThreadCount(10)
        .build();
  }

  /**
   * Get a Solr client for searching.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForSearching() {
    return new Http2SolrClient.Builder(solrUrl + solrCoreName)
        .withConnectionTimeout(10, TimeUnit.SECONDS)
        .withFollowRedirects(true)
        .build();
  }
}
