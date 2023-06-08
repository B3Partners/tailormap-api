/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.listener;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_CREATED;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_DELETED;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_UPDATED;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import nl.b3p.tailormap.api.admin.model.EntityEvent;
import nl.b3p.tailormap.api.admin.model.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.stereotype.Component;

@Component
public class EntityEventPublisher {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired @Lazy private EntityManagerFactory entityManagerFactory;

  @Autowired @Lazy private ObjectMapper objectMapper;

  @Autowired @Lazy private SseEventBus eventBus;

  @Autowired @Lazy private RepositoryRestMvcConfiguration repositoryRestMvcConfiguration;

  public EntityEventPublisher() {}

  private void sendEvent(
      ServerSentEvent.EventTypeEnum eventTypeEnum, Object entity, boolean serializeEntity) {
    Object id = null;
    try {
      id = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
      EntityEvent entityEvent =
          new EntityEvent().entityName(entity.getClass().getSimpleName()).id(String.valueOf(id));
      if (serializeEntity) {
        entityEvent.setObject(repositoryRestMvcConfiguration.objectMapper().valueToTree(entity));
      }
      ServerSentEvent event = new ServerSentEvent().eventType(eventTypeEnum).details(entityEvent);
      this.eventBus.handleEvent(SseEvent.of(DEFAULT_EVENT, objectMapper.writeValueAsString(event)));
    } catch (Exception e) {
      logger.error(
          "Error sending SSE for event type {}, entity {}, id {}",
          eventTypeEnum,
          entity != null ? entity.getClass().getSimpleName() : null,
          id,
          e);
    }
  }

  @PostPersist
  public void postPersist(Object entity) {
    sendEvent(ENTITY_CREATED, entity, true);
  }

  @PostRemove
  public void postRemove(Object entity) {
    sendEvent(ENTITY_DELETED, entity, false);
  }

  @PostUpdate
  public void postUpdate(Object entity) {
    sendEvent(ENTITY_UPDATED, entity, true);
  }
}
