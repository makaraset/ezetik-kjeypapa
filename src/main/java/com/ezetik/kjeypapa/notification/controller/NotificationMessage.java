package com.ezetik.kjeypapa.notification.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessage {

	private String token;
	private String title;
	private String body;
	private String data;
	private String username;

}
