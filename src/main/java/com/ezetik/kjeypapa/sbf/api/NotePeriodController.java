package com.ezetik.kjeypapa.sbf.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.sbf.model.NotePeriod;
import com.ezetik.kjeypapa.sbf.model.NotePeriodRate;
import com.ezetik.kjeypapa.sbf.repository.NotePeriodRateRepository;
import com.ezetik.kjeypapa.sbf.repository.NotePeriodRepository;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/note-period")
@Tag(name = "05- Note Period API", description = "Note period controller")
public class NotePeriodController {

	@Autowired
	NotePeriodRepository npRepo;

	@Autowired
	NotePeriodRateRepository nprRepo;

	@GetMapping("")
	public ResponseEntity<Message<List<NotePeriod>>> findAll() {

		ResponseEntity<Message<List<NotePeriod>>> resp = null;

		try {

			List<NotePeriod> merchants = npRepo.findAll();
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", merchants), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@GetMapping("/{id}")
	public ResponseEntity<Message<NotePeriod>> findByID(@PathVariable("id") int id) {

		ResponseEntity<Message<NotePeriod>> resp = null;

		try {

			NotePeriod np = npRepo.findById(id).orElse(null);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", np), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@PostMapping("")
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Message<NotePeriod>> save(@RequestBody NotePeriod np) {

		ResponseEntity<Message<NotePeriod>> resp = null;

		try {

			NotePeriod data = npRepo.save(np);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Data is saved", data), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	// =======Note Period Rate===========//

	@GetMapping("/rate")
	public ResponseEntity<Message<List<NotePeriodRate>>> findAllRate() {

		ResponseEntity<Message<List<NotePeriodRate>>> resp = null;

		try {

			Sort sort = Sort.by(Sort.Direction.ASC, "id");

			List<NotePeriodRate> merchants = nprRepo.findAll(sort);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", merchants), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@GetMapping("/rate/periodId={periodId}")
	public ResponseEntity<Message<List<NotePeriodRate>>> findRateByPeriodId(@PathVariable("periodId") int periodId) {

		ResponseEntity<Message<List<NotePeriodRate>>> resp = null;

		try {

			List<NotePeriodRate> merchants = nprRepo.findByNotePeriodIdOrderByIdAsc(periodId);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", merchants), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@GetMapping("/rate/{id}")
	public ResponseEntity<Message<NotePeriodRate>> findRateByID(@PathVariable("id") int id) {

		ResponseEntity<Message<NotePeriodRate>> resp = null;

		try {

			NotePeriodRate npr = nprRepo.findById(id).orElse(null);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", npr), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@PostMapping("/rate")
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Message<NotePeriodRate>> saveRate(@RequestBody NotePeriodRate npr) {

		ResponseEntity<Message<NotePeriodRate>> resp = null;

		try {

			NotePeriodRate data = nprRepo.save(npr);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Data is saved", data), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

}
