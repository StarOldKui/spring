package com.luban;


import org.springframework.context.annotation.ComponentScan;

//@ComponentScan("com.luban")
public class AppConfig {


//
//	@Bean
//	public DataSource dataSource() {
//		DriverManagerDataSource dataSource = new DriverManagerDataSource();
//		dataSource.setUrl("jdbc:mysql://192.168.99.100:33306/luban");
//		dataSource.setUsername("root");
//		dataSource.setPassword("123456");
//
//
////		try {
////			Connection connection = dataSource.getConnection();
////			PreparedStatement insert = connection.prepareStatement("insert");
////			insert.execute();
////			connection.commit();
////		} catch (SQLException e) {
////			e.printStackTrace();
////		}
//
//		return dataSource;
//	}
//
//	@Bean
//	public PlatformTransactionManager transactionManager() {
//		DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
//		transactionManager.setDataSource(dataSource());
//
//		return transactionManager;
//	}


//	@Bean
//	public MyAspect myAspect() {
//		return new MyAspect();
//	}


//	@Bean
//	public MyAdvisor myAdvisor() {
//		return new MyAdvisor();
//	}

//	@Bean
//	public ProxyFactoryBean foo() {
//		ProxyFactoryBean proxy = new ProxyFactoryBean();
//		proxy.setTarget(userService());
//		proxy.addAdvisor(myAdvisor());
//		return proxy;
//	}

//	@Bean
//	public BeanNameAutoProxyCreator foo(){
//		BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
//		beanNameAutoProxyCreator.setBeanNames("userService");
//		beanNameAutoProxyCreator.setInterceptorNames("myAdvisor");
////		beanNameAutoProxyCreator.setCustomTargetSourceCreators(new TargetSourceCreator() {
////			@Override
////			public TargetSource getTargetSource(Class<?> beanClass, String beanName) {
////
////			}
////		});
//		return beanNameAutoProxyCreator;
//	}


}
