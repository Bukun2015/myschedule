package myschedule.web.servlet.app.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Setter;
import myschedule.quartz.extra.QuartzRuntimeException;
import myschedule.quartz.extra.SchedulerTemplate;
import myschedule.service.ErrorCode;
import myschedule.service.ErrorCodeException;
import myschedule.service.SchedulerContainer;
import myschedule.service.SchedulerService;
import myschedule.service.ServiceUtils;
import myschedule.web.servlet.ActionHandler;
import myschedule.web.servlet.ViewData;
import myschedule.web.servlet.ViewDataActionHandler;
import org.quartz.JobDetail;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerHandlers {
	
	private static final Logger logger = LoggerFactory.getLogger(SchedulerHandlers.class);

	@Setter
	protected SchedulerContainer schedulerContainer;
		
	@Getter
	protected ActionHandler listenersHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			Map<String, Object> map = ViewData.mkMap();
			copySchedulerStatusData(schedulerService, map);
			SchedulerTemplate scheduler = schedulerService.getScheduler();
			try {
				ListenerManager listenerManager = scheduler.getListenerManager();
				map.put("jobListeners", listenerManager.getJobListeners());
				map.put("triggerListeners", listenerManager.getTriggerListeners());
				map.put("schedulerListeners", listenerManager.getSchedulerListeners());
				viewData.addData("data", map);
			} catch (QuartzRuntimeException e) {
				throw new ErrorCodeException(ErrorCode.SCHEDULER_PROBLEM, "Failed to retrieve scheduler listeners.", e);
			}
		}
	};
	
	@Getter
	protected ActionHandler modifyHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			String configPropsText = schedulerContainer.getSchedulerConfig(configId);
			SchedulerTemplate scheduler = schedulerService.getScheduler();
			viewData.addData("data", ViewData.mkMap(
				"configPropsText", configPropsText,
				"configId", configId,
				"isStandby", scheduler.isInStandbyMode()));
		}
	};

	@Getter
	protected ActionHandler modifyActionHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configPropsText = viewData.findData("configPropsText");
			String configId = viewData.findData("configId");
			schedulerContainer.modifyScheduler(configId, configPropsText);

			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			if (schedulerService.isInited()) {
				String schedulerName = schedulerService.getScheduler().getSchedulerNameAndId();
				viewData.addData("data", ViewData.mkMap( 
					"configId", configId,
					"schedulerService", schedulerService,
					"schedulerName", schedulerName));
			} else {
				viewData.setViewName("redirect:/dashboard/list");
			}
		}
	};
	
	@Getter
	protected ActionHandler summaryHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			Map<String, Object> map = ViewData.mkMap();
			copySchedulerStatusData(schedulerService, map);
			try {
				String summary = schedulerService.getScheduler().getMetaData().getSummary();
				map.put("schedulerSummary", summary);
				viewData.addData("data", map);
			} catch (SchedulerException e) {
				throw new ErrorCodeException(ErrorCode.WEB_UI_PROBLEM, "Unable to get scheduler summary info.", e);
			}
		}
	};

	@Getter
	protected ActionHandler detailHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			SchedulerTemplate schedulerTemplate = schedulerService.getScheduler();
			Map<String, Object> map = ViewData.mkMap();
			copySchedulerStatusData(schedulerService, map);
			if (schedulerTemplate.isStarted() && !schedulerTemplate.isShutdown()) {
				map.put("jobCount", "" + schedulerTemplate.getAllJobDetails().size());
				SchedulerMetaData schedulerMetaData = schedulerTemplate.getSchedulerMetaData();
				map.put("schedulerDetailMap", getSchedulerDetail(schedulerMetaData));
			}
			viewData.addData("data", map);
		}
	};

	@Getter
	protected ActionHandler pauseAllTriggersHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			SchedulerTemplate schedulerTemplate = schedulerService.getScheduler();
			List<Trigger> nonPausedTriggers = new ArrayList<Trigger>();
			for (JobDetail jobDetail : schedulerTemplate.getAllJobDetails()) {
				List<? extends Trigger> jobTriggers = schedulerTemplate.getTriggersOfJob(jobDetail.getKey());
				for (Trigger trigger : jobTriggers) {
					TriggerKey tk = trigger.getKey();
					if (schedulerTemplate.getTriggerState(tk) != TriggerState.PAUSED) {
						nonPausedTriggers.add(trigger);
					}
				}
			}
			schedulerTemplate.pauseAll();
			logger.info("Paused {} triggers in scheduler {}.", nonPausedTriggers.size(), schedulerTemplate.getSchedulerName());
			viewData.addData("data",  ViewData.mkMap("triggers", nonPausedTriggers));
		}
	};
	
	@Getter
	protected ActionHandler resumeAllTriggersHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			SchedulerTemplate schedulerTemplate = schedulerService.getScheduler();
			List<Trigger> pausedTriggers = schedulerTemplate.getPausedTriggers();
			schedulerTemplate.resumeAllTriggers();
			logger.info("Resumed {} triggers in scheduler {}.", pausedTriggers.size(), schedulerTemplate.getSchedulerName());
			viewData.addData("data",  ViewData.mkMap("triggers", pausedTriggers));
		}
	};
	
	@Getter
	protected ActionHandler startHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			try {
				schedulerService.getScheduler().start();
			} catch (QuartzRuntimeException e) {
				throw new ErrorCodeException(ErrorCode.SCHEDULER_PROBLEM, e);
			}
			viewData.setViewName("redirect:/scheduler/detail");
		}
	};

	@Getter
	protected ActionHandler standbyHandler = new ViewDataActionHandler() {
		@Override
		protected void handleViewData(ViewData viewData) {
			String configId = viewData.findData("configId");
			SchedulerService schedulerService = schedulerContainer.getSchedulerService(configId);
			try {
				schedulerService.getScheduler().standby();
			} catch (QuartzRuntimeException e) {
				throw new ErrorCodeException(ErrorCode.SCHEDULER_PROBLEM, e);
			}
			viewData.setViewName("redirect:/scheduler/detail");
		}
	};
	
	protected TreeMap<String, String> getSchedulerDetail(SchedulerMetaData schedulerMetaData) {
		TreeMap<String, String> schedulerInfo = new TreeMap<String, String>();
		List<ServiceUtils.Getter> getters = ServiceUtils.getGetters(schedulerMetaData);
		for (ServiceUtils.Getter getter : getters) {
			String key = getter.getPropName();
			if (key.length() >= 1) {
				if (key.equals("summary")) // skip this field.
					continue;
				key = key.substring(0, 1).toUpperCase() + key.substring(1);
				String value = ServiceUtils.getGetterStrValue(getter);
				schedulerInfo.put(key, value);
			} else {
				logger.warn("Skipped a scheduler info key length less than 1 char. Key=" + key);
			}
		}
		logger.debug("Scheduler info map has " + schedulerInfo.size() + " items.");
		return schedulerInfo;
	}
	

	protected void copySchedulerStatusData(SchedulerService schedulerService, Map<String, Object> dataMap) {
		SchedulerTemplate schedulerTemplate = new SchedulerTemplate((Scheduler)schedulerService.getScheduler());
		dataMap.put("schedulerName", schedulerTemplate.getSchedulerName());
		dataMap.put("isStarted", schedulerTemplate.isStarted());
		dataMap.put("isStandby", schedulerTemplate.isInStandbyMode());
		dataMap.put("isShutdown", schedulerTemplate.isShutdown());
	}
}
