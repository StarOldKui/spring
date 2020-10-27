package com.luban;

import com.luban.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
//		AnnotationConfigApplicationContext applicationContext1 = new AnnotationConfigApplicationContext(AppConfig.class);

		UserService userService = applicationContext.getBean("userService", UserService.class);
//		UserService userService1 = applicationContext1.getBean("userService", UserService.class);

		System.out.println(userService);
//		System.out.println(userService1);

	}
}
