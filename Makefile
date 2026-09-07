.PHONY: verify test clean

# JDK 21（macOS 自动探测；CI 由 setup-java 保证；其他环境请自带 JAVA_HOME）
JAVA_HOME_21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)

# 一条命令自验：编译 + 测试 + ArchUnit 边界规则（AI 写完代码跑这个，绿灯才算完成）
verify:
	JAVA_HOME="$(JAVA_HOME_21)" mvn -q verify

test:
	JAVA_HOME="$(JAVA_HOME_21)" mvn -q test

clean:
	JAVA_HOME="$(JAVA_HOME_21)" mvn -q clean
