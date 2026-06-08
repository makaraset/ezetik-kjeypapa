package com.ezetik.kjeypapa.image.payload;

import com.ezetik.kjeypapa.image.model.Image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageResponse {
	private String uuid;
	private String fileName;
	private String fileType;
	private long size;
	private String entityClass;
	private String entityId;

	public ImageResponse(Image image) {
		setUuid(image.getUuid());
		setFileName(image.getFileName());
		setFileType(image.getFileType());
		setSize(image.getSize());
		setEntityClass(image.getEntityClass());
		setEntityId(image.getEntityId());
	}

}