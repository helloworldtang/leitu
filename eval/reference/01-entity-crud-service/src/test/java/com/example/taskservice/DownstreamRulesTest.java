package com.example.taskservice;

import cn.youhuale.leitu.rules.LeituRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 下游守护：把 leitu-rules 当"宪法资产"用——验证本项目没有触碰 leitu 的 internal。
 * 用法要点：类集合要把「下游包 + leitu 包」都导入（被依赖方与依赖方都要在场）。
 */
class DownstreamRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages(
            "cn.youhuale.leitu.core",
            "cn.youhuale.leitu.capability.data",
            "com.example.taskservice");

    @Test
    void 不触碰core的internal() {
        assertThatCode(() -> LeituRules.INTERNAL_只被core访问.check(classes)).doesNotThrowAnyException();
    }

    @Test
    void 不触碰data能力的internal() {
        assertThatCode(() -> LeituRules.INTERNAL_只被本能力模块访问_数据.check(classes)).doesNotThrowAnyException();
    }
}
