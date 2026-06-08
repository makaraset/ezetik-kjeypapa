package com.ezetik.kjeypapa.notification.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.notification.config.NotificationModel;
import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;

@RestController
@RequestMapping("/api/v1/notification")
@Tag(name = "09- Notification Endpoint", description = "Push notification controller")
@Builder
@PreAuthorize("hasAnyRole('CUSTOMER','MERCHANT')")
public class FirebasePublisherController {

	private final FirebaseMessaging fcm;
	private final NotificationService service;

	@GetMapping("/mine")
	public ResponseEntity<com.ezetik.kjeypapa.security.util.Message<List<NotificationModel>>> getMyNotification() {
		return service.findMyNotification();
	}

	@PostMapping("/read/{id}")
	public ResponseEntity<com.ezetik.kjeypapa.security.util.Message<String>> readMessage(@PathVariable("id") int id) {
		return service.readMessage(id);
	}

	@PostMapping("/delete/{id}")
	public ResponseEntity<com.ezetik.kjeypapa.security.util.Message<String>> deleteMessage(@PathVariable("id") int id) {
		return service.deleteMessage(id);
	}

	@PostMapping("/topics/{topic}")
	public ResponseEntity<String> postToTopic(@RequestBody String message, @PathVariable("topic") String topic)
			throws FirebaseMessagingException {

		Message msg = Message.builder().setTopic(topic).putData("body", message).build();

		String id = fcm.send(msg);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(id);
	}

	@PostMapping("/condition")
	public ResponseEntity<String> postToCondition(@RequestBody ConditionMessageRepresentation message)
			throws FirebaseMessagingException {

		Message msg = Message.builder().setCondition(message.getCondition()).putData("body", message.getData()).build();

		String id = fcm.send(msg);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(id);
	}

	@PostMapping("/clients/{registrationToken}")
	public ResponseEntity<String> postToClient(@RequestBody String message,
			@PathVariable("registrationToken") String registrationToken) throws FirebaseMessagingException {

		Message msg = Message.builder().setToken(registrationToken).putData("body", message).build();

		String id = fcm.send(msg);
		return ResponseEntity.status(HttpStatus.OK).body(id);
	}

	@PostMapping("/send")
	public ResponseEntity<String> sendToClient(@RequestBody NotificationMessage message)
			throws FirebaseMessagingException {

		return service.postToClient(message);
	}

	@PostMapping("/clients")
	public ResponseEntity<List<String>> postToClients(@RequestBody MulticastMessageRepresentation message)
			throws FirebaseMessagingException {

		MulticastMessage msg = MulticastMessage.builder().addAllTokens(message.getRegistrationTokens())
				.putData("body", message.getData()).build();

		BatchResponse response = fcm.sendMulticast(msg);

		List<String> ids = response.getResponses().stream().map(r -> r.getMessageId()).collect(Collectors.toList());

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ids);
	}

	@PostMapping("/subscriptions/{topic}")
	public ResponseEntity<Void> createSubscription(@PathVariable("topic") String topic,
			@RequestBody List<String> registrationTokens) throws FirebaseMessagingException {
		fcm.subscribeToTopic(registrationTokens, topic);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/subscriptions/{topic}/{registrationToken}")
	public ResponseEntity<Void> deleteSubscription(@PathVariable String topic, @PathVariable String registrationToken)
			throws FirebaseMessagingException {
		fcm.subscribeToTopic(Arrays.asList(registrationToken), topic);
		return ResponseEntity.ok().build();
	}

}