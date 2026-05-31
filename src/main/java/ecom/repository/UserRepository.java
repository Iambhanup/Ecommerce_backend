package ecom.repository;

import ecom.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
	java.util.Optional<User> findByEmail(String email);
	java.util.Optional<User> findByEmailAndPhone(String email, String phone);
}
