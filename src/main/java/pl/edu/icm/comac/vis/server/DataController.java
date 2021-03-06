/*
 * Copyright 2014 Pivotal Software, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.edu.icm.comac.vis.server;

import pl.edu.icm.comac.vis.server.service.SearchResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.BindingSet;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.icm.comac.vis.server.model.Graph;
import pl.edu.icm.comac.vis.server.model.Link;
import pl.edu.icm.comac.vis.server.service.DetailsService;
import pl.edu.icm.comac.vis.server.service.GraphIdService;
import pl.edu.icm.comac.vis.server.service.GraphService;
import pl.edu.icm.comac.vis.server.service.NodeTypeService;
import pl.edu.icm.comac.vis.server.service.SearchService;
import pl.edu.icm.comac.vis.server.service.UnknownGraphException;
import pl.edu.icm.comac.vis.server.service.UnknownNodeException;

/**
 *
 * @author Aleksander Nowinski <a.nowinski@icm.edu.pl>
 */
@RestController
@EnableAutoConfiguration
public class DataController {

    protected static final String JSON_FAVOURITE = "favourite";
    protected static final String JSON_IMPORTANCE = "importance";
    private static final int MAX_RESPONSE = 500;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(DataController.class);

    @Autowired
    GraphService graphService;

    @Autowired
    Repository repo;

    @Autowired
    GraphIdService graphIdService;

    @Autowired
    NodeTypeService nodeTypeService;

    @Autowired
    SearchService searchService;
    
    @Autowired
    DetailsService detailsService;

    private static final int MAX_SEARCH_RESULTS = 500;

    @RequestMapping("/data/graph")
    Graph graph(@RequestParam String query) {
        try {
            String[] idArray = query.split("\\|");
            //need cleanup:
            idArray = (String[]) new TreeSet(Arrays.asList(idArray)).toArray(new String[0]);
            String graphId = graphIdService.getGraphId(Arrays.asList(idArray));
            Graph res = graphService.constructGraphs(idArray);
            res.setGraphId(graphId);
            return res;
        } catch (OpenRDFException e) {
            log.error("query failed", e);
            return new Graph();
        }
    }

    @RequestMapping("/data/graphById")
    Graph graphById(@RequestParam String query) throws UnknownGraphException {
        try {
            List<String> nodes = graphIdService.getNodes(query);
            Graph res = graphService.constructGraphs(nodes.toArray(new String[nodes.size()]));
            res.setGraphId(query);
            return res;
//            Map<String, Object> graph = personPublicationGraph(nodes.toArray(new String[nodes.size()]));
//            return graph;
        } catch (OpenRDFException e) {
            log.error("query failed", e);
            return new Graph();
        }
    }


    @RequestMapping("/data/sparql_construct")
    Map<String, Object> sparqlConstruct(@RequestParam("query") String query) {
        log.debug("Invoking construct query: {}", query);
//        List<String> variables = new ArrayList<String>();
        List<String[]> resultArray = new ArrayList<>();

        Map<String, Object> res = new HashMap<String, Object>();
        if (query.trim().isEmpty()) {
            res.put("error", "Query is empty");
        }
        try {
            RepositoryConnection con = repo.getConnection();
            try {
                GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL, query);
                GraphQueryResult qres = graphQuery.evaluate();
                try {
                    while (qres.hasNext() && resultArray.size() < MAX_RESPONSE) {  // iterate over the result
                        Statement st = qres.next();
                        resultArray.add(new String[]{st.getSubject().stringValue(), st.getPredicate().stringValue(), st.getObject().stringValue()});
                    }
                } finally {
                    qres.close();
                }
            } finally {
                con.close();
            }
            res.put("header", new String[]{"Subject", "Predicate", "Object"});
            res.put("values", resultArray);
            log.debug("Finished query got {} results", resultArray.size());
        } catch (OpenRDFException e) {
            res.put("error", e.getMessage());
            log.debug("Exception parsing query: {}", e.getMessage());
        }

