/**
 * 
 */
package org.aksw.simba.autoindex.datasource.sparql;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.aksw.simba.autoindex.es.model.DataClass;
import org.aksw.simba.autoindex.es.model.Entity;
import org.aksw.simba.autoindex.es.model.Property;
import org.aksw.simba.autoindex.request.EndPointParameters;
import org.aksw.simba.autoindex.request.Request;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SparqlHandler {
	
	private static final Logger log = LoggerFactory
	            .getLogger(SparqlHandler.class);
	private static  String commandString = "" ; //Read from properties file.
	private static  String propertiesString = "" ;
	private static  String classesString = "";
	public static  Map<String, String> prefixMap;
	private static final String TEMPLATE_FILE = "src/main/resources/application.properties";
	private Properties properties = new Properties();
	
	@PostConstruct
	private void resourceLoader() throws FileNotFoundException {
		InputStream input = null;
		try {
			 input = new FileInputStream(TEMPLATE_FILE);
			 properties.load(input);
			 commandString = properties.getProperty("entity.whereclause");
			 propertiesString = properties.getProperty("property.whereclause");
			 classesString = properties.getProperty("class.whereclause");
			 int i=1;
			 Map<String, String> prefix = new HashMap<String, String>();
			 while(properties.containsKey("prefix" + i + ".name")) {
				 prefix.put(properties.getProperty("prefix" + i + ".name") , properties.getProperty("prefix" + i + ".url"));
				 ++i;
			 }
			 prefixMap = Collections.unmodifiableMap(prefix);
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}	
		finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private String getPrefixString() {
		String output = "";
		for (Entry<String, String> entry : prefixMap.entrySet()) {
			output += "PREFIX " + entry.getKey() + ":<" + entry.getValue() + ">\n";
		}
		return output;
	}
	
	public String getPropertyQueryString() {
		return getPrefixString() + propertiesString;
	}
	
	public String getClassQueryString() {
		return getPrefixString() + classesString;
	}
	
	public String getEntityQueryString() {
		return getPrefixString() + commandString;
	}
	
	public ArrayList<DataClass> fetchClasses(Request request){
		ArrayList<DataClass> classList = null;
		EndPointParameters endPointParameters= request.getEndPointParameters();
		String baseURI = endPointParameters.getUrl(); 	
		if (baseURI.isEmpty()) {
			log.warn("base URI is empty, Cannot proceed further");
			throw new IllegalArgumentException("base URI is empty");
		}
		if (!baseURI.startsWith("http://")) {
			baseURI = "http://" + baseURI; //Appending because QueryHandler appends local path otherwise
		}
		String commandText = "";
		Map<String, String> prefixes = new HashMap<String, String>();  		
		if(endPointParameters.getIsClassCustomized()) {
			String selectQuery = endPointParameters.getClassSelectQuery();
			commandText = extractPrefixes(selectQuery , prefixes);
		}
		else {
			prefixes = prefixMap;
			commandText = classesString;
		}
		String defaultGraph = request.getDefaultGraph();
		Query query = constructSparqlQuery(baseURI , defaultGraph , 0 , commandText , prefixes);
		ResultSet output = executeQuery(baseURI , query);
		classList = generateClasses(output);
		return classList;
	}
	
	public ArrayList<DataClass> generateClasses(ResultSet result){
		ArrayList<DataClass> classList = new ArrayList<DataClass>();
		while (result.hasNext()) {
			QuerySolution qs = result.next();
			String entry1 = getResourceValue(qs , "key1");
			String entry2 = getResourceValue(qs , "key2");
			DataClass dataClass = new DataClass(entry1 , entry2);
			classList.add(dataClass);
		}
		return classList;
	}
	
	public ArrayList<Property> fetchProperties(Request request){
		ArrayList<Property> propertyList = null;
		EndPointParameters endPointParameters= request.getEndPointParameters();
		String baseURI = endPointParameters.getUrl();
		if (baseURI.isEmpty()) {
			log.warn("base URI is empty, Cannot proceed further");
			throw new IllegalArgumentException("base URI is empty");
		}
		if (!baseURI.startsWith("http://")) {
			baseURI = "http://" + baseURI; //Appending because QueryHandler appends local path otherwise
		}
		String defaultGraph = request.getDefaultGraph();
		String commandText = "";
		Map<String, String> prefixes = new HashMap<String, String>();  		
		if(endPointParameters.getIsPropertyCustomized()) {
			String selectQuery = endPointParameters.getPropertySelectQuery();
			commandText = extractPrefixes(selectQuery , prefixes);
		}
		else {
			prefixes = prefixMap;
			commandText = propertiesString;
		}
		Query query = constructSparqlQuery(baseURI , defaultGraph , 0 , commandText , prefixes);
		ResultSet output = executeQuery(baseURI , query);
		propertyList = generatePropertiesList(output);
		return propertyList;
	}
	
	public ArrayList<Property> generatePropertiesList(ResultSet result){
		ArrayList<Property> propertyList = new ArrayList<Property>();
		while (result.hasNext()) {
			QuerySolution qs = result.next();
			String entry1 = getResourceValue(qs , "key1");
			String entry2 = getResourceValue(qs , "key2");
			Property property = new Property(entry1 , entry2);
			propertyList.add(property);
		}
		return propertyList;
	}
	
    	public Query constructSparqlQuery(String baseURI , String defaultGraph , int limit , String commandString , Map<String,String> prefixes) {
    		ParameterizedSparqlString sparqlQueryHandler = new ParameterizedSparqlString();
    		sparqlQueryHandler.setBaseUri(baseURI);
    		sparqlQueryHandler.setNsPrefixes(prefixes);
    		//TODO: Find a way to handle this by ParameterizedSparql String. 
    		// Doesnt work with SetLiteral or SetParam(Node) or setIRI. Try other options
    		String commandText = commandString;
    	
    		sparqlQueryHandler.setCommandText(commandText);
    		
    		if(!defaultGraph.isEmpty()) {
    			log.warn("Overrding a new default Graph , name= " + defaultGraph);
    			sparqlQueryHandler.setIri("default-graph-uri", defaultGraph);
    		}
    		if (limit > 0) {
    			sparqlQueryHandler.append("LIMIT ");
    			sparqlQueryHandler.appendLiteral(limit);
    		}
    		Query query = QueryFactory.create(sparqlQueryHandler.asQuery());
    		log.debug("Query=" + query.toString());
    		return query;
    	}
    	
    	public ResultSet executeQuery(String baseURI , Query query) {
    		QueryExecution queryExecutionFactory = org.apache.jena.query.QueryExecutionFactory.sparqlService(baseURI , query);
    		ResultSet output = queryExecutionFactory.execSelect();
    		return output;
    	}
    	
    	public String getResourceValue(QuerySolution qs , String key) {
    		String value = "";
    		RDFNode rdfNode = qs.get(key);
			if(rdfNode.isResource() || rdfNode.isURIResource() ) {
				value = qs.getResource(key).getURI().toString();
			}
			else if(rdfNode.isLiteral())
			{
				value = qs.getLiteral(key).getString();
			}
			else {
				log.error("Unsupported type, Not going to end well");
			}

			return value;
    	}
    	
    	public ArrayList<Entity> generateOutputEntities(ResultSet result){
    		ArrayList<Entity> entity_list = new ArrayList<Entity>();
    		while (result.hasNext()) {
    			QuerySolution qs = result.next();
    			String entry1 = getResourceValue(qs , "key1");
    			String entry2 = getResourceValue(qs , "key2");
    			Entity entity = new Entity(entry1 , entry2);
    			entity_list.add(entity);
    		}
    		return entity_list;
    	}
    	
    	public String extractPrefixes(String selectQuery , Map<String,String> prefixes) {
    		String query = "";
    		String[] entities = selectQuery.split("\n");
			for(String s: entities) {	
				if(s.startsWith("PREFIX")) {
					s = s.substring(7 , s.length());
					// +2 is because it should not consider : and <  and > which are not required for our prefix map
					prefixes.put(s.substring(0 , s.indexOf(":") ), s.substring(s.indexOf(":") +2, s.length()-1));
				}
				else
				{
					query += s ;
				}
				
			}
			return query;
    	}

    public ArrayList<Entity> fetchFromSparqlEndPoint(Request request) throws UnsupportedEncodingException{
    		EndPointParameters endPointParameters= request.getEndPointParameters();
    		String baseURI = endPointParameters.getUrl();
    		if (baseURI.isEmpty()) {
    			log.warn("base URI is empty, Cannot proceed further");
    			throw new IllegalArgumentException("base URI is empty");
    		}
    		if (!baseURI.startsWith("http://")) {
    			baseURI = "http://" + baseURI; //Appending because QueryHandler appends local path otherwise
    		}
    		String defaultGraph = request.getDefaultGraph();
    		int limit = request.getlimit();
    		String commandText = null;
    		Map<String, String> prefixes = new HashMap<String, String>();  		
    		if(endPointParameters.getIsEntityCustomized()) {
    			String selectQuery = endPointParameters.getEntitySelectQuery();
    			commandText = extractPrefixes(selectQuery , prefixes);
    		}
    		else {
    			prefixes = prefixMap;
    			commandText = commandString;
    		}
    		//Read Command Text and get Name of Key1,Key2. THis is important for Custom Queries where user can enter his own choice of name
    		ArrayList<String> keyList = new ArrayList<String>();
    		String[] splitStr = commandText.split("\\s+");
    		for (int i = 0; i < splitStr.length; i++) {
    			String token = splitStr[i];
    			System.out.println("i=" + i);
    			if(i > 4) {// Query can be SELECT key1, key2 or SELECT UNIQUE/DISTINCT key1,key2
    				break;
    			}
    			else {
    				if(true ==token.contains("?")) {
    					keyList.add(token.substring(1, token.length()));
    				}
    			}
    		}
    		Query query = constructSparqlQuery(baseURI , defaultGraph , limit , commandText , prefixes);
    		ResultSet output = executeQuery(baseURI , query);
    		ArrayList<Entity> entity_list = generateOutputEntities(output); 		
		return entity_list;
    } 
}
