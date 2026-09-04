 package com.nexus.campus.config;

 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 import org.springframework.scheduling.annotation.AsyncConfigurer;
 import org.springframework.scheduling.annotation.EnableAsync;
 import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

 import java.util.concurrent.Executor;

 @Configuration
 @EnableAsync
 public class AsyncConfig implements AsyncConfigurer {

     @Override
     public Executor getAsyncExecutor() {
         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
         executor.setCorePoolSize(4);
         executor.setMaxPoolSize(10);
         executor.setQueueCapacity(100);
         executor.setThreadNamePrefix("nexus-async-");
         executor.initialize();
         return executor;
     }

     /**
      * Dedicated pool for LLM-bound agent listeners: long model calls can
      * occupy threads for tens of seconds, so they must not contend with
      * message/notification work on the default nexus-async pool. Small queue
      * + Abort policy fail fast; saturation degrades via the rejection
      * handling added at the publish sites.
      */
     @Bean("agentLlmExecutor")
     public Executor agentLlmExecutor() {
         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
         executor.setCorePoolSize(2);
         executor.setMaxPoolSize(4);
         executor.setQueueCapacity(50);
         executor.setThreadNamePrefix("agent-llm-");
         executor.initialize();
         return executor;
     }
 }
