package cronjobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.StatsConfig;
import model.StatsQueryResult;
import model.StatsTypes;
import org.alfresco.model.ContentModel;
import org.alfresco.rest.api.model.Assoc;
import org.alfresco.rest.api.model.Association;
import org.alfresco.rest.api.search.impl.SearchMapper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.*;
import org.alfresco.service.namespace.QName;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesToReadableConverter;
import utils.VenziaModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class StatsGeneratorExecuter implements StatefulJob {
    private static final Logger LOG = LoggerFactory.getLogger(StatsGeneratorExecuter.class);

    private ServiceRegistry serviceRegistry;
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("------ CIUDADANO -------");

        //get json of type venzia:statConfig
        ResultSet rs = searchStatsConfigs();
        List<StatsConfig> configs = new ArrayList<>();

        //parse each to statConfigObject
        rs.forEach(result -> {
            StatsConfig statsConfig = parseContentToStatConfig(result);
            configs.add(statsConfig);
        });

        //for each config launch search
        configs.forEach(config -> {
            JSONArray searchs = config.getQueries();
            List<StatsQueryResult> results = new ArrayList<>();
            searchs.forEach(searchDefinition -> {
                JSONObject searchObject = new JSONObject(searchDefinition.toString());
                StatsQueryResult searchResult = getResultsForQuery(searchObject);
                results.add(searchResult);
            });
            //for each config save in path
            String destinationFolderPath = config.getOutputPathFolder();
            ResultSet folderPathSearch = serviceRegistry.getSearchService().query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_FTS_ALFRESCO, "PATH:"+destinationFolderPath);
            if(folderPathSearch.length() != 0){
                NodeRef prevExist = serviceRegistry.getNodeService().getChildByName(folderPathSearch.getNodeRef(0), ContentModel.ASSOC_CONTAINS, config.getOutputName());
                if(prevExist == null){ //first creation
                    prevExist = serviceRegistry.getFileFolderService().create(folderPathSearch.getNodeRef(0), config.getOutputName(), VenziaModel.TYPE_STATS_REPORT).getNodeRef();
                }
                HashMap<String, Object> jsonOutput = new HashMap<>();
                jsonOutput.put("id", config.getId());
                ArrayList<Object> resultsArray = new ArrayList<>();
                results.forEach(result -> resultsArray.add(result.toHashmap()));
                jsonOutput.put("results", resultsArray);

                try {
                    NodeRef workingCopy;
                    if(!serviceRegistry.getCheckOutCheckInService().isCheckedOut(prevExist)){
                        workingCopy = serviceRegistry.getCheckOutCheckInService().checkout(prevExist);
                    }else{
                        workingCopy = serviceRegistry.getCheckOutCheckInService().getCheckedOut(prevExist);
                    }
                    serviceRegistry.getNodeService().setType(workingCopy, VenziaModel.TYPE_STATS_REPORT);
                    ContentWriter workingCopyWriter = serviceRegistry.getContentService().getWriter(workingCopy,ContentModel.PROP_CONTENT, true);
                    workingCopyWriter.putContent(new ObjectMapper().writeValueAsString(jsonOutput));
                    serviceRegistry.getCheckOutCheckInService().checkin(workingCopy, null);

                } catch (Exception e) {
                    System.out.println("--------------");
                    System.out.println(e.getMessage());
                    serviceRegistry.getLockService().unlock(prevExist); //if fails avoid node locking
                    throw new RuntimeException(e);
                }

            }

        });

    }

    /**Uses search service to search: TYPE:venzia:statsConfig*/
    public ResultSet searchStatsConfigs(){
        SearchParameters sp = new SearchParameters();
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        String query = "TYPE:\""+ VenziaModel.TYPE_STATS_CONFIG +"\"";
        sp.setQuery(query);
        sp.setMaxItems(1000);
        return serviceRegistry.getSearchService().query(sp);
    }

    /**Reads node content and parses as a statConfig object*/
    public StatsConfig parseContentToStatConfig(ResultSetRow result){
        ContentReader contentReader = serviceRegistry.getContentService().getReader(result.getNodeRef(), ContentModel.PROP_CONTENT);
        String contentString = contentReader.getContentString();
        StatsConfig config = new StatsConfig(contentString);
        return config;
    }

    /**Uses the query definition to parse and launch search service*/
    public StatsQueryResult getResultsForQuery(JSONObject searchObject){
        if(searchObject.getString("outputType").equals(StatsTypes.SIZE)){ //works diferent to other searches
           return getResultsForSizeQuery(searchObject);
        }
        SearchParameters sp = new SearchParameters();
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        String query = searchObject.getString("query");
        sp.setQuery(query);
        sp.setIncludeMetadata(false);
        searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
            String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
            sp.addFacetQuery(formatedQuery);
        });
        searchObject.getJSONArray("facetFields").forEach(facetField -> {sp.addFieldFacet(new SearchParameters.FieldFacet(facetField.toString()));});
        ResultSet outputObject = serviceRegistry.getSearchService().query(sp);
        StatsQueryResult queryResult = new StatsQueryResult(outputObject, searchObject);
        return  queryResult;
    }

    /**Receives Json object: {"query":"afts query", "label":"my label"}  and parses it into a search parameter correct format*/
    public String getFormatedFacetQuery(JSONObject facetQuery){
        if(facetQuery.getClass().equals(JSONObject.class)){
            String label = facetQuery.getString("label");
            String facetQ = facetQuery.getString("query");
            facetQ = "{!afts key='" + label + "'}" + facetQ;
            return facetQ;
        }
        return "";
    }

    /**Size query has a different logic, it launches a recursive solr search and populates statsqueryResult with a hashmap:{size:formated string , numOfItems: docs found}*/
    public StatsQueryResult getResultsForSizeQuery(JSONObject searchObject){
        if(searchObject.has("nodeId")){
            String id = searchObject.getString("nodeId");
            NodeRef ancestor = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
            if(!serviceRegistry.getNodeService().exists(ancestor)){throw new RuntimeException("Node not found");}
            long size;
            String formatedSize;
            long numOfDocs;
            QName nodeType = serviceRegistry.getNodeService().getType(ancestor);
            if(nodeType.equals(ContentModel.PROP_CONTENT)){
                ContentData cd = (ContentData) serviceRegistry.getNodeService().getProperty(ancestor, ContentModel.PROP_CONTENT);
                size = cd.getSize();
                formatedSize = BytesToReadableConverter.getReadableSize(size);
                numOfDocs = 1;
                return new StatsQueryResult(searchObject, formatedSize, numOfDocs);
            }
             int pagination = searchObject.getInt("pagination");
             size = getNodeSizeWithSolr(ancestor, 0, pagination);
             formatedSize = BytesToReadableConverter.getReadableSize(size);
             numOfDocs = getNodeAncestorChilds(ancestor);
            return new StatsQueryResult(searchObject, formatedSize, numOfDocs);
        }else {
            throw new RuntimeException("Missing argument nodeId");
        }
    }


    /**For test size methods*/
    public void getNodeSizeWithChildrens(){
        System.out.println("------- GET NODE SIZE WITH CHILDRENS ------");
        System.out.println("++ test Node  company home +++");
        NodeRef testNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,"2ecd2808-1afd-4574-8d28-081afd95740c");
        long startTimeNodeService = System.currentTimeMillis();
        long size = getNodeSizeWithNodeService(testNodeRef);
        long endTimeNodeService = System.currentTimeMillis();
        System.out.println("size by listing childrens: " + BytesToReadableConverter.getSizeInGb(size) + "gb");
        System.out.println("time duration: " + (endTimeNodeService - startTimeNodeService) + " ms");

        long startTimeSolr = System.currentTimeMillis();
        long sizeWithSolr = getNodeSizeWithSolr(testNodeRef,0,5000);
        long endTimeSolr = System.currentTimeMillis();

        System.out.println("size by listing with solr query " + BytesToReadableConverter.getSizeInGb(sizeWithSolr) + "gb");
        System.out.println("time duration: " + (endTimeSolr - startTimeSolr) + " ms");

    }

    /**@deprecated
     * slower, gives a higher value
     * in test for 424 docs this gives a time duration of 298ms and a size of 16.8mb
     * */
    public long getNodeSizeWithNodeService(NodeRef nodeRef){
        long size = 0;
        ContentData content = (ContentData) serviceRegistry.getNodeService().getProperty(nodeRef, ContentModel.PROP_CONTENT);
        try {
            size = content.getSize();
        } catch (Exception e) {
            size=0;
        }

        List<ChildAssociationRef> childs = serviceRegistry.getNodeService().getChildAssocs(nodeRef);
        for(ChildAssociationRef child:childs){
            size = size + getNodeSizeWithNodeService(child.getChildRef());
        }
        return size;
    }

    /**faster, gives a lower value
     * in test for 424 docs this gives a time duration of 89ms and a size of 14.8mb,on average, speed is like half the nodeservice approach
     * */
    public long getNodeSizeWithSolr(NodeRef nodeRef, int skipCount, int length){
        SearchParameters sp = new SearchParameters();
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        sp.setQuery("ANCESTOR:\"" + nodeRef.toString() + "\"");
        sp.setMaxItems(length);
        sp.setSkipCount(skipCount);
        sp.setIncludeMetadata(false);
        long size = 0;
        ResultSet results = serviceRegistry.getSearchService().query(sp);
        for(ResultSetRow rs : results){
            if(rs.getValue(ContentModel.PROP_CONTENT) instanceof ContentData){
                ContentData cd = (ContentData) rs.getValue(ContentModel.PROP_CONTENT);
                size = size + cd.getSize();
            }
        }
        if(results.hasMore()){
            size = size + getNodeSizeWithSolr(nodeRef, skipCount+length, length);
        }
        return size;
    }

    /**query only to know how many results are*/
    public long getNodeAncestorChilds(NodeRef nodeRef){
        SearchParameters sp = new SearchParameters();
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        sp.setQuery("ANCESTOR:\"" + nodeRef.toString() + "\"");
        sp.setMaxItems(1);
        sp.setIncludeMetadata(false);
        return  serviceRegistry.getSearchService().query(sp).getNumberFound();
    }

}
