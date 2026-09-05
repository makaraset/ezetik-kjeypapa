package com.ezetik.kjeypapa.pdl.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the Sambat dictionary mirror fresh.
 *
 * <p>Off by default: it cannot do anything useful until Sambat's credentials
 * work, and a job that fails every night is noise. Enable with
 * {@code pdl.dictionary.refresh.enabled=true}.
 *
 * <p>There is deliberately no startup fetch. An eager boot-time call would take
 * the whole application down whenever Sambat is unreachable — which is the
 * state right now — and would break the context-loads test.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "pdl.dictionary.refresh.enabled", havingValue = "true", matchIfMissing = false)
public class PdlDictionaryScheduler {

	@Autowired
	private PdlDictionaryService dictionary;

	@Scheduled(cron = "${pdl.dictionary.refresh-cron:0 30 3 * * *}", zone = "${pdl.dictionary.timezone:Asia/Phnom_Penh}")
	public void refresh() {
		try {
			log.info("PDL dictionary refresh: {}", dictionary.refresh());
		} catch (Exception e) {
			// Never rethrow: the previous snapshot is still serving customers,
			// and a failed refresh must not stop the next one from running.
			log.warn("PDL dictionary refresh failed, keeping the previous snapshot: {}", e.toString());
		}
	}
}
