package cronjobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.StatsConfig;
import model.StatsQueryResult;
import services.StatGeneratorHelper;


public class StatsGeneratorExecuter implements StatefulJob {
    private static final Logger LOG = LoggerFactory.getLogger(StatsGeneratorExecuter.class);

    private ServiceRegistry serviceRegistry;
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    private StatGeneratorHelper statGeneratorHelper;
    public void setStatGeneratorHelper(StatGeneratorHelper statGeneratorHelper) {this.statGeneratorHelper = statGeneratorHelper;}

    /**
     * On execute retrieves all json Configs of type venzia:statconfig,
     * parses each config and launch the needed queries to build an output json,
     * then saves that json in the specified path
     * */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if(LOG.isDebugEnabled()){LOG.debug("+++ Starting stats generator executer +++");}
        if (serviceRegistry == null) {
            LOG.warn("ServiceRegistry is not injected in StatsGeneratorExecuter; using helper services only");
        }
        long startMilis = System.currentTimeMillis();
        //get jsons of type venzia:statConfig
        ResultSet rs = statGeneratorHelper.searchStatsConfigs();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stats config files found in repository: {}", rs.length());
        }

        /**List of json files each one have several queries*/
        List<StatsConfig> configs = new ArrayList<>();

        //parse each nodeRef to statConfigObject
        rs.forEach(result -> {
            NodeRef nodeRef = result.getNodeRef();
            boolean isWorkspaceNode = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef());
            boolean exists = serviceRegistry != null && serviceRegistry.getNodeService().exists(nodeRef);
            if (!isWorkspaceNode || !exists) {
                LOG.warn("Skipping stats config node '{}' because it is not an active workspace node (workspace={}, exists={})",
                    nodeRef, isWorkspaceNode, exists);
                return;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Parsing stats config node: {}", nodeRef);
            }
            StatsConfig statsConfig = statGeneratorHelper.parseContentToStatConfig(result);
            configs.add(statsConfig);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Parsed stats config: id='{}', outputPath='{}', queries={} ",
                    statsConfig.getId(), statsConfig.getOutputPathFolder(), statsConfig.getQueries() != null ? statsConfig.getQueries().length() : 0);
            }
        });

        if (LOG.isDebugEnabled()) {
            LOG.debug("Total parsed stats config files: {}", configs.size());
        }

        //for each config launch search
        configs.forEach(config -> {
            JSONArray searchs = config.getQueries();
            int queryCount = searchs != null ? searchs.length() : 0;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processing stats config id='{}' with {} queries", config.getId(), queryCount);
            }
            List<StatsQueryResult> results = new ArrayList<>();
            //for each query get its results
            if (searchs != null) {
                searchs.forEach(searchDefinition -> {
                    JSONObject searchObject = new JSONObject(searchDefinition.toString());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Executing query for config id='{}': {}", config.getId(), searchObject);
                    }
                    try {
                        StatsQueryResult searchResult = statGeneratorHelper.getResultsForQuery(searchObject);
                        results.add(searchResult);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Query result for config id='{}': {}", config.getId(), searchResult != null ? searchResult.toHashmap() : null);
                        }
                    } catch (RuntimeException ex) {
                        LOG.error("Skipping failed query for config id='{}'. Cause: {}. Query: {}", config.getId(), ex.getMessage(), searchObject, ex);
                    }
                });
            }

            //build stat report JSON OUTPUT
            HashMap<String, Object> jsonOutput = new HashMap<>();
            jsonOutput.put("id", config.getId());
            ArrayList<Object> resultsArray = new ArrayList<>();
            results.forEach(result -> resultsArray.add(result.toHashmap()));
            jsonOutput.put("results", resultsArray);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Built stats output for config id='{}' with {} result entries", config.getId(), resultsArray.size());
            }

            //save stat report in path
            String destinationFolderPath = config.getOutputPathFolder();
            try {
                NodeRef destinationFolder = statGeneratorHelper.ensureOutputPathExists(destinationFolderPath);
                if(destinationFolder != null){
                   if (LOG.isDebugEnabled()) {
                       LOG.debug("Saving report for config id='{}' into destination='{}'", config.getId(), destinationFolderPath);
                   }
                   statGeneratorHelper.saveJsonInPath(destinationFolder, config, jsonOutput);
                   if (LOG.isDebugEnabled()) {
                       LOG.debug("Saved report for config id='{}' successfully", config.getId());
                   }
                }else{
                    LOG.error("Destination folder '{}' not found for config id='{}'", destinationFolderPath, config.getId());
                }
            } catch (RuntimeException ex) {
                LOG.error("Skipping save for config id='{}' because destination path '{}' is not available or could not be created. Cause: {}",
                    config.getId(), destinationFolderPath, ex.getMessage(), ex);
            }
        });
        long endMilis = System.currentTimeMillis();
        if(LOG.isDebugEnabled()){
            long time = endMilis - startMilis;
            LOG.debug("+++ Ending stats generator executer: reports generation time = {} ms +++", time);
        }
    }
}
