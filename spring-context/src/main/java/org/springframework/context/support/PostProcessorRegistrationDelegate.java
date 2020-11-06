/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 如果有BeanDefinitionRegistryPostProcessors，则执行
		Set<String> processedBeans = new HashSet<>();

		// beanFactory是DefaultListableBeanFactory，是BeanDefinitionRegistry的实现类，所以肯定满足if
		// 100%进吧，beanFactory自己实例化出来的
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			// regularPostProcessors用来存放BeanFactoryPostProcessor
			// 在一般情况下，regularPostProcessors是不会有数据的，只有在外面手动添加BeanFactoryPostProcessor，才会有数据
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// registryProcessors 用来存放BeanDefinitionRegistryPostProcessor
			// BeanDefinitionRegistryPostProcessor继承了BeanFactoryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 这个beanFactoryPostProcessors集合一般情况下都是空的，除非我们手动调用容器的addBeanFactoryPostProcessor方法
			// 循环传进来的beanFactoryPostProcessors，正常情况下，beanFactoryPostProcessors肯定没有数据
			// 因为beanFactoryPostProcessors是获得手动添加的，而不是spring扫描的
			// 只有手动调用annotationConfigApplicationContext.addBeanFactoryPostProcessor(XXX)才会有数据
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// BeanDefinitionRegistryPostProcessor是一个特殊的BeanFactoryPostProcessor，需要先执行postProcessBeanDefinitionRegistry方法
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;

					registryProcessor.postProcessBeanDefinitionRegistry(registry);

					registryProcessors.add(registryProcessor);
				}
				else {
					// 不是的话，就装到regularPostProcessors
					regularPostProcessors.add(postProcessor);
				}
			}

			// 保存当前需要执行的实现了BeanDefinitionRegistryPostProcessor接口的后置处理器
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// =========================================================================================================
			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 首先，先执行实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法
			// 获得实现BeanDefinitionRegistryPostProcessor接口的类的BeanName:org.springframework.context.annotation.internalConfigurationAnnotationProcessor
			// 并且装入数组postProcessorNames，我理解一般情况下，只会找到一个
			// 这里又有一个坑，为什么我自己创建了一个实现BeanDefinitionRegistryPostProcessor接口的类，也打上了@Component注解
			// 配置类也加上了@Component注解，但是这里却没有拿到
			// 因为直到这一步，Spring还没有去扫描，扫描是在ConfigurationClassPostProcessor类中完成的，也就是下面的第一个
			// invokeBeanDefinitionRegistryPostProcessors方法
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 判断这个类是否还实现了PriorityOrdered接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 这里调用了getBean，所以生成一个BeanDefinitionRegistryPostProcessor的bean对象
					// 获得ConfigurationClassPostProcessor类，并且放到currentRegistryProcessors
					// ConfigurationClassPostProcessor是很重要的一个类，它实现了BeanDefinitionRegistryPostProcessor接口
					// 里面执行了扫描Bean，Import，ImportResouce等各种操作
					// 用来处理配置类（有两种情况 一种是传统意义上的配置类，一种是普通的bean）的各种逻辑
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 把name放到processedBeans，后续会根据这个集合来判断处理器是否已经被执行过了
					processedBeans.add(ppName);
				}
			}
			// 处理排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);

			// 合并Processors，为什么要合并，因为registryProcessors是装载BeanDefinitionRegistryPostProcessor的
			// 一开始的时候，spring只会执行BeanDefinitionRegistryPostProcessor独有的方法
			// 而不会执行BeanDefinitionRegistryPostProcessor父类的方法，即BeanFactoryProcessor的方法
			// 所以这里需要把处理器放入一个集合中，后续统一执行父类的方法
			registryProcessors.addAll(currentRegistryProcessors);

			// 执行postProcessBeanDefinitionRegistry方法
			// Spring热插播的体现，像ConfigurationClassPostProcessor就相当于一个组件，Spring很多事情就是交给组件去管理
			// 如果不想用这个组件，直接把注册组件的那一步去掉就可以
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);

			// 因为currentRegistryProcessors是一个临时变量，所以需要清除
			currentRegistryProcessors.clear();

			// =========================================================================================================
			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 首先，先执行实现了Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法
			// 再次根据BeanDefinitionRegistryPostProcessor获得BeanName，看这个BeanName是否已经被执行过了，有没有实现Ordered接口
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 这里才可以获得我们定义的实现了BeanDefinitionRegistryPostProcessor的Bean
				// 因为上面执行了一次invokeBeanDefinitionRegistryPostProcessors，所以自己写的能被扫描上
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 上面的代码是执行了实现了Ordered接口的BeanDefinitionRegistryPostProcessor
			// 下面的代码就是执行没有实现Ordered接口的BeanDefinitionRegistryPostProcessor
			boolean reiterate = true;
			// 在一个BeanDefinitionRegistryPostProcessor中可以注册另一个BeanDefinitionRegistryPostProcessor，所以需要递归找出所有的BeanDefinitionRegistryPostProcessor
			// 一个没有实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor如果在内部注册了一个实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor，那么就是没有实现PriorityOrdered接口的先执行
			while (reiterate) {
				reiterate = false;
				// 这里会再一次拿到实现了PriorityOrdered接口或Ordered接口的BeanDefinitionRegistryPostProcessor，所以需要processedBeans进行过滤
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 执行完BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法后，
			// 再执行BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 执行手动添加的非BeanDefinitionRegistryPostProcessor类型的Bean工厂后置处理器的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			// 如果beanFactory没有可以注册BeanDefinition的功能，则没有BeanDefinitionRegistryPostProcessor，则执行Bean工厂后置处理器的postProcessBeanFactory方法
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 总结一下上面的流程：
		// 1. 如果beanFactory实现了BeanDefinitionRegistry接口，则表示可以通过BeanDefinitionRegistryPostProcessor接口来注册BeanDefinition
		// 2. 因为现在是Spring启动过程中的比较早的阶段（还没有开始扫描@Component），所以只能获取Spring默认添加到BeanFactory中的bean工厂后置处理器，以及程序员手动添加的bean工厂后置处理器
		// 3. 执行的顺序是，先执行BeanDefinitionRegistryPostProcessor中的postProcessBeanDefinitionRegistry方法，因为这个方法可以注册BeanDefinition
		// 4. 先执行手动添加进行来的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法,这个过程中可能会向BeanFactory中注册其他的BeanDefinitionRegistryPostProcessor
		// 5. 从BeanFactory中获取PriorityOrdered接口的BeanDefinitionRegistryPostProcessor，并执行postProcessBeanDefinitionRegistry方法
		// 6. 从BeanFactory中获取Ordered接口的BeanDefinitionRegistryPostProcessor，并执行postProcessBeanDefinitionRegistry方法
		// 7. 在5,6步中都有可能注册新的BeanDefinitionRegistryPostProcessor的
		// 8. 从BeanFactory中获取普通的BeanDefinitionRegistryPostProcessor，并执行postProcessBeanDefinitionRegistry方法
		// 9. 在8步中也有可能注册新的BeanDefinitionRegistryPostProcessor，所以第8步会递归，直到没有新的BeanDefinitionRegistryPostProcessor注册进来了
		// 10. 在前面的步骤中都是执行BeanDefinitionRegistryPostProcessor中的postProcessBeanDefinitionRegistry方法，进行BeanDefinition的注册
		// 11. BeanDefinition注册完了之后，因为BeanDefinitionRegistryPostProcessor本身也是一个BeanFactoryProcessor，所以最后再执行postProcessBeanFactory方法
		// 12. 总结一句话就是：先通过BeanDefinitionRegistryPostProcessor注册BeanDefinition，再执行BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
		// 在默认情况下，上面的步骤中只有一个BeanDefinitionRegistryPostProcessor会执行，就是ConfigurationClassPostProcessor，因为它是Spring默认在添加进去的

		// BeanDefinitionRegistryPostProcessor是特殊的BeanFactoryPostProcessor，在上面的逻辑中都处理完了
		// 所以接下来就是处理普通的BeanFactoryPostProcessor
		// 找到BeanFactoryPostProcessor实现类的BeanName数组
		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);
		// 默认情况下会拿到两个，一个就是ConfigurationClassPostProcessor，一个就是EventListenerMethodProcessor

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 保存直接实现了BeanFactoryPostProcessor接口和PriorityOrdered接口的后置处理器
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 保存直接实现了BeanFactoryPostProcessor接口和Ordered接口的后置处理器
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 保存直接实现了BeanFactoryPostProcessor接口的后置处理器，不包括那些实现了排序接口的类
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 把所有BeanFactoryPostProcessor进行分类
		for (String ppName : postProcessorNames) {
			// 拿到的BeanFactoryPostProcessor包括了BeanDefinitionRegistryPostProcessor，所以要跳过
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 先执行实现了PriorityOrdered接口的BeanFactoryPostProcessor
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 再执行实现了Ordered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后执行普通的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// 到此，所有的BeanFactoryPostProcessor都执行完了
		// 对于BeanFactoryPostProcessor我们可以这么理解：它是去完善BeanFactory的，比如向BeanFactory中去注册BeanDefinition
		// 就好比：BeanFactory是一张白纸，每个BeanFactoryPostProcessor都去这张白纸上去画上一笔，这个白纸就丰满了

		// 所以这段代码，虽然内容很多，但是在默认情况下，主要就是执行了ConfigurationClassPostProcessor
		// 1. 先执行了ConfigurationClassPostProcessor的postProcessBeanDefinitionRegistry方法，基本上就是去注册BeanDefinition
		// 2. 然后执行了ConfigurationClassPostProcessor的postProcessBeanFactory方法

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// 获取BeanPostProcessor类型的bean
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 把priorityOrderedPostProcessors添加到beanFactory中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 把orderedPostProcessors添加到beanFactory中
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {

				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
