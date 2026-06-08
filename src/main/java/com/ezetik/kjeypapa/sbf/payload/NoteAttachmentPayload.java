package com.ezetik.kjeypapa.sbf.payload;

import java.util.List;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;

import lombok.Data;

@Data
public class NoteAttachmentPayload {

	private int id;
	private int noteId;
	private DocumentTypeEnum docType;
	private List<Image> attachFiles;

}
