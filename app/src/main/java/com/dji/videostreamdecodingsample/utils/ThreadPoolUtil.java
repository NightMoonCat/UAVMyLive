package com.dji.videostreamdecodingsample.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {

    //4核的线程池
    private static ThreadPoolExecutor m4CoreThreadPoolExecutor;
    //单核的线程池
    private static ThreadPoolExecutor mSingleThreadPoolExecutor;

    /**
     * 获取4个核心线程数,最大线程数为8的线程池
     *
     * @return
     */
    public static ThreadPoolExecutor get4CoreThreadExcutor() {
        if (m4CoreThreadPoolExecutor == null) {
            synchronized (ThreadPoolUtil.class) {
                if (m4CoreThreadPoolExecutor == null) {
                    m4CoreThreadPoolExecutor = new ThreadPoolExecutor(
                            4,
                            8,
                            3,
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<Runnable>(3),
                            new ThreadPoolExecutor.DiscardOldestPolicy()
                    );
                }
            }
        }
        return m4CoreThreadPoolExecutor;
    }

    /**
     * 获取一个核心线程数的单线程池
     *
     * @return
     */
    public static ThreadPoolExecutor getmSingleThreadPoolExecutor() {
        if (mSingleThreadPoolExecutor == null) {
            synchronized (ThreadPoolUtil.class) {
                if (mSingleThreadPoolExecutor == null) {
                    mSingleThreadPoolExecutor = new ThreadPoolExecutor(
                            1,
                            1,
                            0,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<Runnable>()
                    );
                }
            }
        }
        return mSingleThreadPoolExecutor;
    }
}