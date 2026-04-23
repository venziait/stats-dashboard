package services;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.StatsConfig;
import model.StatsQueryResult;
import model.StatsTypes;
import model.TimeFacetedSearchResultSet;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.ResultSetRow;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;

import org.alfresco.util.ISO9075;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.BytesToReadableConverter;
import utils.VenziaModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper methods used by StatGeneratorExecuter class
 * */
public class StatGeneratorHelper {
    private ServiceRegistry serviceRegistry;
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Uses search service to search: TYPE:venzia:statsConfig
     * */
    public ResultSet searchStatsConfigs(){
        SearchParameters sp = getDefaultSearchParameters();
        String query = "TYPE:\""+ VenziaModel.TYPE_STATS_CONFIG +"\" AND -ASPECT:\"sys:archived\"";
        sp.setQuery(query);
        sp.setMaxItems(1000);
        return serviceRegistry.getSearchService().query(sp);
    }

    /**Returns the basic search parameter object*/
    public SearchParameters getDefaultSearchParameters(){
        SearchParameters sp = new SearchParameters();
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        sp.setIncludeMetadata(false);
        return sp;
    }

    /**
     * Uses the query definition to parse and launch search service
     * */
    public StatsQueryResult getResultsForQuery(JSONObject searchObject){
        if(searchObject.getString("outputType").equals(StatsTypes.SIZE)){ //works different to other searches
            return getResultsForSizeQuery(searchObject);
        }else if(searchObject.getString("outputType").equals(StatsTypes.TIME_GRAPH)){//handle faceting
            return getResultsForTimeQuery(searchObject);
        }
        //default search for other types
        SearchParameters sp = getDefaultSearchParameters();
        String query = expandWellKnownAliasesInQuery(searchObject.getString("query"));
        sp.setQuery(query);
        searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
            String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
            sp.addFacetQuery(formatedQuery);
        });
        searchObject.getJSONArray("facetFields").forEach(facetField -> {sp.addFieldFacet(new SearchParameters.FieldFacet(facetField.toString()));});
        ResultSet outputObject = serviceRegistry.getSearchService().query(sp);
        StatsQueryResult queryResult = new StatsQueryResult(outputObject, searchObject);
        return  queryResult;
    }

    /**
     * Reads node content and parses as a statConfig object
     * */
    public StatsConfig parseContentToStatConfig(ResultSetRow result){
        ContentReader contentReader = serviceRegistry.getContentService().getReader(result.getNodeRef(), ContentModel.PROP_CONTENT);
        String contentString = contentReader.getContentString();
        StatsConfig config = new StatsConfig(contentString);
        return config;
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

    /**
     * faster, gives a lower value
     * in test for 424 docs this gives a time duration of 89ms and a size of 14.8mb,on average, speed is like half the nodeservice approach
     * */
    public long getNodeSizeWithSolr(NodeRef nodeRef, int skipCount, int length){
        SearchParameters sp = getDefaultSearchParameters();
        sp.setQuery("ANCESTOR:\"" + nodeRef.toString() + "\"");
        sp.setMaxItems(length);
        sp.setSkipCount(skipCount);
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
        SearchParameters sp = getDefaultSearchParameters();
        sp.setQuery("ANCESTOR:\"" + nodeRef.toString() + "\"");
        sp.setMaxItems(1);
        return  serviceRegistry.getSearchService().query(sp).getNumberFound();
    }

    /**Time search can be in 3 ways:<br>
     * 1. a facet search, for example: documents created in the last week<br>
     * 2. a facet GROUPED search, for example: documents created in the last week by creator, this is indicated by the field parameter in jsonConfig, only first field will be used<br>
     * 3. a facet LIMITED search,this is when we know which filters we want,  in this case we use filterQuery in config and add a set of filterquerys, result will be like option 2
     * but with this query instead of a automatic filter query: for example: documents created in the last week BY Site with [SITE:swdsp]<br>
     * <br>IN the first case just a normal faceted search is launched, in the second case first  a fields query is launched. With that query we retrieve the diferent values for
     * the property, for example: creator. The for each creator we launch a faceted search adding that value as a filterquery
     * in both cases statsQueryResult will have a prop timeFacetResultSearch
     * <br><code>
     * [ {filter: filter, label: filter label, resultSet:SearchResultset} ,{label: filter label, resultSet:SearchResultset} ]</code>
     * <br>so, for a creator query the resultset would be:
     * <br><code>[
     *   {filter:cm:creator, label: admin, resultSet: resusltet for a query with cm:creator=admin filter},
     *   {filter:cm:creator, label: testUster, resultSet: resusltet for a query with cm:creator=testUser filter}
     * ]</code>
     * <br>If there is no filter filter will be empty and label will be outputLabel
     * */
    public StatsQueryResult getResultsForTimeQuery(JSONObject searchObject){
        List<TimeFacetedSearchResultSet> timeFacetedSearchResultSets = new ArrayList<>();
        SearchParameters sp = getDefaultSearchParameters();
        String query = expandWellKnownAliasesInQuery(searchObject.getString("query"));
        sp.setQuery(query);
        //-- if there is no field nor filter query
        boolean hasFilterQueries= searchObject.has("filterQueries") || (searchObject.has("facetFields") && !searchObject.getJSONArray("facetFields").isEmpty());
        if(!hasFilterQueries){ //normal search with facet
            searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
                String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
                sp.addFacetQuery(formatedQuery);
            });
            searchObject.getJSONArray("facetFields").forEach(facetField -> {sp.addFieldFacet(new SearchParameters.FieldFacet(facetField.toString()));});
            ResultSet outputObject = serviceRegistry.getSearchService().query(sp);
            TimeFacetedSearchResultSet timeFacetedSearchResultSet = new TimeFacetedSearchResultSet("", searchObject.getString("outputLabel"), outputObject);
            timeFacetedSearchResultSets.add(timeFacetedSearchResultSet);

        }else{ //if there are filterqueries it will ignore facetfields
            if(!searchObject.has("filterQueries")){//build results using facetFields as filters
                searchObject.getJSONArray("facetFields").forEach(facetField -> {sp.addFieldFacet(new SearchParameters.FieldFacet(facetField.toString()));});
                searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
                    String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
                    sp.addFacetQuery(formatedQuery);
                });
                ResultSet outputObject = serviceRegistry.getSearchService().query(sp);
                String fieldFacet = searchObject.getJSONArray("facetFields").getString(0);
                outputObject.getFieldFacet(fieldFacet).forEach( (pair) -> { //uses facet field buckets to relaunch the search but using the bucket as a filter
                    SearchParameters fieldSP = getDefaultSearchParameters();
                    fieldSP.setQuery(query);
                    searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
                        String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
                        fieldSP.addFacetQuery(formatedQuery);
                    });
                    fieldSP.addFilterQuery(fieldFacet+":\""+pair.getFirst()+"\"");
                    ResultSet fieldRS = serviceRegistry.getSearchService().query(fieldSP);
                    TimeFacetedSearchResultSet timeFacetedSearchResultSet = new TimeFacetedSearchResultSet(fieldFacet, pair.getFirst(), fieldRS);
                    timeFacetedSearchResultSets.add(timeFacetedSearchResultSet);
                });
            }else{
                JSONArray filterQueries = searchObject.getJSONArray("filterQueries");
                filterQueries.forEach(filter -> {//build results using filterqueries as filter
                    SearchParameters filterSP = getDefaultSearchParameters();
                    filterSP.setQuery(query);
                    searchObject.getJSONArray("facetQueries").forEach(facetQuery -> {
                        String formatedQuery = getFormatedFacetQuery( (JSONObject) facetQuery);
                        filterSP.addFacetQuery(formatedQuery);
                    });
                    if(filter.getClass().equals(JSONObject.class)){
                        filterSP.addFilterQuery(expandWellKnownAliasesInQuery(((JSONObject) filter).getString("query")));
                        ResultSet filterRS = serviceRegistry.getSearchService().query(filterSP);
                        TimeFacetedSearchResultSet timeFacetedSearchResultSet = new TimeFacetedSearchResultSet(((JSONObject) filter).getString("query"), ((JSONObject) filter).getString("label"),filterRS);
                        timeFacetedSearchResultSets.add(timeFacetedSearchResultSet);
                    }
                });
            }
        }
        StatsQueryResult queryResult = new StatsQueryResult(timeFacetedSearchResultSets, searchObject);
        return  queryResult;
    }


    /**
     * Size query has a different logic, it launches a recursive solr search and populates statsqueryResult with a hashmap:{size:formated string , numOfItems: docs found}
     * */
    public StatsQueryResult getResultsForSizeQuery(JSONObject searchObject){
        if(searchObject.has("nodeId")){
            String id = searchObject.getString("nodeId");
            NodeRef ancestor = resolveWellKnownAliasNodeRef(id);
            if (ancestor == null) {
                ancestor = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
            }
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
            size = getNodeSizeWithSolr(ancestor, 0, pagination);//you can change this call with a call to getNodeSizeWithNodeService if you prefer
            formatedSize = BytesToReadableConverter.getReadableSize(size);
            numOfDocs = getNodeAncestorChilds(ancestor);
            return new StatsQueryResult(searchObject, formatedSize, numOfDocs);
        }else {
            throw new RuntimeException("Missing argument nodeId");
        }
    }

    /**
     * Receives Json object: {"query":"afts query", "label":"my label"}  and parses it into a search parameter correct format
     * */
    public String getFormatedFacetQuery(JSONObject facetQuery){
        if(facetQuery.getClass().equals(JSONObject.class)){
            String label = facetQuery.getString("label");
            String facetQ = expandWellKnownAliasesInQuery(facetQuery.getString("query"));
            facetQ = "{!afts key='" + label + "'}" + facetQ;
            return facetQ;
        }
        return "";
    }

    /**
     * Expands well-known aliases used in stat queries.
     */
    public String expandWellKnownAliasesInQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        String expanded = query;
        expanded = expanded.replace("-root-", "/app:company_home");
        expanded = expanded.replace("-shared-", "/app:company_home/cm:Shared");

        String runAsUser = AuthenticationUtil.getRunAsUser();
        if (runAsUser != null && !runAsUser.isEmpty()) {
            expanded = expanded.replace("-my-", "/app:company_home/cm:User_x0020_Homes/cm:" + ISO9075.encode(runAsUser));
        }

        return expanded;
    }

    /**
     * Resolves well-known aliases when used as nodeId values.
     */
    public NodeRef resolveWellKnownAliasNodeRef(String nodeIdOrAlias) {
        if ("-root-".equals(nodeIdOrAlias)) {
            return findNodeByPath("/app:company_home");
        }
        if ("-shared-".equals(nodeIdOrAlias)) {
            return findNodeByPath("/app:company_home/cm:Shared");
        }
        if ("-my-".equals(nodeIdOrAlias)) {
            String runAsUser = AuthenticationUtil.getRunAsUser();
            if (runAsUser == null || runAsUser.isEmpty()) {
                return null;
            }
            return findNodeByPath("/app:company_home/cm:User_x0020_Homes/cm:" + ISO9075.encode(runAsUser));
        }
        return null;
    }

    private NodeRef findNodeByPath(String path) {
        ResultSet resultSet = serviceRegistry.getSearchService().query(
            StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
            SearchService.LANGUAGE_FTS_ALFRESCO,
            buildPathQuery(path)
        );
        if (resultSet.length() == 0) {
            return null;
        }
        return resultSet.getNodeRef(0);
    }


    /**
     * Receives the resulting result search from searching the destination folder by path,
     * the statconfig object and the results
     * Gets or create the output json file and writes the output in it
     * */
    public void saveJsonInPath(ResultSet folderPathSearch, StatsConfig config, Map<String, Object> jsonOutput){
        NodeRef prevExist = serviceRegistry.getNodeService().getChildByName(folderPathSearch.getNodeRef(0), ContentModel.ASSOC_CONTAINS, config.getOutputName());
        if(prevExist == null){ //first creation
            prevExist = serviceRegistry.getFileFolderService().create(folderPathSearch.getNodeRef(0), config.getOutputName(), VenziaModel.TYPE_STATS_REPORT).getNodeRef();
        }
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
            e.printStackTrace();
            serviceRegistry.getLockService().unlock(prevExist); //if fails avoid node locking
            throw new RuntimeException(e);
        }
    }

    /**
     * Ensures the configured output path exists. Missing folders are created under Company Home.
     * Returns a search result pointing to the target destination folder.
     */
    public ResultSet ensureOutputPathExists(String destinationFolderPath) {
        String normalizedPath = normalizePath(destinationFolderPath);
        ResultSet folderPathSearch = serviceRegistry.getSearchService().query(
            StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
            SearchService.LANGUAGE_FTS_ALFRESCO,
            buildPathQuery(normalizedPath)
        );

        if (folderPathSearch.length() != 0) {
            return folderPathSearch;
        }

        if (!normalizedPath.startsWith("/app:company_home")) {
            throw new RuntimeException("Output path must start with /app:company_home. Received: " + destinationFolderPath);
        }

        ResultSet companyHomeSearch = serviceRegistry.getSearchService().query(
            StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
            SearchService.LANGUAGE_FTS_ALFRESCO,
            "PATH:\"/app:company_home\""
        );

        if (companyHomeSearch.length() == 0) {
            throw new RuntimeException("Could not resolve /app:company_home while creating output path: " + destinationFolderPath);
        }

        NodeRef current = companyHomeSearch.getNodeRef(0);
        String relativePath = normalizedPath.substring("/app:company_home".length());
        String[] segments = relativePath.split("/");

        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }

            String[] namespaceAndName = segment.split(":", 2);
            String encodedName = namespaceAndName.length == 2 ? namespaceAndName[1] : namespaceAndName[0];
            String folderName = ISO9075.decode(encodedName);

            NodeRef child = serviceRegistry.getNodeService().getChildByName(current, ContentModel.ASSOC_CONTAINS, folderName);
            if (child == null) {
                child = serviceRegistry.getFileFolderService().create(current, folderName, ContentModel.TYPE_FOLDER).getNodeRef();
            }
            current = child;
        }

        ResultSet createdPathSearch = serviceRegistry.getSearchService().query(
            StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
            SearchService.LANGUAGE_FTS_ALFRESCO,
            buildPathQuery(normalizedPath)
        );

        if (createdPathSearch.length() == 0) {
            throw new RuntimeException("Output path could not be created: " + destinationFolderPath);
        }

        return createdPathSearch;
    }

    private String normalizePath(String destinationFolderPath) {
        String normalizedPath = destinationFolderPath;
        if (normalizedPath == null || normalizedPath.trim().isEmpty()) {
            throw new RuntimeException("Output path is empty");
        }

        normalizedPath = normalizedPath.trim();
        if (normalizedPath.startsWith("'") && normalizedPath.endsWith("'")) {
            normalizedPath = normalizedPath.substring(1, normalizedPath.length() - 1);
        }

        normalizedPath = normalizedPath.replace("\\/", "/");
        return normalizedPath;
    }

    private String buildPathQuery(String normalizedPath) {
        return "PATH:\"" + normalizedPath + "\"";
    }
}
