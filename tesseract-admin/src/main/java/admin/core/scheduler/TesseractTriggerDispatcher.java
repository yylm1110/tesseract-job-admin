package admin.core.scheduler;

import admin.constant.AdminConstant;
import admin.core.event.MailEvent;
import admin.core.mail.TesseractMailTemplate;
import admin.core.scheduler.pool.ISchedulerThreadPool;
import admin.core.scheduler.router.impl.HashRouter;
import admin.entity.*;
import admin.service.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import feignService.IAdminFeignService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import tesseract.core.dto.TesseractExecutorRequest;
import tesseract.core.dto.TesseractExecutorResponse;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

import static admin.constant.AdminConstant.*;
import static admin.util.AdminUtils.epochMiliToString;
import static tesseract.core.constant.CommonConstant.EXECUTE_MAPPING;
import static tesseract.core.constant.CommonConstant.HTTP_PREFIX;

@Slf4j
@Data
public class TesseractTriggerDispatcher {
    private static final String LOG_TEMPLATE_NAME = "logTemplate.html";
    private static final String LOG_SUBJECT = "Tesseract-job日志报警邮件";
    private String groupName;
    private ITesseractJobDetailService tesseractJobDetailService;
    private ITesseractLogService tesseractLogService;
    private ITesseractGroupService groupService;
    private ITesseractExecutorDetailService executorDetailService;
    private ITesseractExecutorService executorService;
    private ITesseractFiredJobService firedJobService;
    private IAdminFeignService feignService;
    private ISchedulerThreadPool threadPool;
    private TesseractMailTemplate mailTemplate;
    private EventBus mailEventBus;


    public ISchedulerThreadPool getThreadPool() {
        return threadPool;
    }

    public void dispatchTrigger(List<TesseractTrigger> triggerList, boolean isOnce) {
        triggerList.stream().forEach(trigger -> threadPool.runJob(new TaskRunnable(trigger, isOnce)));
    }

    public int blockGetAvailableThreadNum() {
        return threadPool.blockGetAvailableThreadNum();
    }

    public void init() {
        threadPool.init();
    }

    private class TaskRunnable implements Runnable {
        private TesseractTrigger trigger;
        private boolean isOnce;

        public TaskRunnable(TesseractTrigger trigger, boolean isOnce) {
            this.trigger = trigger;
            this.isOnce = isOnce;
        }


