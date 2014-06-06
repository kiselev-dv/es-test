package test;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.json.JSONObject;

public class ESTest {
	private static final String INDEX_NAME = "test";
	private static final String TYPE_NAME = "test_type";

	private static final Node node = nodeBuilder().clusterName("TEST").node();
	
	private Client client = node.client();
	private String opts;
	
	
	public ESTest(String opts) {
		this.opts = opts;
	}

	public static void main(String[] args) {

		new ESTest("one_shard.json").run();
		new ESTest("five_shards.json").run();
		
		node.close();
	}
	
	private void run() {
		
		try {
			drop(client);
		}
		catch (Exception e) {
			//index wasn't created yet
		}
		
		System.out.println("TEST " + opts);
		createIndex(client);
		
		System.out.println("Populate");
		populate(client);
		
		try {
			int test1 = testPage(client);
			System.out.println("RESULT: expect 5, got " + test1);
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
		
	}

	private void drop(Client client) {
		new DeleteIndexRequestBuilder(client.admin().indices(), INDEX_NAME).get();
	}

	private int testPage(Client client) throws InterruptedException {
		
		SearchRequestBuilder searchQ =
				client.prepareSearch(INDEX_NAME).setTypes(TYPE_NAME)
				.setSearchType(SearchType.QUERY_AND_FETCH)
				.setNoFields()
				.setQuery(QueryBuilders.matchAllQuery());
		
		searchQ.setSize(5);
		searchQ.setFrom(0);
		
		System.out.println("querry");
		System.out.println(searchQ);
		
		//Without this sleep I've got 0 hits.
		//Maybe my index is too small
		Thread.sleep(1000);
		
		SearchResponse resp = searchQ.execute().actionGet();
		
		System.out.println("response");
		System.out.println(resp.toString());
		
		return resp.getHits().hits().length;
		
	}

	private void populate(Client client) {
		
		BulkRequestBuilder bulk = client.prepareBulk();
		
		for(int i = 0; i < 100; i++ ) {
			JSONObject obj = new JSONObject();
			obj.put("id", "id-" + i);
			obj.put("test", "test string " + i);
			obj.put("field2", i);
			
			IndexRequestBuilder ind = new IndexRequestBuilder(client)
				.setSource(obj.toString()).setIndex(INDEX_NAME).setType(TYPE_NAME);
			
			bulk.add(ind.request());
		}
		
		BulkResponse bulkResponse = bulk.get();
		if(bulkResponse.hasFailures()) {
			throw new RuntimeException(bulkResponse.buildFailureMessage());
		}
	}

	private void createIndex(Client client) {

		
		AdminClient admin = client.admin();

		JSONObject settings;
		try {
			settings = new JSONObject(IOUtils.toString(ESTest.class.getResourceAsStream(
					"/" + opts)));
		} catch (IOException e) {
			throw new RuntimeException("couldn't read index settings", e);
		}

		JSONObject indexSettings = settings.getJSONObject("settings");
		
		CreateIndexRequestBuilder request = admin.indices().prepareCreate(INDEX_NAME)
			.setSettings(indexSettings.toString())
			.addMapping(TYPE_NAME, settings.getJSONObject("mappings").getJSONObject(TYPE_NAME).toString());
		
		request.get();
		
		System.out.println("Create index.");
	}
}
