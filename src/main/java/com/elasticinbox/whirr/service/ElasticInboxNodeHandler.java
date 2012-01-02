/**
 * Licensed to the Optimax Software Ltd. (Optimax) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Optimax licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.elasticinbox.whirr.service;

import static org.apache.whirr.RolePredicates.role;
import static org.apache.whirr.service.FirewallManager.Rule;
import static org.jclouds.scriptbuilder.domain.Statements.call;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.whirr.Cluster;
import org.apache.whirr.Cluster.Instance;
import org.apache.whirr.service.ClusterActionEvent;
import org.apache.whirr.service.ClusterActionHandlerSupport;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;

public class ElasticInboxNodeHandler extends ClusterActionHandlerSupport
{
	public static final String CASSANDRA_ROLE = "cassandra";
	public static final String ROLE = "elasticinbox";
	public static final int LMTP_PORT = 2400;
	public static final int REST_PORT = 8181;
	
	private static final String CONF_S3_ENDPOINT = "elasticinbox.aws.s3.endpoint";
	private static final String CONF_S3_CONTAINER = "elasticinbox.aws.s3.container";
	private static final String CONF_S3_IDENTITY = "elasticinbox.aws.s3.identity";
	private static final String CONF_S3_CREDENTIAL = "elasticinbox.aws.s3.credential";
	private static final String CONF_CASSANDRA_RF = "elasticinbox.cassandra.replication_factor";
	public static final String CONF_BIN_TARBALL = "elasticinbox.tarball.url";

	@Override
	public String getRole() {
		return ROLE;
	}

	@Override
	protected void beforeBootstrap(ClusterActionEvent event) throws IOException 
	{
		addStatement(event, call("install_java"));
		addStatement(event, call("install_tarball"));

		addStatement(event, call("install_service"));
		addStatement(event, call("remove_service"));

		Configuration config = event.getClusterSpec().getConfiguration();

		String tarball = prepareRemoteFileUrl(event, config.getString(CONF_BIN_TARBALL, null));

		if (tarball != null) {
			addStatement(event, call("install_elasticinbox", tarball));
		} else {
			addStatement(event, call("install_elasticinbox"));
		}
	}

	@Override
	protected void beforeConfigure(ClusterActionEvent event)
			throws IOException, InterruptedException
	{
		Cluster cluster = event.getCluster();
		Configuration config = event.getClusterSpec().getConfiguration();

		event.getFirewallManager().addRule(
				Rule.create()
						.destination(cluster.getInstancesMatching(role(ROLE)))
						.ports(LMTP_PORT, REST_PORT));

		addStatement(event, buildConfig("/tmp/elasticinbox.yaml", cluster, config));
		addStatement(event, buildSchema("/tmp/elasticinbox.cml", cluster, config));
		addStatement(event, call("configure_elasticinbox"));
		addStatement(event, call("start_elasticinbox"));
	}

	private Statement buildSchema(String path, Cluster cluster, Configuration config)
	{
		List<String> lines = Lists.newArrayList();
		lines.add("create keyspace ElasticInbox");
		if (config.containsKey(CONF_CASSANDRA_RF)) {
			lines.add("with placement_strategy = 'org.apache.cassandra.locator.SimpleStrategy'");
			lines.add("and strategy_options = [{replication_factor:" + config.getString(CONF_CASSANDRA_RF) + "}]");
		}
		lines.add(";");
		lines.add("use ElasticInbox;");
		lines.add("create column family Accounts with ");
		lines.add("key_validation_class=UTF8Type and");
		lines.add("rows_cached=100000;");
		lines.add("create column family MessageMetadata with ");
		lines.add("column_type=Super and ");
		lines.add("key_validation_class=UTF8Type and");
		lines.add("comparator=TimeUUIDType and ");
		lines.add("subcomparator=BytesType;");
		lines.add("create column family IndexLabels with");
		lines.add("key_validation_class=UTF8Type and");
		lines.add("comparator=TimeUUIDType and ");
		lines.add("rows_cached=100000;");
		lines.add("create column family Counters with");
		lines.add("column_type=Super and");
		lines.add("default_validation_class=CounterColumnType and ");
		lines.add("replicate_on_write=true and");
		lines.add("key_validation_class=UTF8Type and");
		lines.add("comparator=UTF8Type and");
		lines.add("subcomparator=AsciiType;");
		
		Statement script = Statements.appendFile(path, lines);
		return script;
	}

	private Statement buildConfig(String path, Cluster cluster, Configuration config)
	{
		List<Instance> instances = Lists.newArrayList(cluster.getInstancesMatching(role(CASSANDRA_ROLE)));

		List<String> lines = Lists.newArrayList();
		lines.add("mailbox_quota_bytes: 1073741824");
		lines.add("mailbox_quota_count: 50000");
		lines.add("enable_performance_counters: false");
		lines.add("performance_counters_interval: 180");
		lines.add("lmtp_port: " + LMTP_PORT);
		lines.add("lmtp_max_connections: 50");
		lines.add("metadata_storage_driver: cassandra");
		lines.add("store_html_message: true");
		lines.add("store_plain_message: false");
		lines.add("cassandra_autodiscovery: true");
		lines.add("cassandra_cluster_name: 'Test Cluster'");
		lines.add("cassandra_keyspace: 'ElasticInbox'");
		lines.add("cassandra_hosts:");
		for (String instance : getPrivateIps(instances)) {
			lines.add("  - " + instance + ":9160");
		}
		lines.add("");
		lines.add("blobstore_write_profile: aws-demo");
		lines.add("blobstore_profiles:");
		lines.add("  aws-demo:");
		lines.add("    provider: aws-s3");
		if(config.containsKey(CONF_S3_ENDPOINT))
			lines.add("    endpoint: " + config.getString(CONF_S3_ENDPOINT));
		if(config.containsKey(CONF_S3_CONTAINER))
			lines.add("    container: " + config.getString(CONF_S3_CONTAINER));
		if(config.containsKey(CONF_S3_IDENTITY))
			lines.add("    identity: " + config.getString(CONF_S3_IDENTITY));
		if(config.containsKey(CONF_S3_CREDENTIAL))
			lines.add("    credential: " + config.getString(CONF_S3_CREDENTIAL));

		Statement script = Statements.appendFile(path, lines);
		return script;
	}

	private List<String> getPrivateIps(List<Instance> instances)
	{
		return Lists.transform(Lists.newArrayList(instances),
				new Function<Instance, String>() {
					@Override
					public String apply(Instance instance) {
						return instance.getPrivateIp();
					}
				});
	}
}
