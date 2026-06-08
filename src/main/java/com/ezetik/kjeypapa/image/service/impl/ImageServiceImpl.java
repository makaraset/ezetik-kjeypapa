package com.ezetik.kjeypapa.image.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;
import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.image.service.ImageService;

@Service
public class ImageServiceImpl implements ImageService {

	@Autowired
	private ImageRepository imageRepository;

	@Override
	public Image save(Image image) throws NullPointerException {
		if (image == null)
			throw new NullPointerException("Image Data NULL");
		return imageRepository.save(image);
	}

	@Override
	public Image findByFileName(String fileName) {
		return this.imageRepository.findByFileName(fileName);
	}

	@Override
	public Image findByUuid(String uuid) {
		return this.imageRepository.findByUuid(uuid);
	}

	@Override
	public List<ImageResponse> findAllImageResponse() {
		return this.imageRepository.findAllImageResponse();
	}

	@Override
	public void disactiveImageByEntityIdOrFileName(String entityId, String entityClass, String fileName) {
		this.imageRepository.disactiveImageByEntityIdOrFileName(entityId, entityClass, fileName);

	}

	@Override
	public List<ImageResponse> findByEntityIdAndStatus(String entityId, boolean status) {

		return this.imageRepository.findByEntityIdAndStatus(entityId, status);
	}

	@Override
	public List<ImageResponse> findByEntityClassAndEntityIdAndStatus(String entityClass, String entityId,
			boolean status) {

		return this.imageRepository.findByEntityClassAndEntityIdAndStatus(entityClass, entityId, status);
	}

}
