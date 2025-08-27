package com.lei.usercenter.once;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import cn.hutool.core.date.StopWatch;
import com.lei.usercenter.mapper.UserMapper;
import com.lei.usercenter.model.domain.User;
import com.lei.usercenter.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class InsertUser {

    @Resource
    private UserService userService;

    //线程设置
    private ExecutorService executorService = new ThreadPoolExecutor(16, 1000, 10000, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10000));
    /**
     * 循环插入用户  耗时：7260ms
     * 批量插入用户   1000  耗时： 4751ms
     */
    /**
     * 批量插入用户
     */

    //  第一次延迟5秒后执行，之后按fixedRate的规则每10秒执行一次
    //@Scheduled(initialDelay = 5000, fixedRate = Long.MAX_VALUE)
    public void doInsertUser() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final int INSERT_SIZE = 1000;
        List<User> userList = new ArrayList<>();
        for (int i = 0; i < INSERT_SIZE; i++) {
            User user = new User();
            user.setUsername("假用户");
            user.setUserAccount("jiayonghu" + System.currentTimeMillis());
            user.setProfile("fake user");
            user.setAvatarUrl("https://iknow-pic.cdn.bcebos.com/a9d3fd1f4134970af7a1f9b187cad1c8a6865d5a");
            user.setGender(i % 2 == 0 ? 0 : 1);
            user.setUserPassword("58f98006537194ebd5ec1ceaf6cde45e");
            user.setPhone("15111112222");
            user.setEmail("jiayonghu" + i + "@qq.com");
            user.setUserStatus(0);
            user.setUserRole(0);
            user.setPlanetCode("10000" + i);
            String[] tags = {"running", "女", "大一", "学生", "美食", "python", "java", "电影", "C++", "音乐", "旅行", "乐观", "开心", "阅读", "跳舞", "游戏", "健身"};
            List<String> selectedTags = new ArrayList<>();
            int tagCount = (int) (Math.random() * 6) + 1;
            for (int j = 0; j < tagCount; j++) {
                selectedTags.add(tags[(int) (Math.random() * tags.length)]);
            }
            // 使用 JSON 格式化标签
            String formattedTags = selectedTags.stream()
                    .map(tag -> "\"" + tag + "\"")
                    .collect(Collectors.toList())
                    .toString();
            user.setTags(formattedTags);
            userList.add(user);
        }
        //mybatis-plus批量插入，每批次插入100条数据，不需要每次都建立SQL连接，提升速度性能
        userService.saveBatch(userList, 100);
        stopWatch.stop();
        System.out.println("插入用户耗时：" + stopWatch.getTotalTimeMillis() + "毫秒");
    }


    /**
     * 并发批量插入用户   100000  耗时： 26830ms
     */

    //  第一次延迟5秒后执行，之后按fixedRate的规则每10秒执行一次
    //@Scheduled(initialDelay = 5000, fixedRate = Long.MAX_VALUE)
    public void doConcurrencyInsertUser() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final int INSERT_SIZE = 100000;
        // 分十组
        int j = 0;
        //批量插入数据的大小
        int batchSize = 5000;
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        // i 要根据数据量和插入批量来计算需要循环的次数。（鱼皮这里直接取了个值，会有问题,我这里随便写的）
        for (int i = 0; i < INSERT_SIZE / batchSize; i++) {
            List<User> userList = new ArrayList<>();
            while (true) {
                j++;
                User user = new User();
                user.setUsername("假用户");
                user.setUserAccount("jiayonghu" + System.currentTimeMillis());
                user.setProfile("这是一个假数据用户。");
                user.setAvatarUrl("https://iknow-pic.cdn.bcebos.com/a9d3fd1f4134970af7a1f9b187cad1c8a6865d5a");
                user.setGender(i % 2 == 0 ? 0 : 1);
                user.setUserPassword("58f98006537194ebd5ec1ceaf6cde45e");
                user.setPhone("15111112222");
                user.setEmail("jiayonghu" + i + "@qq.com");
                user.setUserStatus(0);
                user.setUserRole(0);
                user.setPlanetCode("10000" + i);
                String[] tags = {"running", "女", "大一", "学生", "美食", "python", "java", "电影", "C++", "音乐", "旅行", "乐观", "开心", "阅读", "跳舞", "游戏", "健身"};
                List<String> selectedTags = new ArrayList<>();
                int tagCount = (int) (Math.random() * 6) + 1;
                for (int k = 0; k < tagCount; k++) {
                    selectedTags.add(tags[(int) (Math.random() * tags.length)]);
                }
                // 使用 JSON 格式化标签
                String formattedTags = selectedTags.stream()
                        .map(tag -> "\"" + tag + "\"")
                        .collect(Collectors.toList())
                        .toString();
                user.setTags(formattedTags);
                userList.add(user);
                if (j % batchSize == 0) {
                    break;
                }
            }
            //异步执行
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                System.out.println("ThreadName：" + Thread.currentThread().getName());
                userService.saveBatch(userList, batchSize);
            }, executorService);
            futureList.add(future);
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[]{})).join();

        stopWatch.stop();
        System.out.println(stopWatch.getLastTaskTimeMillis());
    }

}