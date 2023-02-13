/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

@Entity
@Table(name = "groups")
public class Group {
  // May access app for which authentication is required. Role to be assigned by default to any
  // users created manually. Exists to give the account with the "actuator" role only access to
  // /api/actuator/ and not apps for which authentication is required.
  public static final String APP_AUTHENTICATED = "app-authenticated";
  public static final String ADMIN = "admin";
  public static final String ADMIN_CATALOG = "admin-catalog";
  public static final String ADMIN_USERS = "admin-users";
  public static final String ADMIN_APPLICATIONS = "admin-applications";
  public static final String ACTUATOR = "actuator";

  @Id private String name;

  private boolean systemGroup;

  private String description;

  @Column(columnDefinition = "text")
  private String notes;

  @ManyToMany(mappedBy = "groups")
  private Set<User> members = new HashSet<>();

  public String getName() {
    return name;
  }

  public Group setName(String name) {
    this.name = name;
    return this;
  }

  public boolean isSystemGroup() {
    return systemGroup;
  }

  public Group setSystemGroup(boolean systemGroup) {
    this.systemGroup = systemGroup;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public Group setDescription(String title) {
    this.description = title;
    return this;
  }

  public String getNotes() {
    return notes;
  }

  public Group setNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public Set<User> getMembers() {
    return members;
  }

  public Group setMembers(Set<User> members) {
    this.members = members;
    return this;
  }
}