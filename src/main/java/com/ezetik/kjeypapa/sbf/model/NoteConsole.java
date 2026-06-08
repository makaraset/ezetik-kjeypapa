package com.ezetik.kjeypapa.sbf.model;

import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteConsole {

	Note note;
	NoteAttachmentResponse attachment;
	NoteAttachmentResponse noteSigned;

}
