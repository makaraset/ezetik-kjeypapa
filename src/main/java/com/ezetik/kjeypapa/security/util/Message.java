package com.ezetik.kjeypapa.security.util;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Message<T> {
	private String type;
	private String message;
	private T data;

	public Message(String type, String message, T data) {
		super();
		this.type = type;
		this.message = message;
		this.data = data;
	}

}