        @Override
        public void run() {
            try {
                //获取job detail
                TesseractJobDetail jobDetail = getJobDetail();
                if (jobDetail == null) {
                    doFail("没有发现可运行job");
                    return;
                }
                //获取执行器
                TesseractExecutor executor = executorService.getById(trigger.getExecutorId());
                if (executor == null) {
                    doFail("没有找到可用执行器");
                    return;
                }
                //执行器下机器列表
                List<TesseractExecutorDetail> executorDetailList = getExecutorDetail(executor.getId());
                if (CollectionUtils.isEmpty(executorDetailList)) {
                    doFail("执行器下没有可用机器");
                    return;
                }
                //路由发送执行
                routerExecute(jobDetail, executorDetailList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * @param jobDetail          触发器对应任务
         * @param executorDetailList 机器列表
         */
        private void routerExecute(TesseractJobDetail jobDetail, List<TesseractExecutorDetail> executorDetailList) {
            //路由选择
            @NotNull Integer strategy = trigger.getStrategy();
            //广播
            if (SCHEDULER_STRATEGY_BROADCAST.equals(strategy)) {
                executorDetailList.parallelStream().forEach(executorDetail -> buildRequestAndSend(jobDetail, executorDetail, null));
            } else if (SCHEDULER_STRATEGY_SHARDING.equals(strategy)) {
                //分片
                @NotNull Integer shardingNum = trigger.getShardingNum();
                int size = executorDetailList.size();
                int count = 0;
                for (int i = 0; i < shardingNum; i++) {
                    if (i < size) {
                        count = 0;
                    }
                    buildRequestAndSend(jobDetail, executorDetailList.get(count), count++);
                }

            } else {
                //正常调用
                TesseractExecutorDetail executorDetail = SCHEDULE_ROUTER_MAP.getOrDefault(trigger.getStrategy(), new HashRouter()).routerExecutor(executorDetailList);
                buildRequestAndSend(jobDetail, executorDetail, null);
            }
        }

        /**
         * 构建请求并发送
         *
         * @param jobDetail
         * @param executorDetail
         */
        private void buildRequestAndSend(TesseractJobDetail jobDetail, TesseractExecutorDetail executorDetail, Integer shardingIndex) {
            TesseractLog tesseractLog = buildDefaultLog(shardingIndex);
            tesseractLog.setSocket(executorDetail.getSocket());
            tesseractLog.setMsg("执行中");
            //设置结束时间为0 表示未结束
            tesseractLog.setEndTime(0L);
            tesseractLog.setStatus(AdminConstant.LOG_WAIT);
            tesseractLog.setExecutorDetailId(executorDetail.getId());
            tesseractLogService.save(tesseractLog);
            //设置firedTrigger
            firedJobService.save(buildFiredJob(jobDetail, executorDetail, tesseractLog.getId()));
            //构建请求发送
            doRequest(buildRequest(tesseractLog.getId(), jobDetail.getId(), jobDetail.getClassName(), executorDetail.getId(), shardingIndex), tesseractLog, executorDetail);
        }

        /**
         * 构建默认日志
         *
         * @return
         */
        private TesseractLog buildDefaultLog(Integer shardingIndex) {
            TesseractLog tesseractLog = new TesseractLog();
            tesseractLog.setClassName("");
            tesseractLog.setCreateTime(System.currentTimeMillis());
            tesseractLog.setCreator("test");
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
         * 获取当前触发器可执行的任务
         * 一个触发器只能对应一个任务!!!!!!!!!!!
         *
         * @return
         */
        private TesseractJobDetail getJobDetail() {
            QueryWrapper<TesseractJobDetail> jobQueryWrapper = new QueryWrapper<>();
            jobQueryWrapper.lambda().eq(TesseractJobDetail::getTriggerId, trigger.getId());
            return tesseractJobDetailService.getOne(jobQueryWrapper);
        }

        /**
         * 保存失败日志并产生报警邮件
         *
         * @param msg
         */
        private void doFail(String msg) {
            TesseractLog tesseractLog = buildDefaultLog(null);
            tesseractLog.setMsg(msg);
            tesseractLogService.save(tesseractLog);
            sendMail(tesseractLog);
        }

        /**
         * 获取可执行的机器
         *
         * @param executorId
         * @return
         */
        private List<TesseractExecutorDetail> getExecutorDetail(Integer executorId) {
            QueryWrapper<TesseractExecutorDetail> executorDetailQueryWrapper = new QueryWrapper<>();
            executorDetailQueryWrapper.lambda().eq(TesseractExecutorDetail::getExecutorId, executorId);
            return executorDetailService.list(executorDetailQueryWrapper);
        }

        /**
         * 构建正在执行的任务bean
         *
         * @param jobDetail
         * @param executorDetail
         * @param logId
         * @return
         */
        private TesseractFiredJob buildFiredJob(TesseractJobDetail jobDetail, TesseractExecutorDetail executorDetail, Long logId) {
            TesseractFiredJob tesseractFiredTrigger = new TesseractFiredJob();
            tesseractFiredTrigger.setCreateTime(System.currentTimeMillis());
            tesseractFiredTrigger.setTriggerName(trigger.getName());
            tesseractFiredTrigger.setTriggerId(trigger.getId());
            tesseractFiredTrigger.setJobId(jobDetail.getId());
            tesseractFiredTrigger.setClassName(jobDetail.getClassName());
            tesseractFiredTrigger.setSocket(executorDetail.getSocket());
            tesseractFiredTrigger.setLogId(logId);
            tesseractFiredTrigger.setRetryCount(0);
            return tesseractFiredTrigger;
        }

        /**
         * 构建请求
         *
         * @param logId
         * @param className
         * @param executorDetailId
         * @return
         */
        private TesseractExecutorRequest buildRequest(Long logId, Integer jobId, String className, Integer executorDetailId, Integer shardingIndex) {
            TesseractExecutorRequest executorRequest = new TesseractExecutorRequest();
            executorRequest.setJobId(jobId);
            executorRequest.setClassName(className);
            executorRequest.setShardingIndex(trigger.getShardingNum());
            executorRequest.setLogId(logId);
            executorRequest.setTriggerId(trigger.getId());
            executorRequest.setShardingIndex(shardingIndex);
            executorRequest.setExecutorDetailId(executorDetailId);
            return executorRequest;
        }

        /**
         * 发送调度请求
         *
         * @param executorRequest
         * @param tesseractLog
         * @param executorDetail
         */
        private void doRequest(TesseractExecutorRequest executorRequest, TesseractLog tesseractLog, TesseractExecutorDetail executorDetail) {
            log.info("开始调度:{}", executorRequest);
            TesseractExecutorResponse response;
            try {
                response = feignService.sendToExecutor(new URI(HTTP_PREFIX + executorDetail.getSocket() + EXECUTE_MAPPING), executorRequest);
            } catch (URISyntaxException e) {
                log.error("URI异常:{}", e.getMessage());
                response = TesseractExecutorResponse.builder().body("URI异常").status(TesseractExecutorResponse.FAIL_STAUTS).build();
            }
            //执行成功直接返回等待执行后更新日志状态
            if (response.getStatus() == TesseractExecutorResponse.SUCCESS_STATUS) {
                return;
            }
            //如果执行失败则更新日志状态并且移出执行表
            tesseractLog.setStatus(AdminConstant.LOG_FAIL);
            tesseractLog.setEndTime(System.currentTimeMillis());
            Object body = response.getBody();
            if (body != null) {
                tesseractLog.setMsg(body.toString());
            }
            //移出执行表
            QueryWrapper<TesseractFiredJob> firedJobQueryWrapper = new QueryWrapper<>();
            firedJobQueryWrapper.lambda().eq(TesseractFiredJob::getTriggerId, trigger.getId());
            firedJobService.remove(firedJobQueryWrapper);
            //修改日志状态
            tesseractLog.setStatus(LOG_FAIL);
            tesseractLogService.updateById(tesseractLog);
            log.info("tesseractLog:{}", tesseractLog);
            //发送邮件
            this.sendMail(tesseractLog);
        }

        private void sendMail(TesseractLog tesseractLog) {
            sendMail(tesseractLog, groupService.getById(tesseractLog.getGroupId()));
        }

        /**
         * 失败后发送报警邮件
         */
        private void sendMail(TesseractLog tesseractLog, TesseractGroup group) {
            HashMap<String, Object> model = Maps.newHashMap();
            model.put("log", tesseractLog);
            model.put("createTime", epochMiliToString(tesseractLog.getCreateTime(), null));
            model.put("endTime", epochMiliToString(tesseractLog.getEndTime(), null));
            String body = mailTemplate.buildMailBody(LOG_TEMPLATE_NAME, model);
            MailEvent mailEvent = new MailEvent();
            mailEvent.setBody(body);
            mailEvent.setSubject(LOG_SUBJECT);
            mailEvent.setTo(group.getMail());
            mailEventBus.post(mailEvent);
        }

    }

    public void stop() {
        threadPool.shutdown();
    }
}