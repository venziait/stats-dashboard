# Venzia stat dashboard
Stat dashboard provides a easy-configurable way to generate and show alfresco statistics
![image](images/dashboard-preview.png)

## Repo Structure
The project consist in 2 folders:

**alfresco-sdk**: Contains the stat generation logic

**alfresco-content-app**: stats display

## Stat generation cron
Stat generation is made through a cronjob. The main reason to this instead live-launched searchs is to avoid overwhelming and innecesary requests.
Cronjob periodicity is controlled by the property: `venzia.stats.generator.cronexpression`.
For dev purposes currently is executed each 2 mins. **Set a periodicity that don't impact your repository performance**
Cronjob can be launched from [admin tools](http://localhost:8080/alfresco/s/ootbee/admin/scheduled-jobs#) with the taskname `com.venzia.cronjobs.StatsGeneratorJobDetail`.

## Stat config
Stat configuration is done throug json files that live in the repository. This json file should be of type `venzia:statconfig`. Each json file has 1 or more search config. The cron will parse JSON files, launch each request and generate a json file with the results. Several config json file are provided in [stats-configs folder](alfresco-sdk/stats-dashboard/stats-dashboard-platform/src/main/resources/stats-configs)

json files have this structure:
```JSON
{
    "id":"unique id of that stat config",
    "outputPathFolder":"path in which save the stat report, for example: '\\/app:company_home\\/cm:global-reports'",
    "outputName": "name of the output file: for example: numberGraph-report.json"
    "queries":[]
}
```
Output jsons will be saved in the configured path with document type `venzia:statsReport`

You can split your stats in several json config (for example one config per site) and scatter the json output.
For example each site will have the folder statConfigs and statReports. This way you can easily manage, edit, and delete configs without restarts. This is usefull to manage stat permisions, only users with access to that folder will see the stat in their dashboard.

## Stat types
In the configuration each query has a type. This type impacts the way the query is done and the way its rendered.
A basic query has this structure:
```JSON
{
      "query": "cm:creator:admin",
      "facetQueries":[],
      "facetFields":[],
      "outputType": "resultNumber",
      "outputLabel": "Documentos created by admin",
      "outputIcon": "dashboard",
      "outputIconColor": "#212121",
      "outputCardbgColor": "#fffffa",
      "outputTextColor": "#0000008a"
    },
```
Some Types have different fields but the following are common for all
 
|field name | required |description
|-----------|-------------|-------|
|query|true| base query to launch in afts format |
|   outputType|true| type of the query, allowed values `resultNumber,numberGraph,timeGraph,size`|
|outputLabel|true|label for stat display|
|hasFacetQueries|false|in type numbergraph query will obtain its result from facetqueries instead fieldFacets|
|outputIcon|false|mat icon to display in output, default dashboard |
|outputIconColor|false|color of the icon, default black|
|outputCardbgColor|false|background color of the display card, default white|
|outputTextColor|false|color of the card text, default black
|outputSublabel|false| for size queries, sublabel for num of docs value|

## Result number type
outputType value `resultNumber`

it will launch the query and return the number of founds
![image](images/resultNumber-preview.png)

## Size type
outputType value `size`

mandatory parameters
```JSON
{
    "nodeId": "2ecd2808-1afd-4574-8d28-081afd95740c",
    "pagination": 1000
}
```

The following well known Alfresco aliases are also supported in place of a nodeId.

```sh
-root-
-shared-
-my-
```

![image](images/size-preview.png)


It will launch a recursive search to find the weight of the indicated nodeId, if nodeId is a document it will return its size. If its a folder it will travel its tree to get the combined size and number of chldrens and grandchildren.

Pagination indicates the max-items in the batches processing while traversing the child nodes. 

> **Warning: Recursive size calculation can be a exigent process, avoid to much size stats and using it over very big folders. After adding a size stat always launch the cron and check if performance is impacted**

## numberGraph type
output type value `numberGraph`.
Displays a bar chart. A basic configuration should be: show me a graph of documents by its creator
```
    {
      "query": "TYPE:'cm:content'",
      "facetQueries":[],
      "facetFields":["creator"],
      "hasFacetQueries": false,
      "fieldOutput": "creator",
      "outputType": "numberGraph",
      "outputLabel": "documents created by User"
    }
```
Field output and facetFields should be the same. 
![image](images/numberGraphBasic-preview.png)
You can use facetQueries instead facetFields. If you add facetQueries, fieldOutput will be ignored. The next config outputs a documents by time chart
```
 {
      "query": "* AND TYPE:'cm:content' AND -creator:System",
      "facetQueries":[
        {"query": "created:[NOW-1DAY TO NOW]", "label": "today"},
        {"query": "created:[NOW-7DAY TO NOW]", "label": "last week"},
        {"query": "created:[NOW/YEAR TO NOW]", "label": "last year"},
        {"query": "created:[NOW-20YEAR TO NOW]", "label": "last 20 years"}
      ],
      "facetFields":["created"],
      "hasFacetQueries": true,
      "fieldOutput": "creator",
      "outputType": "numberGraph",
      "outputLabel": "documents created by date"
    }
```
![image](images/numberGraphFacets-preview.png)


## timeGraph Type
outputType `timeGraph`
Time graph is a bit more complex as it generates several datasets. There are 3 ways in which you can configure queries:

1. a facet search, for example: documents created in last week.  
```json
    {
      "query": "* AND TYPE:'cm:content' AND -creator:System",
      "facetQueries":[
        {"query": "created:[NOW-1DAY TO NOW]", "label": "today", "order": 5},
        {"query": "created:[NOW-2DAY TO NOW-1DAY]", "label": "yesterday" , "order": 4},
        {"query": "created:[NOW-3DAY TO NOW-2DAY]", "label": "2 days ago" , "order": 3},
        {"query": "created:[NOW-4DAY TO NOW-3DAY]", "label": "3 days ago" , "order": 2},
        {"query": "created:[NOW-5DAY TO NOW-4DAY]", "label": "4 days ago", "order": 1},
        {"query": "created:[NOW-6DAY TO NOW-5DAY]", "label": "5 days ago", "order": 0}
      ],
      "facetFields": [],
    "outputType": "timeGraph",
    "outputLabel": "documents created in the last week"
    }
```
For a time report you have to use facetqueries to define a temporal window and set an order. Order will be used to sort the output. This query will generate one dataset
![image](images/timeGraph-simplequery-preview.png)

2. a facet **grouped** search, for example: documents created in the last week by creator.
```JSON
 {
      "query": "* AND TYPE:'cm:content' AND -creator:System",
      "facetQueries":[
        {"query": "created:[NOW-1DAY TO NOW]", "label": "today", "order": 5},
        {"query": "created:[NOW-2DAY TO NOW-1DAY]", "label": "yesterday" , "order": 4},
        {"query": "created:[NOW-3DAY TO NOW-2DAY]", "label": "2 days ago" , "order": 3},
        {"query": "created:[NOW-4DAY TO NOW-3DAY]", "label": "3 days ago" , "order": 2},
        {"query": "created:[NOW-5DAY TO NOW-4DAY]", "label": "4 days ago", "order": 1},
        {"query": "created:[NOW-6DAY TO NOW-5DAY]", "label": "5 days ago", "order": 0}
      ],
      "facetFields": ["creator"],
      "outputType": "timeGraph",
      "outputLabel": "documents created in the last week by creator"
    }
```
With the inclusion of facet field the generator will launch a first query, it will obtain the facet buckets and for each facet bucket will launch a query with that bucked as a filter. **this can be expensive if the facet field has too much entries, use it with care**
![image](images/timeGraph-facetfieldquery-preview.png)

3. a facet **limited search**,for example: document creation in the sites swdsp,use-case-example and testSite.
```JSON
 {
      "query": "* AND TYPE:'cm:content' AND -creator:System",
      "facetQueries":[
        {"query": "created:[NOW-1DAY TO NOW]", "label": "today", "order": 5},
        {"query": "created:[NOW-2DAY TO NOW-1DAY]", "label": "yesterday" , "order": 4},
        {"query": "created:[NOW-3DAY TO NOW-2DAY]", "label": "2 days ago" , "order": 3},
        {"query": "created:[NOW-4DAY TO NOW-3DAY]", "label": "3 days ago" , "order": 2},
        {"query": "created:[NOW-5DAY TO NOW-4DAY]", "label": "4 days ago", "order": 1},
        {"query": "created:[NOW-6DAY TO NOW-5DAY]", "label": "5 days ago", "order": 0}
      ],
      "filterQueries": [
        {"query":"SITE:swsdp", "label": "swsdp"},
        {"query": "SITE:testsite", "label":  "Test site"},
        {"query": "SITE:monitoring-use-case", "label":  "Monitoring use case"}],
      "outputType": "timeGraph",
      "outputLabel": "documents created by site"
    }
```
In this case instead of a facetField you provide a limited set of filter queries. This is useful if you only want to avoid the multiple datasets of the facet search and avoid overwhelming as you know exactly how many queries will be launched (1 per filter).
![image](images/timeGraph-filteredquery-preview.png)


## Graph visualization
Frontend for stat display is a simple Alfresco Content App. Graph visualization is done with [charts.js](https://www.chartjs.org/). You can see the display logic and styles in [stats-dashboard.component](alfresco-content-app/stats-dashboard/src/app/stats-dashboard). By default charts use a random palette obtained from [colors.ts](alfresco-content-app/stats-dashboard/src/app/stats-dashboard/colors.ts)

## Using
For use compile the jar file and add it in your /modules/jar folder. Do the same for share.jar so you can add the type for stat configs. You can add the frontend just by adding chart.js dependency in your package.json and copying the stats-dashboard component.

## Extending
THe easiest way to extend stat generation is to set your custom type of config, for example `doughnutGraph` (should be similar to numberGraph but with multiple datasets). Add the type to [StatTypes class](alfresco-sdk/stats-dashboard/stats-dashboard-platform/src/main/java/model/StatsTypes.java). Then in [getResultsForQuery](alfresco-sdk/stats-dashboard/stats-dashboard-platform/src/main/java/services/StatGeneratorHelper.java) add a else if statement for your type and handle the search. Modify [StatQueryResult](alfresco-sdk/stats-dashboard/stats-dashboard-platform/src/main/java/model/StatsQueryResult.java) to include the results (for example a List of resultSets doughnutResults) and handle the json output in its getResultsByType method. Then, in the frontend, add a case for that type and draw the graph. 


Visit us at https://venzia.es and https://aqua.venzia.es or contact directly by email info@venzia.es