package com.mamashop.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 對應 app.admin.*：預設管理員帳號，密碼一律從環境變數讀取。 */
@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password, String name) {
}
