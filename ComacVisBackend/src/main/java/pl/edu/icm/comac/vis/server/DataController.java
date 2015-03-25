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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openrdf.OpenRDFException;
import org.openrdf.query.BindingSet;
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

/**
 *
 * @author Aleksander Nowinski <a.nowinski@icm.edu.pl>
 */
@RestController
@EnableAutoConfiguration
public class DataController {
    private static final int MAX_RESPONSE=500;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RestController.class.getName());

    @Autowired
    Repository repo;

    @RequestMapping("/data/sparql")
    Map sparql(@RequestParam("query") String query) {
        List<String> variables = new ArrayList<String>();
        List<String[]> resultArray = new ArrayList<>();

        Map<String, Object> res = new HashMap<String, Object>();
        if (query.trim().isEmpty()) {
            res.put("error", "Query is empty");
        }
        try {
            RepositoryConnection con = repo.getConnection();
            try {
                String queryString = query;//"SELECT (COUNT(*) AS ?no) { ?s ?p ?o  }";
                TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

                TupleQueryResult result = tupleQuery.evaluate();
                try {
                    variables.addAll(result.getBindingNames());
                    while (result.hasNext()&& resultArray.size()<MAX_RESPONSE) {  // iterate over the result
                        String[] arr = new String[variables.size()];
                        BindingSet bindingSet = result.next();
                        for (int i = 0; i < arr.length; i++) {
                            String var = variables.get(i);

                            String val = null;
                            if (var != null) {
                                val = bindingSet.getValue(var).stringValue();
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
        } catch (OpenRDFException e) {
            res.put("error", e.getMessage());
        }
        return res;
    }
}