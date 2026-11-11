package com.mamashop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 排除 UserDetailsServiceAutoConfiguration：登入由 AuthService 自己處理，
// 不需要 Spring Boot 預設建立的記憶體帳號（否則啟動時會印出一組隨機密碼）
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MamashopApplication {

	public static void main(String[] args) {
		SpringApplication.run(MamashopApplication.class, args);
	}

}
