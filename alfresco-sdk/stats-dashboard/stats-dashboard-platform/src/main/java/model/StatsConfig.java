package model;

import org.json.JSONArray;
import org.json.JSONObject;

/**Model for stat config, receives the json and parses it to java object
 * Input json is:<br>
 * <pre>
 *{@code
 {
     "id": "global stats",
     "queries":[
         {
             "query": "cm:creator:admin",
             "facetQueries":[],
             "facetFields":["field:creator"],
             "outputType": "resultNumber",
             "outputLabel": "documentos creados por admin"
         }
        ],
     "outputPath": "cm:contentrepository/myfolder"
    }
 }
 </pre>
 * */
public class StatsConfig {
    String id;
    String outputPathFolder;
    String outputName;
    JSONArray queries;
    public StatsConfig(String json) {
        JSONObject jsonObject = new JSONObject(json);
        setId(jsonObject.getString("id"));
        setOutputPathFolder(jsonObject.getString("outputPathFolder"));
        setOutputName(jsonObject.getString("outputName"));
        setQueries(jsonObject.getJSONArray("queries"));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputPathFolder() {
        return outputPathFolder;
    }

    public void setOutputPathFolder(String outputPathFolder) {
        this.outputPathFolder = outputPathFolder;
    }

    public JSONArray getQueries() {
        return queries;
    }

    public void setQueries(JSONArray queries) {
        this.queries = queries;
    }

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }
}
