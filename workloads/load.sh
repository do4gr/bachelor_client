#!/bin/bash

workload="./workloads/workload_social"
clientpath="./$1/src/main/java/com/yahoo/ycsb/webservice/$1/target.classes"
logfile="${workload}_$1_load.log"

./bin/ycsb load $1 -s -p table=user        -p recordcount=1000       -P ${workload} -cp ${clientpath}     2>&1 | tee -a ${logfile}
./bin/ycsb load $1 -s -p table=post        -p recordcount=1000       -P ${workload} -cp ${clientpath}    2>&1 | tee -a ${logfile}
./bin/ycsb load $1 -s -p table=comment     -p recordcount=1000       -P ${workload} -cp ${clientpath}    2>&1 | tee -a ${logfile}
./bin/ycsb load $1 -s -p table=like        -p recordcount=1000       -P ${workload} -cp ${clientpath}     2>&1 | tee -a ${logfile}
./bin/ycsb load $1 -s -p table=group       -p recordcount=1000       -P ${workload} -cp ${clientpath}     2>&1 | tee -a ${logfile}
./bin/ycsb load $1 -s -p table=friendship  -p recordcount=1000       -P ${workload} -cp ${clientpath}     2>&1 | tee -a ${logfile}