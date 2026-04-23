package model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Each query in the configs will be launched, and it results saved as StatQueryResult Class
 * this class provides helper methods to output the result in a json format and extract and parse results
 * to a readable and manageable output
 * */
public class StatsQueryResult {
    /**Search results*/
    ResultSet results; //search results

    /**Output resultset when outputType is of type size*/
    HashMap<String,String> sizeResultSet;

    /**Search result when output is of type timeGraph*/
    List<TimeFacetedSearchResultSet> timeResultSets;

    /**resultNumber || numberGraph || timeGraph|| size */
    String outputType;
    String outputLabel;
    String outputSublabel;
    String outputIcon;
    String outputIconColor;
    String outputCardbgColor;
    String outputTextColor;
    /**In number graph type it will export to json the response of facet queries instead of field buckets*/
    boolean hasFacetQueries;
    /**Field to export from the buckets list*/
    String fieldOutput;
    /**Original config of the query to*/
    JSONObject searchConfig;


    public StatsQueryResult(ResultSet results, String outputType, String outputLabel) {
        this.results = results;
        this.outputType = outputType;
        this.outputLabel = outputLabel;
    }

    /**Default constructor*/
    public StatsQueryResult(ResultSet results, JSONObject searchObject){
        setCommonProperties(searchObject);
        this.results = results;
        this.searchConfig = searchObject;
        hasFacetQueries = searchObject.has("hasFacetQueries") ? searchObject.getBoolean("hasFacetQueries") : false;
        fieldOutput = searchObject.has("fieldOutput") ? searchObject.getString("fieldOutput") : "";
    }

    /**Constructor invoked when outputType is of type size */
    public StatsQueryResult(JSONObject searchObject, String size, long childs){
        setCommonProperties(searchObject);
        sizeResultSet = new HashMap<>();
        sizeResultSet.put("\"size\"", "\""+size+"\"");
        sizeResultSet.put("\"numOfFounds\"", "\""+String.valueOf(childs)+"\"");
    }

    /**Constructor for time graph type*/
    public StatsQueryResult(List<TimeFacetedSearchResultSet> results, JSONObject searchObject){
        setCommonProperties(searchObject);
        this.timeResultSets = results;
        this.searchConfig = searchObject;
        hasFacetQueries = searchObject.has("hasFacetQueries") ? searchObject.getBoolean("hasFacetQueries") : false;
        fieldOutput = searchObject.has("fieldOutput") ? searchObject.getString("fieldOutput") : "";
    }

    /**Extracts properties for all kind of graphs, labels, colors etc*/
    public void setCommonProperties(JSONObject searchObject){
        outputType = searchObject.getString("outputType");
        outputLabel = searchObject.getString("outputLabel");
        outputIcon = searchObject.has("outputIcon") ? searchObject.getString("outputIcon") : "";
        outputIconColor = searchObject.has("outputIconColor") ? searchObject.getString("outputIconColor") : "";
        outputCardbgColor =  searchObject.has("outputCardbgColor") ? searchObject.getString("outputCardbgColor") : "";
        outputTextColor =  searchObject.has("outputTextColor") ? searchObject.getString("outputTextColor") : "";
        outputSublabel =  searchObject.has("outputSublabel") ? searchObject.getString("outputSublabel") : "";
    }

    public ResultSet getResults() {
        return results;
    }

    public void setResults(ResultSet results) {
        this.results = results;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getOutputLabel() {
        return outputLabel;
    }

    public void setOutputLabel(String outputLabel) {
        this.outputLabel = outputLabel;
    }

    /**Method invokeD to extract object values as a entry of the final stat json*/
    public HashMap<String, Object> toHashmap(){
        HashMap<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("outputType", getOutputType());
        jsonOutput.put("outputLabel", getOutputLabel());
        jsonOutput.put("hasFacetQueries", hasFacetQueries);
        jsonOutput.put("fieldOutput", fieldOutput);
        jsonOutput.put("results", getResultsByType());
        jsonOutput.put("outputIcon", outputIcon);
        jsonOutput.put("outputIconColor", outputIconColor);
        jsonOutput.put("outputCardbgColor", outputCardbgColor);
        jsonOutput.put("outputTextColor", outputTextColor);
        jsonOutput.put("outputSublabel", outputSublabel);
        return jsonOutput;
    }

    /**Parses result to string depending on output type*/
    public String getResultsByType(){
        if(outputType.equals(StatsTypes.RESULT_NUMBER)){
            return String.valueOf(results.getNumberFound());
        }else if(outputType.equals(StatsTypes.NUMBER_GRAPH) || outputType.equals(StatsTypes.DOUGHNUT_CHART)){
            JSONArray chartResults = new JSONArray();

            if(!hasFacetQueries){ //output contains result from fieldFacet
                List<Pair<String, Integer>> fieldsBuckets = results.getFieldFacet(fieldOutput);
                fieldsBuckets.forEach(pair -> {
                    JSONArray point = new JSONArray();
                    point.put(pair.getFirst());
                    point.put(pair.getSecond());
                    chartResults.put(point);
                });
            }else {//output contains results from facetQueries
                Map<String, Integer> facetQueries = results.getFacetQueries();
                for(Map.Entry<String,Integer> facet:facetQueries.entrySet()){
                    JSONArray point = new JSONArray();
                    point.put(facet.getKey());
                    point.put(facet.getValue());
                    chartResults.put(point);
                }
            }

            return chartResults.toString();
        }else if(outputType.equals(StatsTypes.TIME_GRAPH)){
            JSONArray facetQueriesArr = new JSONArray();
            timeResultSets.forEach(timeFacetedSearchResultSet -> {
                JSONObject timeResultAsJsonObject = timeFacetedSearchResultSet.toJSONObject(searchConfig);
                facetQueriesArr.put(timeResultAsJsonObject);
            });
            return facetQueriesArr.toString();
        }else if(outputType.equals(StatsTypes.SIZE)){
            return sizeResultSet.toString();
        }
        return "";
    }
}
