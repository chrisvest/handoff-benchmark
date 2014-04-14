
Exmaple output
--------------

With uninteresting stuff removed:

```
shipilev:handoffbenchmark$ uname -a
Darwin shipilev.local 13.1.0 Darwin Kernel Version 13.1.0: Thu Jan 16 19:40:37 PST 2014; root:xnu-2422.90.20~2/RELEASE_X86_64 x86_64
shipilev:handoffbenchmark$ java -version
java version "1.7.0_51"
Java(TM) SE Runtime Environment (build 1.7.0_51-b13)
Java HotSpot(TM) 64-Bit Server VM (build 24.51-b03, mixed mode)
shipilev:handoffbenchmark$ java -jar target/microbenchmarks.jar '.*HandoffBenchmark.*' -f 1 -wi 5 -i 5
...
Benchmark                                                                    Mode   Samples         Mean   Mean error    Units
h.HandoffBenchmark.AtomicHandoffBenchmark.a:put                             thrpt         5    11212.921      236.445   ops/ms
h.HandoffBenchmark.AtomicHandoffBenchmark.a:take                            thrpt         5    11212.896      236.418   ops/ms
h.HandoffBenchmark.ExchangerHandoffBenchmark.a:put                          thrpt         5      325.621      140.111   ops/ms
h.HandoffBenchmark.ExchangerHandoffBenchmark.a:take                         thrpt         5      325.609      140.100   ops/ms
h.HandoffBenchmark.JakesFixedHandoffBenchmark.a:put                         thrpt         5     9100.183      586.992   ops/ms
h.HandoffBenchmark.JakesFixedHandoffBenchmark.a:take                        thrpt         5     9100.169      586.959   ops/ms
h.HandoffBenchmark.SynchronousQueueHandoffBenchmark.a:put                   thrpt         5      270.238      101.349   ops/ms
h.HandoffBenchmark.SynchronousQueueHandoffBenchmark.a:take                  thrpt         5      270.234      101.312   ops/ms
shipilev:handoffbenchmark$
```
