package com.ezetik.kjeypapa.notification.config;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Notification;

import lombok.Builder;

@Service
@Builder
public class NotificationServiceImpl implements NotificationService {

	private final NotificationRepository repo;

	private final FirebaseMessaging fcm;

	private final UserService userService;

	@Override
	public ResponseEntity<Message<List<NotificationModel>>> findMyNotification() {

		ResponseEntity<Message<List<NotificationModel>>> resp = null;

		try {

			String myUsername = SecurityContextHolder.getContext().getAuthentication().getName();

			List<NotificationModel> notifications = repo.findByUsername(myUsername);
			if (!notifications.isEmpty()) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully", notifications),
						HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null), HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<NotificationModel>> findById(int id) {

		ResponseEntity<Message<NotificationModel>> resp = null;

		try {
			Optional<NotificationModel> notification = repo.findById(id);

			if (notification.isPresent()) {

				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully", notification.get()),
						HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null), HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<String>> readMessage(int id) {
		ResponseEntity<Message<String>> resp = null;

		try {
			NotificationModel notification = repo.findById(id).orElse(null);

			if (notification != null) {

				notification.setRead(true);
				repo.save(notification);

				resp = new ResponseEntity<>(
						new Message<>("SUCCESS", notification.getId() + "-Is read.", notification.getId().toString()),
						HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<String>> deleteMessage(int id) {
		ResponseEntity<Message<String>> resp = null;

		try {
			NotificationModel notification = repo.findById(id).orElse(null);

			if (notification != null) {

				notification.setDeleted(true);
				repo.save(notification);

				resp = new ResponseEntity<>(new Message<>("SUCCESS", notification.getId() + "-Is deleted.",
						notification.getId().toString()), HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	// @formatter:off
	@Override
	public ResponseEntity<String> postToClient(NotificationMessage message) 
					throws FirebaseMessagingException {

		Notification notification = 
				Notification.builder()
				.setTitle(message.getTitle())
				.setBody(message.getBody())
				.build();
		
		AndroidConfig android = 
				AndroidConfig
				.builder()
				.setNotification(
						AndroidNotification
						.builder()
						.setChannelId("high_importance_channel")
						.build()
						)
				.build();
		
		com.google.firebase.messaging.Message msg = 
				com.google.firebase.messaging.Message.builder()
				.setToken(message.getToken())
				.setNotification(notification)
				.putData("data", message.getData())
				.setAndroidConfig(android)
				.build();
		String id = fcm.send(msg);
		
		NotificationModel nd = new NotificationModel();
		nd.setTitle(message.getTitle());
		nd.setBody(message.getBody());
		nd.setData(message.getData());
		nd.setRead(false);
		nd.setDeleted(false);
		nd.setUser(userService.findUserByUsername(message.getUsername()));
		repo.save(nd);
		
		return ResponseEntity.status(HttpStatus.OK).body(id);
	}
	// @formatter:on

}
