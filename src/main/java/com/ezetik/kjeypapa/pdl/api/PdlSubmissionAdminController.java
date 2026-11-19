package com.ezetik.kjeypapa.pdl.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.payload.PdlSubmissionFiledRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlSubmissionNotFiledRequest;
import com.ezetik.kjeypapa.pdl.service.PdlSubmissionReconcileService;
import com.ezetik.kjeypapa.pdl.service.PdlSubmissionReconcileService.Unresolved;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Submissions that were sent to Sambat and never answered.
 *
 * <p>Sambat expose no lookup that would settle it, so an operator reads their
 * console and tells us which way it went. Until they do, the loan will not
 * resubmit — a duplicate credit enquiry against a real person is not a mistake
 * that can be walked back.
 */
@RestController
@RequestMapping("/api/v1/pdl/admin/submissions")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "15- PDL Submission Reconciliation", description = "Submissions sent but never answered")
public class PdlSubmissionAdminController {

	@Autowired
	private PdlSubmissionReconcileService service;

	/** Everything waiting on a human answer. */
	@GetMapping("/unresolved")
	public ResponseEntity<Message<List<Unresolved>>> unresolved() {
		List<Unresolved> rows = service.unresolved();
		return ResponseEntity.ok(new Message<>("SUCCESS",
				rows.isEmpty() ? "Nothing awaiting reconciliation"
						: rows.size() + " submission(s) awaiting reconciliation",
				rows));
	}

	/**
	 * Sambat's console shows the application. Records it as filed, taking their
	 * application id, and reconstructs the consent record that was lost when the
	 * failed call rolled back.
	 */
	@Operation(summary = "Record an unanswered submission as filed", description = """
			For a loan listed by `GET /unresolved`, when Sambat's console shows the application. The loan
			becomes Submitted and takes Sambat's LOS AppId.

			The outcome is in the answer's `type`:

			- `SUCCESS`: recorded as filed.
			- `NOT_FOUND`: no loan with this id is awaiting an answer.
			- `INVALID`: `losAppId` is missing or is not greater than 0. Nothing was changed.
			- `BAD_REQUEST`: the body could not be read. The message names the field.
			""")
	@PostMapping("/{loanId}/filed")
	public ResponseEntity<Message<String>> filed(@PathVariable("loanId") int loanId,
			@RequestBody PdlSubmissionFiledRequest body) {
		return service.markFiled(loanId, body.losAppId(), str(body.losApplicationNo()), str(body.note()));
	}

	/** Sambat's console shows nothing. Clears the block so the customer can submit. */
	@Operation(summary = "Record an unanswered submission as not filed", description = """
			For a loan listed by `GET /unresolved`, when Sambat's console does not show the application. The
			customer can then submit it again. The body may be left out.

			The outcome is in the answer's `type`:

			- `SUCCESS`: the loan can be submitted again.
			- `NOT_FOUND`: no loan with this id is awaiting an answer.
			- `BAD_REQUEST`: the body could not be read.
			""")
	@PostMapping("/{loanId}/not-filed")
	public ResponseEntity<Message<String>> notFiled(@PathVariable("loanId") int loanId,
			@RequestBody(required = false) PdlSubmissionNotFiledRequest body) {
		return service.markNotFiled(loanId, body == null ? "" : str(body.note()));
	}

	private static String str(String v) {
		return v == null ? "" : v;
	}
}
