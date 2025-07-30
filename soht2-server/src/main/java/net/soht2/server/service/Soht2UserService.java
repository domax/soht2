/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static io.vavr.Predicates.not;
import static java.util.Optional.ofNullable;
import static net.soht2.server.service.ExceptionHelper.*;
import static org.springframework.util.StringUtils.hasText;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.entity.UserEntity;
import net.soht2.server.repository.UserEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Service for managing users in the SOHT2 server. This service provides methods to load user
 * details, create, update, delete users, and manage user roles.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class Soht2UserService implements UserDetailsService {

  private static final String ERR_USER_NOT_FOUND = "User not found";
  private static final String ERR_USER_EMPTY = "User name must be provided";
  private static final String ERR_TARGET =
      "Invalid allowed target format. Must be in the format 'host:123' or '*.host:*'";

  private static final Pattern RE_TARGET = Pattern.compile("^[a-z0-9.*-]+:[0-9*]+$");

  private final Soht2ServerConfig soht2ServerConfig;
  private final UserEntityRepository userEntityRepository;
  private final PasswordEncoder passwordEncoder;
  private final Cache userCache;

  @Setter(onMethod_ = {@Autowired, @Lazy})
  private Soht2UserService self;

  /**
   * Loads a user by username. This method is used by Spring Security to authenticate users.
   *
   * @param username the username of the user to load
   * @return a {@link UserDetails} object containing user information
   * @throws UsernameNotFoundException if the user is not found
   */
  @Transactional(readOnly = true)
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    log.trace("loadUserByUsername: username={}", username);
    return self.getCachedUserEntity(username)
        .map(
            userEntity ->
                User.withUsername(userEntity.getName())
                    .password(userEntity.getPassword())
                    .authorities(userEntity.getRole())
                    .build())
        .orElseThrow(() -> new UsernameNotFoundException(ERR_USER_NOT_FOUND));
  }

  /**
   * Retrieves a cached user entity by username. If the user is not found in the cache, it queries
   * the database.
   *
   * @param username the username of the user to retrieve
   * @return an {@link Optional} containing the {@link UserEntity} if found, or empty if not found
   */
  @Cacheable(cacheNames = "userCache", key = "#username")
  public Optional<UserEntity> getCachedUserEntity(String username) {
    return userEntityRepository.findByNameIgnoreCase(username);
  }

  /**
   * Checks if the admin user exists in the database. If not, it creates a new admin user with the
   * default username and password specified in the server configuration.
   */
  @Transactional
  public void checkAdminUserExists() {
    val username = soht2ServerConfig.getAdminUsername().toLowerCase();
    val password = soht2ServerConfig.getDefaultAdminPassword();
    val admin =
        userEntityRepository
            .findByNameIgnoreCase(username)
            .orElseGet(
                () ->
                    userEntityRepository.save(
                        UserEntity.builder()
                            .name(username)
                            .password(passwordEncoder.encode(password))
                            .role(UserEntity.ROLE_ADMIN)
                            .build()));
    log.debug("checkAdminUserExists: admin={}", admin);
  }

  /**
   * Creates a new user with the specified name, password, and role.
   *
   * @param name the name of the user to create
   * @param password the password for the new user
   * @param role the role of the new user (default is {@link UserEntity#ROLE_USER})
   * @param allowedTargets a set of allowed targets for the user
   * @return a {@link Soht2User} object representing the created user
   * @throws HttpClientErrorException.BadRequest if the name of the user or password is not
   *     provided, or if the specified user already exists
   */
  @Transactional
  public Soht2User createUser(
      String name, String password, @Nullable String role, Set<String> allowedTargets)
      throws HttpClientErrorException {
    log.debug("createUser: name={}, role={}, allowedTargets={}", name, role, allowedTargets);

    if (!hasText(name)) throw badRequest(ERR_USER_EMPTY);
    if ("self".equalsIgnoreCase(name)) throw badRequest("Cannot create user with name 'self'");
    if (!name.matches("^\\w+$"))
      throw badRequest("User name must consist of alphanumeric characters and underscores only");
    if (!hasText(password)) throw badRequest("Password must be provided");
    if (userEntityRepository.existsByNameIgnoreCase(name)) throw badRequest("User already exists");
    if (!allowedTargets.stream().allMatch(RE_TARGET.asMatchPredicate()))
      throw badRequest(ERR_TARGET);

    return userEntityRepository
        .save(
            UserEntity.builder()
                .name(name.toLowerCase())
                .password(passwordEncoder.encode(password))
                .role(checkRole(role).orElse(UserEntity.ROLE_USER))
                .allowedTargets(allowedTargets)
                .build())
        .toSoht2User();
  }

  /**
   * Changes the details of an existing user.
   *
   * @param name the name of the user to change
   * @param password the new password for the user (optional)
   * @param role the new role for the user (optional)
   * @param allowedTargets a set of allowed targets for the user
   * @return a {@link Soht2User} object representing the updated user
   * @throws HttpClientErrorException.BadRequest if the name of the user is not provided or if the
   *     role is invalid
   */
  @Transactional
  public Soht2User updateUser(
      String name,
      @Nullable String password,
      @Nullable String role,
      @Nullable Set<String> allowedTargets)
      throws HttpClientErrorException {
    log.debug("updateUser: name={}, role={}, allowedTargets={}", name, role, allowedTargets);

    if (!hasText(name)) throw badRequest(ERR_USER_EMPTY);
    if (!ofNullable(allowedTargets).stream()
        .flatMap(Collection::stream)
        .allMatch(RE_TARGET.asMatchPredicate())) throw badRequest(ERR_TARGET);

    var userEntity =
        userEntityRepository
            .findByNameIgnoreCase(name)
            .orElseThrow(() -> notFound(ERR_USER_NOT_FOUND));

    var hasChanges = false;
    if (hasText(password)) {
      userEntity.setPassword(passwordEncoder.encode(password));
      hasChanges = true;
    }
    if (hasText(role)) {
      checkRole(role).ifPresent(userEntity::setRole);
      hasChanges = true;
    }
    if (allowedTargets != null) {
      userEntity.setAllowedTargets(allowedTargets);
      hasChanges = true;
    }
    if (hasChanges) {
      userEntity = userEntityRepository.save(userEntity);
      userCache.evict(name);
    }
    return userEntity.toSoht2User();
  }

  /**
   * Deletes a user by name. If the specified user is an admin, the force parameter must be set to
   * true to allow deletion.
   *
   * @param name the name of the user to delete
   * @param force if true, allows deletion of admin users; otherwise, throws an exception
   * @throws HttpClientErrorException.BadRequest if the name of the user is not provided
   * @throws HttpClientErrorException.Forbidden if the user to be deleted is admin and {@code force}
   *     argument is {@code false}
   */
  @Transactional
  public void deleteUser(String name, boolean force) throws HttpClientErrorException {
    log.debug("deleteUser: name={}", name);

    if (!hasText(name)) throw badRequest(ERR_USER_EMPTY);
    val userEntity =
        userEntityRepository
            .findByNameIgnoreCase(name)
            .orElseThrow(() -> notFound(ERR_USER_NOT_FOUND));
    if (userEntity.getRole().equals(UserEntity.ROLE_ADMIN) && !force)
      throw forbidden("Cannot delete admin user");
    userEntityRepository.delete(userEntity);
    userCache.evict(name);
  }

  /**
   * Lists all users in the system.
   *
   * @return a list of {@link Soht2User} objects representing all users
   */
  @Transactional(readOnly = true)
  public List<Soht2User> listUsers() {
    log.debug("listUsers");
    return userEntityRepository.findAll().stream().map(UserEntity::toSoht2User).toList();
  }

  /**
   * Lists users by their names.
   *
   * @param names a collection of usernames to retrieve
   * @return a list of {@link Soht2User} objects representing the specified users
   */
  public List<Soht2User> listUsers(Collection<String> names) {
    log.debug("listUsers: names={}", names);
    return userEntityRepository.findAllByNameIgnoreCaseIn(names).stream()
        .map(UserEntity::toSoht2User)
        .toList();
  }

  /**
   * Retrieves the details of the currently logged-in user.
   *
   * @param authentication the current authentication object containing user details
   * @return a {@link Soht2User} object representing the current user
   * @throws HttpClientErrorException.Unauthorized if no user is logged in
   * @throws HttpClientErrorException.Forbidden if the user is not found
   */
  @Transactional(readOnly = true)
  public Soht2User getSelf(Authentication authentication) throws HttpClientErrorException {
    log.debug("getSelf");
    return userEntityRepository
        .findByNameIgnoreCase(authentication.getName())
        .map(UserEntity::toSoht2User)
        .orElseThrow(() -> forbidden(ERR_USER_NOT_FOUND));
  }

  /**
   * Changes the password of the currently logged-in user. The old password must be provided for
   * verification.
   *
   * @param oldPassword the current password of the user
   * @param newPassword the new password to set for the user
   * @param authentication the current authentication object containing user details
   * @return a {@link Soht2User} object representing the updated user
   * @throws HttpClientErrorException.BadRequest if the old or new password is not provided, or if
   *     they are the same
   * @throws HttpClientErrorException.Unauthorized if no user is logged in
   * @throws HttpClientErrorException.Forbidden if the user is not found
   */
  @Transactional
  public Soht2User changePassword(
      String oldPassword, String newPassword, Authentication authentication)
      throws HttpClientErrorException {
    log.debug(
        "changePassword: old.size={}, new.size={}", oldPassword.length(), newPassword.length());
    if (!hasText(oldPassword)) throw badRequest("Old password must be provided");
    if (!hasText(newPassword)) throw badRequest("New password must be provided");
    if (oldPassword.equals(newPassword))
      throw badRequest("New password must be different from old password");
    var userEntity =
        userEntityRepository
            .findByNameIgnoreCase(authentication.getName())
            .orElseThrow(() -> forbidden(ERR_USER_NOT_FOUND));
    if (!passwordEncoder.matches(oldPassword, userEntity.getPassword()))
      throw badRequest("Old password is incorrect");

    userEntity.setPassword(passwordEncoder.encode(newPassword));
    userEntity = userEntityRepository.save(userEntity);
    userCache.evict(authentication.getName());

    return userEntity.toSoht2User();
  }

  private Optional<String> checkRole(@Nullable String role) {
    return ofNullable(role)
        .filter(not(String::isBlank))
        .map(String::toUpperCase)
        .map(
            r ->
                Optional.of(r)
                    .filter(UserEntity.ROLES::contains)
                    .orElseThrow(() -> badRequest("Invalid role: " + r)));
  }

  static Optional<CurrentUser> getCurrentUser(Authentication authentication) {
    return Optional.of(authentication)
        .map(Authentication::getPrincipal)
        .filter(UserDetails.class::isInstance)
        .map(UserDetails.class::cast)
        .map(CurrentUser::from);
  }

  @Builder
  record CurrentUser(String name, String role, boolean isAdmin) {
    static CurrentUser from(UserDetails userDetails) {
      return new CurrentUser(
          userDetails.getUsername().toLowerCase(),
          userDetails.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .findAny()
              .orElse(UserEntity.ROLE_USER),
          userDetails.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .anyMatch(UserEntity.ROLE_ADMIN::equals));
    }
  }
}
