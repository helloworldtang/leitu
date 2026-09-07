package cn.youhuale.leitu.rules;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * repo 级边界规则：扫描全 reactor（leitu-core + examples + 本模块）。
 * 与 leitu-core 内的 CoreBoundaryRulesTest（模块内快速反馈）互补——这里是全仓库的最终门。
 */
@AnalyzeClasses(packages = "cn.youhuale.leitu", importOptions = ImportOption.DoNotIncludeTests.class)
class RepoBoundaryRulesTest {

    @ArchTest
    static final ArchRule core零框架依赖 = LeituRules.CORE_零框架依赖;

    @ArchTest
    static final ArchRule internal只被core访问 = LeituRules.INTERNAL_只被core访问;

    @ArchTest
    static final ArchRule examples相互独立 = LeituRules.EXAMPLES_相互独立;
}
