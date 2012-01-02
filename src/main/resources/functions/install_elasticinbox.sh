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
function install_elasticinbox() {

  C_TAR_URL=${1:-https://github.com/downloads/elasticinbox/elasticinbox/elasticinbox-0.2.0-SNAPSHOT-bin.tar.gz}

  c_tar_file=`basename $C_TAR_URL`
  c_tar_dir=`echo $c_tar_file | awk -F '-bin' '{print $1}'`

  EI_HOME=/usr/local/$c_tar_dir
  C_CONF_DIR=/usr/local/$c_tar_dir/conf
  C_LOG_DIR=/var/log/elasticinbox

  echo "export EI_HOME=$EI_HOME" >> /etc/profile

  install_tarball $C_TAR_URL
  
  mkdir -p $C_LOG_DIR

  cat >/etc/init.d/elasticinbox <<END_OF_FILE
#!/bin/bash

PROG="$EI_HOME/bin/elasticinbox"
PROGNAME="ElasticInbox"

LOGFILE=/var/log/elasticinbox/console.log
PIDFILE=/var/run/elasticinbox.pid

running(){
    PID=\`cat \$PIDFILE 2>/dev/null\`

    # Check that the pid is sane.
    if [ "x\$PID" == "x" ] ; then
        false
    else
        # Check that the process is alive.
        ps \$PID >/dev/null 2>&1
    fi
}

start(){
    echo -n $"Starting \$PROGNAME: "

    # Try to start the program.
    if running; then
        echo "Failed.  Maybe remove \$PIDFILE?"
        false
    else
        mkdir -p \`dirname \$LOGFILE\`
        \$PROG > \$LOGFILE 2>&1 &
        PID=\$!
        mkdir -p \`dirname \$PIDFILE\`
        echo \$PID > \$PIDFILE

        echo "Success."
        true
    fi
}

stop(){
    echo -n $"Stopping \$PROGNAME: "

    # Check if it's already stopped.
    if ! running ; then
        echo "Failed.  Already stopped."
        false
    else
         # Find the PID and kill it.
        PID=\`cat \$PIDFILE 2>/dev/null\`
        if [ "x\$PID" == "x" ] ; then
            echo "Failed."
            false
        else
            # (Try five times to kill it).
            for i in \`seq 0 5\`; do
                kill \$PID
                sleep 1
                if ! running ; then
                    break
                fi
            done

            # Check if it is finished.
            if running ; then
                echo "Failed."
                false
            else
                # Clear out the pidfile.
                echo "Success."
                rm -f \$PIDFILE
                true
            fi
        fi
    fi
}

restart(){
    stop
    start
}

status(){
    echo -n $"Status of \$PROGNAME: "

    if running ; then
        echo "Running."
        true
    else
        echo "Not running."
        false
    fi
}

# See how we were called.
case "\$1" in
    start)
 start
 RETVAL=\$?
 ;;
    stop)
 stop
 RETVAL=\$?
 ;;
    status)
 status
 RETVAL=\$?
 ;;
    restart)
 restart
 RETVAL=\$?
 ;;
    *)
 echo $"Usage: \$0 {start|stop|status|restart}"
 RETVAL=2
esac

exit
END_OF_FILE

  chmod +x /etc/init.d/elasticinbox
  install_service elasticinbox

}

