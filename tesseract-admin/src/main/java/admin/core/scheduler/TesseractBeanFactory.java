package admin.core.scheduler;

import admin.core.scheduler.bean.CurrentTaskInfo;
import admin.core.scheduler.bean.TaskContextInfo;
import admin.core.scheduler.pool.DefaultSchedulerThreadPool;
import admin.entity.*;
import tesseract.core.dto.TesseractExecutorRequest;

import java.util.List;

import static admin.constant.AdminConstant.LOG_FAIL;
import static admin.constant.AdminConstant.SCHEDULER_NAME_MAP;

public class TesseractBeanFactory {
    /**
     * 构建默认日志
     *
     * @return
     */
    public static TesseractLog createDefaultLog(CurrentTaskInfo currentTaskInfo) {
        TesseractJobDetail jobDetail = currentTaskInfo.getTaskContextInfo().getJobDetail();
        TesseractTrigger trigger = currentTaskInfo.getTaskContextInfo().getTrigger();
        Integer shardingIndex = currentTaskInfo.getShardingIndex();
        TesseractLog tesseractLog = new TesseractLog();
        tesseractLog.setClassName(jobDetail.getClassName());
        tesseractLog.setCreateTime(System.currentTimeMillis());
        tesseractLog.setCreator(jobDetail.getCreator());
        tesseractLog.setRetryCount(0);
        tesseractLog.setGroupId(trigger.getGroupId());
        tesseractLog.setGroupName(trigger.getGroupName());
        tesseractLog.setTriggerName(trigger.getName());
        tesseractLog.setEndTime(System.currentTimeMillis());
        tesseractLog.setExecutorDetailId(0);
        tesseractLog.setStatus(LOG_FAIL);
        tesseractLog.setSocket("");
        tesseractLog.setStrategy(SCHEDULER_NAME_MAP.getOrDefault(trigger.getStrategy(), "未知调度<不应该出现>"));
        if (shardingIndex == null) {
            tesseractLog.setShardingIndex(-1);
        } else {
            tesseractLog.setShardingIndex(shardingIndex);
        }
        return tesseractLog;
    }

    /**
     * 构建请求
     *
     * @param currentTaskInfo
     * @return
     */
    public static TesseractExecutorRequest createRequest(CurrentTaskInfo currentTaskInfo) {
        TesseractJobDetail jobDetail = currentTaskInfo.getTaskContextInfo().getJobDetail();
        TesseractTrigger trigger = currentTaskInfo.getTaskContextInfo().getTrigger();
        TesseractLog log = currentTaskInfo.getLog();
        TesseractExecutorRequest executorRequest = new TesseractExecutorRequest();
        executorRequest.setJobId(jobDetail.getId());
        executorRequest.setClassName(jobDetail.getClassName());
        executorRequest.setLogId(log.getId());
        executorRequest.setTriggerId(trigger.getId());
        executorRequest.setShardingIndex(currentTaskInfo.getShardingIndex());
        executorRequest.setExecutorDetailId(currentTaskInfo.getCurrentExecutorDetail().getId());
        return executorRequest;
    }

    /**
     * 构建正在执行的任务bean
     *
     * @param currentTaskInfo
     * @return
     */
    public static TesseractFiredJob createFiredJob(CurrentTaskInfo currentTaskInfo) {
        TesseractTrigger trigger = currentTaskInfo.getTaskContextInfo().getTrigger();
        TesseractJobDetail jobDetail = currentTaskInfo.getTaskContextInfo().getJobDetail();
        TesseractExecutorDetail currentExecutorDetail = currentTaskInfo.getCurrentExecutorDetail();
        TesseractLog log = currentTaskInfo.getLog();
        TesseractFiredJob tesseractFiredTrigger = new TesseractFiredJob();
        tesseractFiredTrigger.setCreateTime(System.currentTimeMillis());
        tesseractFiredTrigger.setTriggerName(trigger.getName());
        tesseractFiredTrigger.setTriggerId(trigger.getId());
        tesseractFiredTrigger.setJobId(jobDetail.getId());
        tesseractFiredTrigger.setClassName(jobDetail.getClassName());
        tesseractFiredTrigger.setSocket(currentExecutorDetail.getSocket());
        tesseractFiredTrigger.setExecutorDetailId(currentExecutorDetail.getId());
        tesseractFiredTrigger.setLogId(log.getId());
        tesseractFiredTrigger.setRetryCount(0);
        return tesseractFiredTrigger;
    }


    /**
     * 创建调度线程
     *
     * @param tesseractGroup
     * @return
     */
    public static SchedulerThread createSchedulerThread(TesseractGroup tesseractGroup) {
        TesseractTriggerDispatcher triggerDispatcher = createTesseractTriggerDispatcher(tesseractGroup.getName(), tesseractGroup.getThreadPoolNum());
        SchedulerThread schedulerThread = new SchedulerThread(tesseractGroup, triggerDispatcher);
        schedulerThread.setDaemon(true);
        return schedulerThread;
    }

    /**
     * 创建任务分发器
     *
     * @param groupName
     * @param threadNum
     * @return
     */
    public static TesseractTriggerDispatcher createTesseractTriggerDispatcher(String groupName, Integer threadNum) {
        DefaultSchedulerThreadPool threadPool = new DefaultSchedulerThreadPool(threadNum);
        TesseractTriggerDispatcher tesseractTriggerDispatcher = new TesseractTriggerDispatcher();
        tesseractTriggerDispatcher.setGroupName(groupName);
        tesseractTriggerDispatcher.setThreadPool(threadPool);
        return tesseractTriggerDispatcher;
    }


    /**
     * 创建任务调度上下文Bean
     *
     * @param jobDetail
     * @param executorDetailList
     * @param trigger
     * @return
     */
    public static TaskContextInfo createTaskContextInfo(TesseractJobDetail jobDetail,
                                                        List<TesseractExecutorDetail> executorDetailList, TesseractTrigger trigger) {
        TaskContextInfo taskContextInfo = new TaskContextInfo();
        taskContextInfo.setJobDetail(jobDetail);
        taskContextInfo.setExecutorDetailList(executorDetailList);
        taskContextInfo.setTrigger(trigger);
        return taskContextInfo;
    }
}