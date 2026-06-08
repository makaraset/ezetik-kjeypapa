package com.ezetik.kjeypapa.security.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ezetik.kjeypapa.security.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {

	User findByEmail(String email);

	User findByUsername(String username);

	Optional<User> findByRegistedId(String registeredId);

	@Modifying
	@Query("UPDATE User u set u.enabled =:enabled WHERE u.id=:userId")
	void changeStatus(Long userId, boolean enabled);

	// @Query("SELECT COUNT(u) FROM User u WHERE u.username=:username ")
	int countByUsername(String username);

	long deleteByUsername(String username);

	@Modifying
	@Query("UPDATE User u set u.profilePhoto =:fileName WHERE u.username=:userName")
	int updateProfilePhoto(String fileName, String userName);

	@Modifying
	@Query("UPDATE User u set u.fcmToken =:token WHERE u.username=:userName")
	int setFCMToken(String token, String userName);

}
