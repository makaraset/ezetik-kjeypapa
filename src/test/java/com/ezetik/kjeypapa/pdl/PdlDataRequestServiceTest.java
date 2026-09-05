package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ezetik.kjeypapa.pdl.model.PdlDataRequest;
import com.ezetik.kjeypapa.pdl.model.PdlDataRequestStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlDataRequestTypeEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlDataDecisionPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlDataRequestPayload;
import com.ezetik.kjeypapa.pdl.repository.PdlDataRequestRepository;
import com.ezetik.kjeypapa.pdl.service.PdlDataRequestService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlDataRequestServiceTest {

	@Mock PdlDataRequestRepository repo;
	@Mock UserService userService;
	@InjectMocks PdlDataRequestService service;

	private User customer;

	@BeforeEach
	void setUp() {
		customer = new User();
		customer.setId(19);
		customer.setUsername("010849001");
		when(userService.findUserByUsername("010849001")).thenReturn(customer);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("010849001", "x", List.of()));
		when(repo.save(any(PdlDataRequest.class))).thenAnswer(i -> i.getArgument(0));
		when(repo.findByUser_IdAndStatusIn(any(), any())).thenReturn(List.of());
	}

	private PdlDataRequestPayload payload(String type) {
		PdlDataRequestPayload p = new PdlDataRequestPayload();
		p.setType(type);
		p.setDetails("please");
		return p;
	}

	@Test
	@DisplayName("a request is raised for the caller, PENDING, with no decision on it")
	void createsPendingRequestForCaller() {
		var body = service.create(payload("DELETION")).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		PdlDataRequest r = body.getData();
		assertThat(r.getUser()).isSameAs(customer);
		assertThat(r.getType()).isEqualTo(PdlDataRequestTypeEnum.DELETION);
		assertThat(r.getStatus()).isEqualTo(PdlDataRequestStatusEnum.PENDING);
		assertThat(r.getDecidedBy()).isNull();
		assertThat(r.getDecidedDate()).isNull();
	}

	@Test
	@DisplayName("an unknown type is refused rather than stored as null")
	void unknownTypeIsRefused() {
		// A null type would reach a NOT NULL column and fail as a 500, telling
		// the customer nothing about what they got wrong.
		var body = service.create(payload("EXPORT_EVERYTHING")).getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		verify(repo, never()).save(any());
	}

	@Test
	@DisplayName("a second open request of the same type is refused")
	void duplicateOpenRequestIsRefused() {
		// Two open deletion requests are one obligation. Letting them pile up
		// means the queue length stops matching the number of people waiting.
		PdlDataRequest existing = new PdlDataRequest();
		existing.setType(PdlDataRequestTypeEnum.DELETION);
		existing.setStatus(PdlDataRequestStatusEnum.PENDING);
		when(repo.findByUser_IdAndStatusIn(any(), any())).thenReturn(List.of(existing));

		assertThat(service.create(payload("DELETION")).getBody().getType()).isEqualTo("DUPLICATE");
		// but a different right is a different obligation
		assertThat(service.create(payload("ACCESS")).getBody().getType()).isEqualTo("SUCCESS");
	}

	@Test
	@DisplayName("the customer only ever sees their own requests")
	void mineIsScopedToTheCaller() {
		service.mine();

		verify(repo).findByUser_IdOrderByIdDesc(19);
	}

	@Test
	@DisplayName("an answered request cannot be answered again")
	void answeredRequestsAreOneShot() {
		// Otherwise a completed request can be quietly reopened and answered
		// differently, and the record no longer shows what the customer was told.
		PdlDataRequest done = new PdlDataRequest();
		done.setId(4);
		done.setStatus(PdlDataRequestStatusEnum.COMPLETED);
		done.setDecidedBy("first-operator");
		when(repo.findById(4)).thenReturn(java.util.Optional.of(done));

		PdlDataDecisionPayload d = new PdlDataDecisionPayload();
		d.setStatus("REJECTED");
		d.setReason("changed my mind");

		var body = service.decide(4, d, "second-operator").getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(done.getDecidedBy()).isEqualTo("first-operator");
		verify(repo, never()).save(any());
	}

	@Test
	@DisplayName("picking a request up and then finishing it is allowed")
	void inProgressIsNotTerminal() {
		PdlDataRequest open = new PdlDataRequest();
		open.setId(5);
		open.setStatus(PdlDataRequestStatusEnum.IN_PROGRESS);
		when(repo.findById(5)).thenReturn(java.util.Optional.of(open));

		PdlDataDecisionPayload d = new PdlDataDecisionPayload();
		d.setStatus("COMPLETED");
		d.setReason("record sent by email");

		var body = service.decide(5, d, "operator").getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(open.getStatus()).isEqualTo(PdlDataRequestStatusEnum.COMPLETED);
		assertThat(open.getDecidedBy()).isEqualTo("operator");
		assertThat(open.getDecidedDate()).isNotNull();
	}

	@Test
	@DisplayName("a rejection must say why")
	void rejectionRequiresAReason() {
		// The one case the customer is most entitled to an explanation for.
		PdlDataRequest open = new PdlDataRequest();
		open.setId(6);
		open.setStatus(PdlDataRequestStatusEnum.PENDING);
		when(repo.findById(6)).thenReturn(java.util.Optional.of(open));

		PdlDataDecisionPayload d = new PdlDataDecisionPayload();
		d.setStatus("REJECTED");

		var body = service.decide(6, d, "operator").getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(open.getStatus()).isEqualTo(PdlDataRequestStatusEnum.PENDING);
	}

	@Test
	@DisplayName("a decision cannot set the status back to PENDING")
	void cannotDecideBackToPending() {
		// "Un-acknowledging" a request would reset the clock on a response
		// obligation that has already started running.
		PdlDataRequest open = new PdlDataRequest();
		open.setId(7);
		open.setStatus(PdlDataRequestStatusEnum.IN_PROGRESS);
		when(repo.findById(7)).thenReturn(java.util.Optional.of(open));

		PdlDataDecisionPayload d = new PdlDataDecisionPayload();
		d.setStatus("PENDING");

		assertThat(service.decide(7, d, "operator").getBody().getType()).isEqualTo("INVALID");
		assertThat(open.getStatus()).isEqualTo(PdlDataRequestStatusEnum.IN_PROGRESS);
	}
}
