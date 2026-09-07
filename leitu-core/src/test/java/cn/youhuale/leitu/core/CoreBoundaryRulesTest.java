package cn.youhuale.leitu.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * core 边界规则（守护面）：每次构建自动执行，违规即构建失败。
 * 规则与 ADR-002 / ADR-003 对应；改动规则先改 ADR。
 */
@AnalyzeClasses(packages = "cn.youhuale.leitu.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreBoundaryRulesTest {

    @ArchTest
    static final ArchRule core_零框架依赖_只依赖JDK与自身 = noClasses()
            .that().resideInAPackage("cn.youhuale.leitu.core..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("java..", "cn.youhuale.leitu..")
            .because("core 是端口面+调度机制，零框架依赖（ADR-002/ADR-003）；中间件只准出现在 adapter 层");

    @ArchTest
    static final ArchRule internal_只被core内部访问 = classes()
            .that().resideInAPackage("cn.youhuale.leitu.core..internal..")
            .should().onlyHaveDependentClassesThat()
            .resideInAPackage("cn.youhuale.leitu.core..")
            .because("internal 只经 api 的工厂方法触达（GLOSSARY）；examples 与未来模块只准用 api/spi/model");
}
