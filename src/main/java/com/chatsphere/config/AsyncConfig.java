package com.chatsphere.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
     * Pool cho việc tạo thông báo và gửi Web Push (Phase 5 mục 5.2).
     *
     * <p>Tách khỏi {@code mailExecutor} vì hai lý do: khối lượng khác hẳn (mỗi tin nhắn nhóm
     * sinh ra hàng chục tác vụ, còn mail chỉ vài cái mỗi ngày), và một SMTP treo 5 giây không
     * được phép làm chậm thông báo của cả hệ thống.
     *
     * <p>Hàng đợi lớn (1000) nhưng KHÔNG dùng CallerRunsPolicy như mail: nếu đầy, chạy ngay
     * trên luồng gọi sẽ kéo ngược độ trễ vào đúng luồng gửi tin nhắn — thứ mà cả thiết kế này
     * sinh ra để tránh. Đầy thì thà mất thông báo (đã có bản ghi DB, người dùng vẫn thấy khi
     * mở app) còn hơn làm chậm việc gửi tin.
     */
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }

    /**
     * Bộ hẹn giờ cho debounce offline của module presence (Phase 4.2): mỗi lần phiên WebSocket
     * cuối cùng của một người đóng lại, ta hẹn kiểm tra lại sau ~10 giây thay vì báo offline ngay.
     *
     * <p>Pool RIÊNG, không dùng chung {@code mailExecutor}: SMTP treo 5 giây một lần sẽ làm các
     * tác vụ presence xếp hàng phía sau và báo offline trễ hẳn — hai loại công việc có đặc tính
     * thời gian hoàn toàn khác nhau.
     *
     * <p>Pool nhỏ là đủ: mỗi tác vụ chỉ là một lệnh đọc Redis rồi (hiếm khi) gửi vài frame.
     */
    @Bean("presenceScheduler")
    public ThreadPoolTaskScheduler presenceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("presence-");
        // Tắt máy thì bỏ luôn các tác vụ debounce đang chờ: cả tiến trình sắp dừng, mọi phiên
        // WebSocket sẽ đứt hết — thông báo offline lẻ tẻ lúc này vừa vô nghĩa vừa dễ lỗi.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
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