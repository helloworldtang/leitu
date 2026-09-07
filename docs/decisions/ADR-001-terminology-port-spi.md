# ADR-001 术语体系：Port + SPI，退役"契约"伞词

状态：已接受（2026-09-07）

## 背景

早期设计曾用"契约"统称核心接口抽象。该词在业界有三重歧义：DDD 契约（聚合不变量/上下文集成）、Design by Contract（前置/后置/不变量）、消费者驱动契约测试（Pact）。实际讨论中已造成误读——一个需要三层限定词才能说清的词，是需要退役的伞词。

业界普查同时确认：**core 是 Java 生态核心模块的压倒性惯例**（spring-core / vertx-core / grpc-core / reactor-core / camel-core / jackson-core / quarkus-core / micronaut-core / wildfly-core）；kernel 稀有且携带操作系统语义（进程/生命周期/调度）与 Jupyter kernel 歧义。WildFly 自称微内核架构却仍叫 wildfly-core——**架构叙事写在文档里，不必背在模块名上**。

## 决策

- api 面接口称 **Port**（六边形架构正典；Port↔Adapter 与 Adapter 层命名成套）
- spi 面接口沿用 **SPI**
- 核心模块称 **core**，单 artifact，域为包级结构
- "契约"限用于 DbC 语境；限用词另含 kernel、runtime（见 GLOSSARY 限用词表）
- 新术语入库三关：**唯一指称 / 业界对齐 / 不携带无法兑现的承诺**

## 后果

- 概念分三层：core 的 Port（管道必答题）/ 能力的 Port（领域能力）/ 领域接口（业务模块，使用者的 DDD 地盘，累土不定义）
- 术语表成为一切文档用词的单一依据；引文如需使用旧词，保留原词并加注
