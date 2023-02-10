/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@EnableWebSecurity
public class TailormapUserDetailsService implements UserDetailsService {
  private final Log logger = LogFactory.getLog(getClass());

  private final UserRepository userRepository;

  public TailormapUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new UsernameNotFoundException("User " + username + " not found");
    }
    // This will usually log a {bcrypt}... password unless it was explicitly changed to {noop}...
    // So no plaintext passwords are logged
    logger.trace("Found user: " + user.getUsername() + ", password " + user.getPassword());
    return new TailormapUserDetails(user);
  }
}
