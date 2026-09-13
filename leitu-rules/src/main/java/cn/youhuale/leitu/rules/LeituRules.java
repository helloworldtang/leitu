package cn.youhuale.leitu.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 累土的架构边界规则集——可发布的"宪法资产"。
 *
 * <p>两种消费方式：
 * <ul>
 *   <li>本仓库：leitu-rules 的测试引这些规则扫描全 reactor（core + examples + 未来模块）；</li>
 *   <li>下游项目：依赖 {@code cn.youhuale:leitu-rules}，在自己测试里
 *       {@code @ArchTest static final ArchRule r = LeituRules.CORE_零框架依赖;} 守自己的架构。</li>
 * </ul>
 *
 * <p>改规则=改宪法：先改对应 ADR，再改这里（变更权责矩阵：rules 改语义必须人+ADR）。
 */
public final class LeituRules {

    private LeituRules() {
    }

    /** core 是端口面+调度机制，零框架依赖；中间件只准出现在 adapter 层。依据 ADR-002 / ADR-003。 */
    public static final ArchRule CORE_零框架依赖 = noClasses()
            .that().resideInAPackage("cn.youhuale.leitu.core..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("java..", "cn.youhuale.leitu..")
            .because("core 是端口面+调度机制，零框架依赖（ADR-002/ADR-003）；中间件只准出现在 adapter 层");

    /** internal 只经 api 的工厂方法触达；examples、下游与未来模块只准用 api/spi/model。依据 GLOSSARY：工厂方法隔离 internal。 */
    public static final ArchRule INTERNAL_只被core访问 = classes()
            .that().resideInAPackage("cn.youhuale.leitu.core..internal..")
            .should().onlyHaveDependentClassesThat()
            .resideInAPackage("cn.youhuale.leitu.core..")
            .because("internal 只经 api 的工厂方法触达（GLOSSARY）；examples 与外部只准用 api/spi/model");

    /** capability 的 internal 同理：只被本能力模块访问。新能力模块落地时在此扩展同款规则（见下方 data 同款）。 */
    public static final ArchRule INTERNAL_只被本能力模块访问 = capabilityInternal只被本模块访问("capability.access");

    /** data 能力同款（ADR-010 落地时扩展；两条独立规则不可合并——合并会让模块间互访 internal）。 */
    public static final ArchRule INTERNAL_只被本能力模块访问_数据 = capabilityInternal只被本模块访问("capability.data");

    private static ArchRule capabilityInternal只被本模块访问(String modulePath) {
        return classes()
                .that().resideInAPackage("cn.youhuale.leitu." + modulePath + "..internal..")
                .should().onlyHaveDependentClassesThat()
                .resideInAPackage("cn.youhuale.leitu." + modulePath + "..")
                .because("能力模块（" + modulePath + "）的 internal 同样只经 api 工厂方法触达（GLOSSARY）；外部只准用 api/spi/model");
    }

    /** 各金样本相互独立：examples 是平行的示范，彼此不依赖（同一样本内部的依赖不受限）。 */
    public static final ArchRule EXAMPLES_相互独立 =
            com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                    .matching("cn.youhuale.leitu.examples.(*)..")
                    .should().notDependOnEachOther()
                    .because("金样本之间不互相依赖——每个样本独立成篇，像目录里的平行条目");
}
