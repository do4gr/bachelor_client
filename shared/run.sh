#!/bin/bash

workload="./workloads/workload_social"
clientpath="./$1/src/main/java/com/yahoo/ycsb/webservice/$1/target.classes"
logfile="${workload}_$1.log"

./bin/ycsb run $1 -P ${workload} -cp ${clientpath} 2>&1 | tee ${logfile}