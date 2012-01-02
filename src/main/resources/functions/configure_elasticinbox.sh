#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
function configure_elasticinbox() {

  . /etc/profile

  PRIVATE_IP=`/sbin/ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1}'`

  cd $EI_HOME
  cp /tmp/elasticinbox.yaml config/elasticinbox.yaml

  # need to wait for Cassandra service
  # ordering not supported currently? https://issues.apache.org/jira/browse/WHIRR-221
  #$CASSANDRA_HOME/bin/cassandra-cli --host $PRIVATE_IP < /tmp/elasticinbox.cml 
}
