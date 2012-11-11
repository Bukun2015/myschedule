package myschedule.quartz.extra;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.List;
import myschedule.quartz.extra.SchedulerMain;
import myschedule.quartz.extra.util.ProcessUtils;
import myschedule.quartz.extra.util.ResultFile;

import org.junit.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.SchedulerPlugin;

public class SchedulerMainTest {
	public static ResultFile RESULT_FILE = new ResultFile("target/SchedulerMainTest.tmp");
	
	@Test
	public void testMainWithTimeout() throws Exception {		
		try {
			RESULT_FILE.resetFile();
			// Run SchedulerMain with timeout settings so it should exit automatically.
			String config = "myschedule/quartz/extra/SchedulerMainTest-quartz.properties";
			String[] javaCmdArgs = { SchedulerMain.class.getName(), config };
			String[] javaOpts = { "-DSchedulerMain.Timeout=700" };
			ProcessUtils.runJavaWithOpts(3000, javaOpts, javaCmdArgs);
			
			List<String> result = RESULT_FILE.readLines();
			assertThat(result.size(), is(4));
			assertThat(result.get(0), is("name: MyResultSchedulerPluginTest"));
			assertThat(result.get(1), containsString("initialize:"));
			assertThat(result.get(2), containsString("start:"));
			assertThat(result.get(3), containsString("shutdown:"));
		} finally {
			RESULT_FILE.delete();
		}
	}
	
	@Test
	public void testMainAsServer() throws Exception {		
		try {
			RESULT_FILE.resetFile();
			try {
				// Default SchedulerMain will run as server, so this should cause test to timeout.
				String config = "myschedule/quartz/extra/SchedulerMainTest-quartz.properties";
				ProcessUtils.runJava(700, SchedulerMain.class.getName(), config);
				fail("We should have timed-out, but didn't.");
			} catch (ProcessUtils.TimeoutException e) {
				// expected.
			}
			List<String> result = RESULT_FILE.readLines();
			assertThat(result.size(), is(3));
			assertThat(result.get(0), is("name: MyResultSchedulerPluginTest"));
			assertThat(result.get(1), containsString("initialize:"));
			assertThat(result.get(2), containsString("start:"));
			
			// Note we don't have shutdown due to timeout!
		} finally {
			RESULT_FILE.delete();
		}
	}
	
	public static class ResultSchedulerPlugin implements SchedulerPlugin {
		public ResultSchedulerPlugin() {
		}
		
		@Override
		public void initialize(String name, Scheduler scheduler) throws SchedulerException {
			RESULT_FILE.appendLine("name: " + name);
			RESULT_FILE.appendLine("initialize: " + new Date());
		}

		@Override
		public void start() {
			RESULT_FILE.appendLine("start: " + new Date());
		}

		@Override
		public void shutdown() {
			RESULT_FILE.appendLine("shutdown: " + new Date());
		}
	}
}
