package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.service.SbfCifResolver.Outcome;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Sambat's answers of 2026-11-05, one branch each: when a CIF is stored, and when nothing is. */
@ExtendWith(MockitoExtension.class)
class SbfCifResolverTest {

	@Mock SbfGatewayClient sbf;
	@Mock PdlPersonalInfoRepository personalRepo;
	@Mock UserRepository userRepository;
	@InjectMocks SbfCifResolver resolver;

	private User user;
	private PdlPersonalInfo dara;

	@BeforeEach
	void setUp() {
		user = new User();
		user.setId(42);
		dara = new PdlPersonalInfo();
		dara.setIdNo("012345678");
		dara.setDateOfBirth("03/01/1990");
		dara.setGender("M");
		dara.setKhmerFamilyName("សុខ");
		dara.setKhmerFirstName("តារា");
		dara.setLatinFamilyName("SOK");
		dara.setLatinFirstName("DARA");
	}

	private static ObjectNode theirRecord() {
		ObjectNode r = new ObjectMapper().createObjectNode();
		r.put("custKeyNum", 70);
		r.put("idNo", "012345678");
		r.put("dob", "1990-01-03");
		r.put("sex", "M");
		r.put("lname", "SOK");
		r.put("fname", "DARA");
		return r;
	}

	@Test
	@DisplayName("a stored CIF is final: Sambat are not asked again")
	void storedIsFinal() throws Exception {
		user.setRegistedId("70");

		SbfCifResolver.CifCheck c = resolver.ensure(user, dara);

		assertThat(c.outcome()).isEqualTo(Outcome.STORED);
		assertThat(c.cif()).isEqualTo(70L);
		verify(sbf, never()).customerByIdNo(anyString());
	}

	@Test
	@DisplayName("a full match is stored on the user")
	void matchIsStored() throws Exception {
		when(sbf.customerByIdNo("012345678")).thenReturn(theirRecord());

		SbfCifResolver.CifCheck c = resolver.ensure(user, dara);

		assertThat(c.outcome()).isEqualTo(Outcome.MATCHED);
		assertThat(user.getRegistedId()).isEqualTo("70");
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("no record means a new customer, and nothing is stored")
	void noRecordIsNew() throws Exception {
		when(sbf.customerByIdNo("012345678")).thenReturn(null);

		assertThat(resolver.ensure(user, dara).outcome()).isEqualTo(Outcome.NEW);
		assertThat(user.getRegistedId()).isNull();
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("a record under other details is never stored, but names the CIF and what differs")
	void mismatchIsNotStored() throws Exception {
		when(sbf.customerByIdNo("012345678")).thenReturn(theirRecord().put("dob", "1991-01-03"));

		SbfCifResolver.CifCheck c = resolver.ensure(user, dara);

		assertThat(c.outcome()).isEqualTo(Outcome.MISMATCH);
		assertThat(c.cif()).isEqualTo(70L);
		assertThat(c.differences()).containsExactly(SbfCustomerMatch.DATE_OF_BIRTH);
		assertThat(user.getRegistedId()).isNull();
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("an outage is not a new customer")
	void outageIsUnavailable() throws Exception {
		when(sbf.customerByIdNo(anyString())).thenThrow(new IOException("timed out"));

		assertThat(resolver.ensure(user, dara).outcome()).isEqualTo(Outcome.UNAVAILABLE);
		assertThat(user.getRegistedId()).isNull();
	}

	@Test
	@DisplayName("a record without a usable custKeyNum cannot be filed either way")
	void noCustKeyNum() throws Exception {
		ObjectNode rec = theirRecord();
		rec.remove("custKeyNum");
		when(sbf.customerByIdNo(anyString())).thenReturn(rec);

		assertThat(resolver.ensure(user, dara).outcome()).isEqualTo(Outcome.UNAVAILABLE);
	}

	@Test
	@DisplayName("a merchant code or staff username in registedId holds the account, without asking Sambat")
	void notACif() throws Exception {
		user.setRegistedId("M-0042");

		SbfCifResolver.CifCheck c = resolver.ensure(user, dara);

		assertThat(c.outcome()).isEqualTo(Outcome.MISMATCH);
		assertThat(c.differences()).containsExactly("registedId");
		verify(sbf, never()).customerByIdNo(anyString());
	}

	@Test
	@DisplayName("ensure(user) finds the personal record itself")
	void loadsThePersonalRecord() throws Exception {
		when(personalRepo.findByUser(42)).thenReturn(List.of(dara));
		when(sbf.customerByIdNo("012345678")).thenReturn(theirRecord());

		assertThat(resolver.ensure(user).outcome()).isEqualTo(Outcome.MATCHED);
	}

	@Test
	@DisplayName("the LPO's view looks Sambat up but stores nothing")
	void statusStoresNothing() throws Exception {
		when(sbf.customerByIdNo("012345678")).thenReturn(theirRecord());

		assertThat(resolver.status(user, dara).outcome()).isEqualTo(Outcome.MATCHED);
		assertThat(user.getRegistedId()).isNull();
		verify(userRepository, never()).save(any());
	}
}
