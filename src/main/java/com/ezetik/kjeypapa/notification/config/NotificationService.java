package com.ezetik.kjeypapa.notification.config;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.security.util.Message;
import com.google.firebase.messaging.FirebaseMessagingException;

public interface NotificationService {

	ResponseEntity<Message<List<NotificationModel>>> findMyNotification();

	ResponseEntity<Message<NotificationModel>> findById(int noteId);

	ResponseEntity<Message<String>> readMessage(int id);

	ResponseEntity<Message<String>> deleteMessage(int id);

	public ResponseEntity<String> postToClient(NotificationMessage message) throws FirebaseMessagingException;

}
