package com.chatsphere.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Pool riêng cho mail, KHÔNG dùng chung executor mặc định của Spring: SMTP chậm và hay treo,
     * để nó chiếm chung pool sẽ kéo theo mọi tác vụ nền khác (Phase sau còn push notification, media).
     */
    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mail-");
        // Hàng đợi đầy → chạy ngay trên thread gọi thay vì vứt bỏ task. Mail bị chậm còn hơn mất.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20); // shutdown vẫn gửi nốt mail đang trong hàng đợi
        executor.initialize();
        return executor;
    }

    /**
     * Method @Async trả void thì exception không có ai nhận (không có Future để .get()).
     * Không khai báo handler này thì lỗi biến mất im lặng.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}