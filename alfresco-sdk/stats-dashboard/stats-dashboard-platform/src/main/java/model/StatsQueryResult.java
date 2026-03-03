package model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.repo.search.impl.querymodel.impl.functions.In;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.util.Pair;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsQueryResult {
    /**Search results*/
    ResultSet results; //search results
    /**resultNumber || numberGraph || size */
    String outputType;

    String outputLabel;
    /**In number graph type it will export to json the response of facet queries instead of field buckets*/
    boolean hasFacetQueries;
    /**Field to export from the buckets list*/
    String fieldOutput;

    /**Output resultset when outputType is of type size*/
    HashMap<String,String> sizeResultSet;
    public StatsQueryResult(ResultSet results, String outputType, String outputLabel) {
        this.results = results;
        this.outputType = outputType;
        this.outputLabel = outputLabel;
    }

    public StatsQueryResult(ResultSet results, JSONObject searchObject){
        this.results = results;
        outputType = searchObject.getString("outputType");
        outputLabel = searchObject.getString("outputLabel");
        hasFacetQueries = searchObject.has("hasFacetQueries") ? searchObject.getBoolean("hasFacetQueries") : false;
        fieldOutput = searchObject.has("fieldOutput") ? searchObject.getString("fieldOutput") : "";
    }

    /**Constructor invoked when outputType is of type size */
    public StatsQueryResult(JSONObject searchObject, String size, long childs){
        outputType = searchObject.getString("outputType");
        outputLabel = searchObject.getString("outputLabel");
        sizeResultSet = new HashMap<>();
        sizeResultSet.put("size", size);
        sizeResultSet.put("numOfFounds", String.valueOf(childs));
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
                Map<String, Integer> facetQueries = results.getFacetQueries();
                return facetQueries.toString();
            }
        }else if(outputType.equals(StatsTypes.TIME_GRAPH)){
            //timegraph uses temporal facets queries just like numbergraph but facets are now always by date
            //TODO: Handle facet order to show correctly in front
            Map<String, Integer> facetQueries = results.getFacetQueries();
            return facetQueries.toString();
        }else if(outputType.equals(StatsTypes.SIZE)){
            return sizeResultSet.toString();
        }
        return "";
    }
}
