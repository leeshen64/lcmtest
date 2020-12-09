OpenWRT Package Test Plugin
===
About
---
This plugin integrates Jenkins with [Demeter](https://github.com/sercomm-cloudwu/lcmdemeter) project.

![](https://github.com/sercomm-cloudwu/lcmtest/blob/main/resources/jenkins-package-test.jpg)

Features
---
* Loop install and uninstall the specific package
* Collect CPU & RAM & storage consumption records within a container
* Output raw text log to charts

Getting Start
---
1. Host your [Jenkins](https://www.jenkins.io/) server and [Demeter](https://github.com/leeshen64/lcmdemeter) server
2. Install [OpenJDK](https://openjdk.java.net/) and [Maven](https://maven.apache.org/)
3. Obtain required dependencies from [Demeter](https://github.com/sercomm-cloudwu/lcmdemeter) project and build them by Maven
```console
$ git clone https://github.com/leeshen64/lcmdemeter demeter
$ cd ./demeter/demeter_commons_id/
$ ./mvnw install -Dmvn.test.skip=true
$ cd ../demeter_commons_umei/
$ ./mvnw install -Dmvn.test.skip=true
$ cd ../demeter_commons_util/
$ ./mvnw install -Dmvn.test.skip=true
$ cd ../demeter_c2c_client/
$ ./mvnw install -Dmvn.test.skip=true
```
4. Build [OpenWRT Package Test Plugin](#openwrt-package-test-plugin) by Maven
```console
$ ./mvnw verify -Dmvn.test.skip=true
```
5. Upload the output HPL file (target/jenkins-openwrt-plugin.hpi) to Jenkins by its GUI
![](https://github.com/sercomm-cloudwu/lcmtest/blob/main/resources/jenkins-package-test-upload.jpg)

Making Changes
---
Verify your changes by the following command line script then browsing `http://localhost:8080/jenkins`
```console
$ ./run_debug.sh
```