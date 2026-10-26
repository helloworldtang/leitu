package com.example.fxservice;

import cn.youhuale.leitu.rules.LeituRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 下游守护：把 leitu-rules 当"宪法资产"用——验证本项目没有触碰 leitu 的 internal。
 * 用法要点：类集合要把「下游包 + leitu 包」都导入（被依赖方与依赖方都要在场）。
 * 本服务只用 core（配置 / 观测 / 上下文 / 判定），故只带 core 的 internal 规则。
 */
class DownstreamRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages(
            "cn.youhuale.leitu.core",
            "com.example.fxservice");

    @Test
    void 不触碰core的internal() {
        assertThatCode(() -> LeituRules.INTERNAL_只被core访问.check(classes)).doesNotThrowAnyException();
    }
}
