#!/bin/bash

sudo java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -jar wpa-1.4-SNAPSHOT.jar
