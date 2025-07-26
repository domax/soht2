/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.entity.UserEntity;
import net.soht2.server.service.Soht2UserService;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing users in the SOHT2 system. Provides endpoints for creating, updating,
 * deleting, and listing users, as well as retrieving the current user's information and changing
 * their password.
 */
// <editor-fold desc="OpenAPI Annotations">
@OpenAPIDefinition(
    info = @Info(title = "SOHT2 Server API", version = "0.0.2"),
    servers = {
      @Server(url = "${soht2.server.open-api-server-url}", description = "SOHT2 OpenAPI Server")
    })
// </editor-fold>
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserController {

  private final Soht2UserService soht2UserService;

  /**
   * Creates a new user with the specified username, password, and optional role. Only users with
   * the admin role can create new users.
   *
   * @param username the username of the new user
   * @param password the password of the new user
   * @param role the role of the new user (optional)
   * @return the created {@link Soht2User} object
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Creates a new user with the specified name, password, and role.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("hasAuthority('" + UserEntity.ROLE_ADMIN + "')")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public Soht2User create(
      @RequestParam("username") String username,
      @RequestParam("password") String password,
      @RequestParam(name = "role", required = false) @Nullable String role) {
    return soht2UserService.createUser(username, password, role);
  }

  /**
   * Updates an existing user with the specified username, password, and optional role. Only users
   * with the admin role can update users.
   *
   * @param name the username of the user to update
   * @param password the new password for the user (optional)
   * @param role the new role for the user (optional)
   * @return the updated {@link Soht2User} object
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Updates an existing user with the specified name, password, and role.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("hasAuthority('" + UserEntity.ROLE_ADMIN + "')")
  @PutMapping(path = "/{name}", produces = APPLICATION_JSON_VALUE)
  public Soht2User update(
      @PathVariable("name") String name,
      @RequestParam(name = "password", required = false) @Nullable String password,
      @RequestParam(name = "role", required = false) @Nullable String role) {
    return soht2UserService.updateUser(name, password, role);
  }

  /**
   * Deletes a user with the specified username. Only users with the admin role can delete users.
   *
   * @param name the username of the user to delete
   * @param force if true, forces deletion even if the user is currently connected
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Deletes a user with the specified username.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("hasAuthority('" + UserEntity.ROLE_ADMIN + "')")
  @DeleteMapping(path = "/{name}")
  public void delete(
      @PathVariable("name") String name,
      @RequestParam(name = "force", defaultValue = "false") boolean force) {
    soht2UserService.deleteUser(name, force);
  }

  /**
   * Lists all users in the system. Only users with the admin role can view the list of users.
   *
   * @return a collection of {@link Soht2User} objects representing all users
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Lists all users in the system.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("hasAuthority('" + UserEntity.ROLE_ADMIN + "')")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public Collection<Soht2User> list() {
    return soht2UserService.listUsers();
  }

  /**
   * Retrieves the current user's information. This endpoint does not require any specific role.
   *
   * @return the {@link Soht2User} object representing the current user
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Retrieves the current user's information.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @GetMapping(path = "/self", produces = APPLICATION_JSON_VALUE)
  public Soht2User self(Authentication authentication) {
    return soht2UserService.getSelf(authentication);
  }

  /**
   * Changes the password of the current user. This endpoint does not require any specific role.
   *
   * @param oldPassword the current password of the user
   * @param newPassword the new password to set for the user
   * @return the updated {@link Soht2User} object
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "User Requests")
  @Operation(summary = "Changes the password of the current user.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PutMapping(path = "/self", produces = APPLICATION_JSON_VALUE)
  public Soht2User password(
      @RequestParam("old") String oldPassword,
      @RequestParam("new") String newPassword,
      Authentication authentication) {
    return soht2UserService.changePassword(oldPassword, newPassword, authentication);
  }
}
