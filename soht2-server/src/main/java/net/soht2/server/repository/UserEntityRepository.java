/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.soht2.server.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEntityRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByNameIgnoreCase(String name);

  List<UserEntity> findAllByNameIgnoreCaseIn(Collection<String> names);

  boolean existsByNameIgnoreCase(String name);
}
