package com.kset.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.kset", importOptions = ImportOption.DoNotIncludeTests.class)
class FrameworkArchitectureTest {

    @ArchTest
    public static final ArchRule TOP_LEVEL_PACKAGES_HAVE_NO_CYCLES =
            slices().matching("com.kset.(*)..").should().beFreeOfCycles();

    @ArchTest
    public static final ArchRule SHARED_PACKAGES_DO_NOT_DEPEND_ON_STARTERS =
            noClasses().that().resideInAnyPackage("com.kset.common..", "com.kset.cloud..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.kset.agent..", "com.kset.auth..", "com.kset.cache..",
                            "com.kset.datasource..", "com.kset.dubbo..", "com.kset.gateway..",
                            "com.kset.mq..", "com.kset.nacos..", "com.kset.redis..",
                            "com.kset.schedule..", "com.kset.sentinel..", "com.kset.web..");
}
