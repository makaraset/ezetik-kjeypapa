package com.ezetik.kjeypapa.notification.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationModel, Integer> {

	@Query("select n from NotificationModel n where n.user.username=:username and n.isDeleted=false order by id desc")
	List<NotificationModel> findByUsername(String username);

}
