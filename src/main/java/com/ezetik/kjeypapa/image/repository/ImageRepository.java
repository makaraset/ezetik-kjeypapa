package com.ezetik.kjeypapa.image.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public interface ImageRepository extends JpaRepository<Image, Integer> {

	Image findByFileName(String fileName);

	@Query(value = "select new com.ezetik.kjeypapa.image.payload.ImageResponse(im.uuid, im.fileName, im.fileType, im.size, im.entityClass, im.entityId) from com.ezetik.kjeypapa.image.model.Image im where im.status=:status and im.entityId=:entityId", nativeQuery = false)
	List<ImageResponse> findByEntityIdAndStatus(String entityId, boolean status);

	@Query(value = "select new com.ezetik.kjeypapa.image.payload.ImageResponse(im.uuid, im.fileName, im.fileType, im.size, im.entityClass, im.entityId) from com.ezetik.kjeypapa.image.model.Image im where im.status=:status and im.entityId=:entityId and im.entityClass LIKE %:entityClass%", nativeQuery = false)
	List<ImageResponse> findByEntityClassAndEntityIdAndStatus(String entityClass, String entityId, boolean status);

	Image findByUuid(String uuid);

	@Modifying
	@Query(value = "update com.ezetik.kjeypapa.image.model.Image i set i.status=false where (i.entityId = :entityId and i.entityClass=:entityClass) or i.fileName = :fileName", nativeQuery = false)
	void disactiveImageByEntityIdOrFileName(String entityId, String entityClass, String fileName);

	@Query(value = "select new com.ezetik.kjeypapa.image.payload.ImageResponse(im.uuid, im.fileName, im.fileType, im.size, im.entityClass, im.entityId) from com.ezetik.kjeypapa.image.model.Image im where im.status=true", nativeQuery = false)
	List<ImageResponse> findAllImageResponse();

}