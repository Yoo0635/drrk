package com.drrk.main.auth;

import com.drrk.domain.user.User;
import java.util.Optional;

public interface UserAccountRepository {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	Optional<User> findById(Long id);

	User save(User user);

	Long idOf(User user);
}
