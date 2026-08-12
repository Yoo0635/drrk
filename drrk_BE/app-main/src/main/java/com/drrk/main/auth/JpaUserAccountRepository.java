package com.drrk.main.auth;

import com.drrk.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserAccountRepository extends JpaRepository<User, Long>, UserAccountRepository {

	@Override
	boolean existsByEmail(String email);

	@Override
	Optional<User> findByEmail(String email);

	@Override
	default Long idOf(User user) {
		return user.getId();
	}
}
