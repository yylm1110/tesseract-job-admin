package com.kevin.scepter.client.core.utils;

/**
 * @author: kevin
 * @description: 系统工具
 * @updateRemark: 修改内容(每次大改都要写修改内容)
 * @date: 2019-07-30 10:58
 */
public class SystemUtils {

    /**
     * 判断是否是苹果系统
     *
     * @return boolean
     */
    public static boolean isMacOs() {
        String os = System.getProperties().getProperty("os.name");
        return "Mac OS X".equalsIgnoreCase(os);
    }

    /**
     * 判断是否是win系统
     *
     * @return boolean
     */
    public static boolean isWinOs() {
        String os = System.getProperties().getProperty("os.name");
        return os.toLowerCase().indexOf("windows") > -1;
    }

    /**
     * 获得系统能够开启的线程数，根据可以开启的最大线程数-1来计算，最少为1
     *
     * @return int
     */
    public static int getSystemThreadCount() {
        int cpus = getCpuProcessorCount();
        int result = cpus - 1;
        return result == 0 ? 1 : result;
    }

    /**
     * 获得CPU数量
     *
     * @return int
     */
    public static int getCpuProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }

}