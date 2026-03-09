package model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsQueryResult {
    /**Search results*/
    ResultSet results; //search results
    /**Output resultset when outputType is of type size*/
    HashMap<String,String> sizeResultSet;

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
    JSONObject searchConfig;


    public StatsQueryResult(ResultSet results, String outputType, String outputLabel) {
        this.results = results;
        this.outputType = outputType;
        this.outputLabel = outputLabel;
    }

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


    @Deprecated(since = "use to hashmap instead")
    public String toJson() throws JsonProcessingException {
        HashMap<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("outputType", getOutputType());
        jsonOutput.put("outputLabel", getOutputLabel());
        jsonOutput.put("results", getResultsByType());
        return new ObjectMapper().writeValueAsString(jsonOutput);
    }


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
        }else if(outputType.equals(StatsTypes.NUMBER_GRAPH)){
            if(!hasFacetQueries){
                List<Pair<String, Integer>> fieldsBuckets = results.getFieldFacet(fieldOutput);
                fieldsBuckets.forEach(pair  -> pair.setFirst("\""+pair.getFirst()+"\"")); //escaped commas for parsing with (JSON.parse(input.results[0].results)
                return fieldsBuckets.toString().replace("(", "[").replace(")", "]");
            }else {
                //output object
                Map<String, Integer> facetQueries = results.getFacetQueries();
                List<Pair<String, Integer>> toPairs = new ArrayList<>();
                for(Map.Entry<String,Integer> facet:facetQueries.entrySet()){
                    toPairs.add(new Pair<>("\""+facet.getKey()+"\"", facet.getValue()));
                }
                return toPairs.toString().replace("(", "[").replace(")", "]");
            }
        }else if(outputType.equals(StatsTypes.TIME_GRAPH)){
            JSONArray facetQueriesArr = new JSONArray();
            timeResultSets.forEach(timeFacetedSearchResultSet -> {
                JSONObject timeResultAsJsonObject = timeFacetedSearchResultSet.toJSONObject(searchConfig);
                facetQueriesArr.put(timeResultAsJsonObject);
            });
            System.out.println(facetQueriesArr);
            return facetQueriesArr.toString();
        }else if(outputType.equals(StatsTypes.SIZE)){
            return sizeResultSet.toString();
        }
        return "";
    }
}
