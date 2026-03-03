package cronjobs;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.schedule.AbstractScheduledLockedJob;
import org.alfresco.schedule.ScheduledJobLockExecuter;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;


public class StatsGeneratorJobDetail extends AbstractScheduledLockedJob implements StatefulJob {


    @Override
    public void executeJob(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobData = context.getJobDetail().getJobDataMap();

        // Extract the Job executer to use
        Object executerObj = jobData.get("jobExecuter");
        if (executerObj == null || !(executerObj instanceof StatefulJob)) {
            throw new AlfrescoRuntimeException(
                    "ScheduledJob data must contain valid 'Executer' reference");
        }
        StatefulJob statefulJob = (StatefulJob) executerObj;
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>() {
            public Object doWork() throws Exception {
                statefulJob.execute(context);
                return null;
            }
        }, AuthenticationUtil.getSystemUserName());
    }
}
