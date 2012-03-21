package myschedule.web;

import java.io.File;
import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import lombok.Getter;
import myschedule.service.ConfigStore;
import myschedule.service.FileConfigStore;
import myschedule.service.Initable;
import myschedule.service.ResourceLoader;
import myschedule.service.SchedulerContainer;
import myschedule.service.ServiceContainer;
import myschedule.web.servlet.app.filter.SessionDataFilter;
import myschedule.web.servlet.app.handler.DashboardHandlers;
import myschedule.web.servlet.app.handler.JobHandlers;
import myschedule.web.servlet.app.handler.SchedulerHandlers;
import myschedule.web.servlet.app.handler.ScriptingHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a singleton space/container to hold global application services and configuration.
 * 
 * @author Zemian Deng <saltnlight5@gmail.com>
 *
 */
public class AppConfig extends EasyMap implements Initable {
	private static final String CONFIG_FILE_KEY = "myschedule.config";
	private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
	
	// This class singleton instance access
	// ====================================
	private static AppConfig instance;
	
	private AppConfig() {
		super("classpath:myschedule/myschedule-config.default.properties");
	}
	
	synchronized public static AppConfig getInstance() {
		if (instance == null) {
			instance = new AppConfig();
		}
		return instance;
	}

	// Application services
	// ====================
	// This field is not exposed out side
	private ServiceContainer serviceContainer;
	
	@Getter
	private ConfigStore configStore;
	@Getter
	private SchedulerContainer schedulerContainer;
	@Getter
	private ResourceLoader resourceLoader;
	
	@Getter
	private DashboardHandlers dashboardHandler;	
	@Getter
	private JobHandlers jobHandlers;
	@Getter
	private SchedulerHandlers schedulerHandlers;
	@Getter
	private ScriptingHandlers scriptingHandlers;
    @Getter
    protected SessionDataFilter sessionDataFilter;
	
	@Override
	public void init() {
		// Let user add more config if needed to.
		addConfigBySysProps(CONFIG_FILE_KEY);

		serviceContainer = new ServiceContainer();		
		resourceLoader = new ResourceLoader();
		
		Class<?> configStoreCls = getConfigClass("myschedule.configStore.class");
		ConfigStore configStore = newInstance(configStoreCls);
		if (configStore instanceof FileConfigStore) {
			String dir = getConfig("myschedule.configStore.FileConfigStore.directory");
			File configDir = new File(dir);
			logger.info("FileConfigStore directory set to: {}", configDir);
			
			FileConfigStore fileConfigStore = (FileConfigStore)configStore;
			fileConfigStore.setStoreDir(configDir);
		}
		logger.info("ConfigStore set to: {}", configStore);
		serviceContainer.addService(configStore);

		schedulerContainer = new SchedulerContainer();
		schedulerContainer.setConfigStore(configStore);
		serviceContainer.addService(schedulerContainer);

		dashboardHandler = new DashboardHandlers();
		dashboardHandler.setSchedulerContainer(schedulerContainer);
		dashboardHandler.setResourceLoader(resourceLoader);

		int defaultFireTimesCount = getConfigInt("myschedule.handlers.JobHandler.defaultFireTimesCount");
		jobHandlers = new JobHandlers();
		jobHandlers.setDefaultFireTimesCount(defaultFireTimesCount);
		jobHandlers.setSchedulerContainer(schedulerContainer);
		
		schedulerHandlers = new SchedulerHandlers();
		schedulerHandlers.setSchedulerContainer(schedulerContainer);
		
		scriptingHandlers = new ScriptingHandlers();
		scriptingHandlers.setSchedulerContainer(schedulerContainer);
		scriptingHandlers.setResourceLoader(resourceLoader);
		
		sessionDataFilter = new SessionDataFilter();
        sessionDataFilter.setSchedulerContainer(schedulerContainer);
		
		// Ensure all services get init and started.
		serviceContainer.init();
		serviceContainer.start();
	}
	
	@Override
	public void destroy() {
		if (serviceContainer != null) {
			// Ensure all services get stop and destroy
			serviceContainer.stop();
			serviceContainer.destroy();
		}
	}
	
	@Override
	public boolean isInited() {
		throw new RuntimeException("Not supported.");
	}
	
	// Web App Config
	// ==============	
	private static final String MY_SCHEDULE_VERSION_RES_NAME = "META-INF/maven/myschedule/myschedule-quartz-extra/pom.properties";
	private static final String QUARTZ_VERSION_RES_NAME = "META-INF/maven/org.quartz-scheduler/quartz/pom.properties";
	
	public void contextInitialized(ServletContextEvent sce) {
		ServletContext ctx = sce.getServletContext();
		String myscheduleVersion = getMyScheduleVersion();
		String contextPath = ctx.getContextPath();
		String quartzVersion = getQuartzVersion();
		
		ctx.setAttribute("myscheduleVersion", myscheduleVersion);
		logger.info("Set webapp attribute myscheduleVersion=" + ctx.getAttribute("myscheduleVersion"));
		
		ctx.setAttribute("quartzVersion", quartzVersion);
		logger.info("Set webapp attribute quartzVersion=" + ctx.getAttribute("quartzVersion"));

		ctx.setAttribute("contextPath", contextPath);
		logger.info("Set webapp attribute contextPath=" + ctx.getAttribute("contextPath"));

		String mainServletPathName = getMainServletPathName();
		ctx.setAttribute("mainPath", contextPath + mainServletPathName);
		logger.info("Set webapp attribute mainPath=" + ctx.getAttribute("mainPath"));

		String viewsDirectory = getViewsDirectory();
		ctx.setAttribute("viewsPath", contextPath + viewsDirectory);
		logger.info("Set webapp attribute viewsPath=" + ctx.getAttribute("viewsPath"));
		
		String themeName = getConfig("myschedule.web.themeName");
		ctx.setAttribute("themeName", themeName);
		logger.info("Set webapp attribute themeName=" + ctx.getAttribute("themeName"));
	}
	
	public String getMainServletPathName() {
		return getConfig("myschedule.web.mainServletPathName");
	}
	
	public String getViewsDirectory() {
		return getConfig("myschedule.web.viewsDirectory");
	}
		
	private String getMyScheduleVersion() {
		try {
			Properties props = resourceLoader.loadProperties(MY_SCHEDULE_VERSION_RES_NAME);
			String version = props.getProperty("version");
			return "myschedule-" + version;
		} catch (RuntimeException e) {
			logger.debug("Not able to get myschedule version properties. Use LATEST.SNAPSHOT label instead.");
			return "myschedule-LATEST.SNAPSHOT";
		}
	}

	private String getQuartzVersion() {
		try {
			Properties props = resourceLoader.loadProperties(QUARTZ_VERSION_RES_NAME);
			String version = props.getProperty("version");
			return "quartz-" + version;
		} catch (RuntimeException e) {
			logger.warn("Failed to get quartz version properties. Use UNKNOWN label instead.", e);
			return "quartz-UNKNOWN";
		}
	}
	
	public long getPauseAfterShutdown() {
		return getConfigLong("myschedule.web.pauseAfterShutdown");
	}
}
