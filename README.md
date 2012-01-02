## Launching ElasticInbox with Whirr

Follow these instructions to start a cluster on EC2 running ElasticInbox

### Install Whirr

Run the following commands from you local machine.

Set your AWS credentials as environment variables:

```
% export AWS_ACCESS_KEY_ID=...
% export AWS_SECRET_ACCESS_KEY=...
```

Download and install Whirr:

```
% curl -O http://www.apache.org/dist/incubator/whirr/whirr-0.6.0-incubating/whirr-0.6.0-incubating.tar.gz
% tar zxf whirr-0.6.0-incubating.tar.gz
% export PATH=$PATH:$(pwd)/whirr-0.6.0-incubating/bin
```

Create a password-less SSH keypair for Whirr to use:

```
% ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa_scm
```

### Install Whirr ElasticInbox Plugin

Build plugin from source:

```
% git clone git://github.com/elasticinbox/whirr-elasticinbox.git
% cd whirr-elasticinbox
% mvn clean install
```

Copy the generated JAR file to Whirr's lib directory.

### Launch an ElasticInbox Cluster

The following command will start a cluster of 3 nodes running Cassandra and 
ElasticInbox. To change the number of nodes edit elasticinbox-ec2.properties file.
You may want to change other recipe details before launching.

```
% whirr launch-cluster --config elasticinbox-ec2.properties
```

Whirr will report progress to the console as it runs. The command will exit when
the cluster is ready to be used.

### Configure the ElasticInbox cluster

Create schema by running following command on one of the nodes (currently we
cannot automate this step due to limitation in Whirr, see WHIRR-221). Replace 
$PRIVATE_IP with private IP of any Cassandra host (ifconfig eth0).

```
% $CASSANDRA_HOME/bin/cassandra-cli --host $PRIVATE_IP < /tmp/elasticinbox.cml
```

Optionally, you may want to adjust Cassandra token ranges. By default, when not 
specified, Cassandra picks up a random token, which will lead to hot spots.

### Use the cluster

Add AWS LoadBalancer with TCP/2400 for all ElasticInbox instances. Use `smtp-source` 
command to generate LMTP traffic. For instance, following command will send 100 messages
with size of 4K in 40 concurrent streams:

```
# apt-get install postfix

% time smtp-source -L -s 40 -m 100 -l 4096 -d -c -f me@elasticinbox.com \
    -t test@elasticinbox.com ElasticInbox-LB-1070648408.eu-west-1.elb.amazonaws.com:2400
```

### Shutdown the cluster

Finally, when you want to shutdown the cluster, run the following command. Note
that all data and state stored on the cluster will be lost.

```
% whirr destroy-cluster --config elasticinbox-ec2.properties
```