        return res;
    }

    @RequestMapping("/data/sparql_select")
    Map sparql(@RequestParam("query") String query) {
        log.debug("Invoking tuple query: {}", query);
        List<String> variables = new ArrayList<String>();
        List<String[]> resultArray = new ArrayList<>();

        Map<String, Object> res = new HashMap<String, Object>();
        if (query.trim().isEmpty()) {
            res.put("error", "Query is empty");
        }
        try {
            RepositoryConnection con = repo.getConnection();
            String queryString = query;//"SELECT (COUNT(*) AS ?no) { ?s ?p ?o  }";
            //now add predefined connections:
            if (!query.trim().toUpperCase().startsWith("PREFIX")) {
                StringBuilder b = new StringBuilder();
                for (String[] ns : RDFConstants.PREDEFINED_NAMESPACES) {
                    b.append("PREFIX " + ns[0] + ": <" + ns[1] + "> ");
                }
                queryString = b.toString() + " " + query;
            }
            try {
                TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
                TupleQueryResult result = tupleQuery.evaluate();
                try {
                    variables.addAll(result.getBindingNames());
                    while (result.hasNext() && resultArray.size() < MAX_RESPONSE) {  // iterate over the result
                        String[] arr = new String[variables.size()];
                        BindingSet bindingSet = result.next();
                        for (int i = 0; i < arr.length; i++) {
                            String var = variables.get(i);

                            String val = null;
                            if (var != null) {
                                final Value v = bindingSet.getValue(var);
                                if (v != null) {
                                    val = v.stringValue();
                                } else {
                                    val = null;
                                }
                            }
                            log.debug("Result var {}={}, table size={}", var, val, resultArray.size());
                            arr[i] = val;
                        }
                        resultArray.add(arr);
                    }
                } finally {
                    result.close();
                }
            } finally {
                con.close();
            }
            res.put("header", variables);
            res.put("values", resultArray);
            log.debug("Finished query got {} results", resultArray.size());
        } catch (Exception e) {
            res.put("error", e.getMessage());
            log.debug("Exception parsing query: {}", e);
        }
        return res;
    }

    /*
     PREFIX foaf: <http://xmlns.com/foaf/0.1/> PREFIX dc: <http://purl.org/dc/elements/1.1/>  select ?fav ?favname ?type where { { ?fav foaf:name ?favname } UNION { ?fav dc:title ?favname } . filter(CONTAINS(lcase(?favname), "nowiń")). ?fav <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?type} order by ?favname
    
     */
    @RequestMapping("/data/search")
    Map search(@RequestParam("query") String query) {
        log.debug("Got a search query: {}", query);
        Map<String, Object> res = new HashMap<String, Object>();
        Map<String, Object> response = new HashMap<String, Object>();

        query = query.toLowerCase();

        try {

            List<SearchResult> searchResultList = searchService.search(query, MAX_RESPONSE + 1);

            boolean hasMore = searchResultList.size() > MAX_SEARCH_RESULTS;
            if (hasMore) {
                searchResultList = searchResultList.subList(0, MAX_SEARCH_RESULTS);
            }
            response.put("docs", searchResultList);
            response.put("hasMoreResults", hasMore);
            res.put("response", response);
        } catch (Exception e) {
            res.put("error", e.getMessage());
        }
        return res;
    }

    /** Controller method to serve detailed info about the object with given id.
     * 
     * @param id identifier of the object to check info for.
     * @return 
     */
    @RequestMapping("/data/details")
    Map objectDetails(@RequestParam("query") String id) throws UnknownNodeException, OpenRDFException {
        log.debug("Got id request for object {}", id);
        
        Map<String, Object> res = detailsService.getObjectInfo(id);
        return res;
    }

    private Map<String, Double> calculateImportance(Map<URI, Map<String, Object>> nodes, Set<Link> links) {
        Set<String> others = nodes.entrySet().stream()
                .filter(e -> !e.getValue().containsKey(JSON_FAVOURITE))
                .map(e -> e.getKey().stringValue())
                .collect(Collectors.toSet());
        Set<String> favs = nodes.entrySet().stream()
                .filter(e -> e.getValue().containsKey(JSON_FAVOURITE))
                .map(e -> e.getKey().stringValue())
                .collect(Collectors.toSet());
        Map<String, int[]> linkCount = new HashMap<>(); //0-fav, 1-other
        for (Link link : links) {
            int idx = favs.contains(link.getTargetId()) ? 0 : 1;
            linkCount.computeIfAbsent(link.getSourceId(), x -> new int[2])[idx]++;
            idx = favs.contains(link.getSourceId()) ? 0 : 1;
            linkCount.computeIfAbsent(link.getTargetId(), x -> new int[2])[idx]++;
        }
        //now recalculate importance:
        Map<String, Double> res = new HashMap<>();
        //favs: 
        for (String f : favs) {
            int[] lc = linkCount.computeIfAbsent(f, x -> new int[2]);
            int l = lc[0] + lc[1] - 2;
            if (l < 1) {
                l = 1;
            }
            res.put(f, 1. + 0.7 * (1. - 1. / l));
        }
        for (String other : others) {
            int[] lc = linkCount.computeIfAbsent(other, x -> new int[2]);
            int l = 2 * lc[0] + lc[1] - 1;
            if (l < 1) {
                l = 1;
            }
            res.put(other, 0.7 + 0.7 * (1. - 1. / l));
        }
        return res;
    }

    private void applyImportance(Map<URI, Map<String, Object>> nodes, Map<String, Double> importance) {
        Map<String, Map<String, Object>> snodes = nodes.entrySet().stream().
                collect(Collectors.toMap(
                        e -> e.getKey().stringValue(), e -> e.getValue()
                ));//necessary to change key type.
        for (Map.Entry<String, Double> entry : importance.entrySet()) {
            Map<String, Object> node = snodes.get(entry.getKey());
            node.put(JSON_IMPORTANCE, "" + entry.getValue());
        }
    }

}
