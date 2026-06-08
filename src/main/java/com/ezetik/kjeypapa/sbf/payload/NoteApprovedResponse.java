package com.ezetik.kjeypapa.sbf.payload;

import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.model.Note;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteApprovedResponse {

	Note note;
	LoanFacility loanFacility;
}
