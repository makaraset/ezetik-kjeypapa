package com.ezetik.kjeypapa.security.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.security.model.OneTimePassword;

@Repository
public interface OneTimePasswordRepository extends CrudRepository<OneTimePassword, Long> {

	Optional<OneTimePassword> findByOneTimePasswordCode(int otp);

}