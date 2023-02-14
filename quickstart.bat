cd rocketmq-all-5.0.0-source-release/distribution/target/rocketmq-5.0.0/rocketmq-5.0.0
start bin\mqnamesrv.cmd
set NAMESRV_ADDR=localhost:9876
rem start bin\mqbroker.cmd --enable-proxy
start bin\mqbroker.cmd
start bin\mqproxy.cmd
