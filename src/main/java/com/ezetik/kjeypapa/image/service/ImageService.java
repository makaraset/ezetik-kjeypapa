package com.ezetik.kjeypapa.image.service;

import java.util.List;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;

public interface ImageService {

	public Image save(Image image);

	public Image findByFileName(String fileName);

	public Image findByUuid(String uuid);

	public List<ImageResponse> findAllImageResponse();

	public void disactiveImageByEntityIdOrFileName(String entityId, String entityClass, String fileName);

	public List<ImageResponse> findByEntityIdAndStatus(String entityId, boolean status);

	public List<ImageResponse> findByEntityClassAndEntityIdAndStatus(String entityClass, String entityId,
			boolean status);

}
