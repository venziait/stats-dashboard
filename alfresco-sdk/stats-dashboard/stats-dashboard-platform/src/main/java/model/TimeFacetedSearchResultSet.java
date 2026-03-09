package model;

import org.alfresco.service.cmr.search.ResultSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**Objet that stores resultSets of a faceted search, as json is:
 *  {filter:cm:created, label: admin, resultSet: resusltet for a query with cm:creator=admin filter},
 * */
public class TimeFacetedSearchResultSet {
    String filter;
    String label;
    ResultSet facetResultSet;

    public TimeFacetedSearchResultSet(String filter, String label, ResultSet facetResultSet) {
        this.filter = filter;
        this.label = label;
        this.facetResultSet = facetResultSet;
    }
    /**Returns data as JSONobject
     * Uses searchconfig to get the order for each result, ouput as:
     *   {
     *   "today":{"count":2, "order":0, "label":"admin", filter:"cm:created"},
     *   "yesterday":{"count":2, "order":0, "label":"admin",filter:"cm:created"}}
     *   }
     * */
    public JSONObject toJSONObject(JSONObject searchConfig){
        JSONObject jsonOutput = new JSONObject();
        for(Object facet: searchConfig.getJSONArray("facetQueries")){//all configs
            if(facet.getClass().equals(JSONObject.class)){
                int order = ((JSONObject) facet).getInt("order");
                String facetLabel = ((JSONObject) facet).getString("label");
                int count = facetResultSet.getFacetQueries().getOrDefault(facetLabel, 0);
                JSONObject facetContent = new JSONObject().put("count", count).put("order", order).put("label", label).put("filter", filter);
                jsonOutput.put(facetLabel, facetContent);
            }
        }
        return  jsonOutput;
    }

    @Override
    public String toString() {
        return "TimeFacetedSearchResultSet{" +
                "filter='" + filter + '\'' +
                ", label='" + label + '\'' +
                ", facetResultSet=" + facetResultSet +
                '}';
    }
}
