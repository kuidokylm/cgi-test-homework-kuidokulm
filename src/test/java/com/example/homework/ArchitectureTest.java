package com.example.homework;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.example.homework",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

  @ArchTest
  @SuppressWarnings("unused")
  static final ArchRule controllersShouldNotDependOnRepositories =
      noClasses()
          .that().resideInAPackage("..controller..")
          .should().dependOnClassesThat()
          .resideInAPackage("..repository..");

  @ArchTest
  @SuppressWarnings("unused")
  static final ArchRule repositoriesShouldNotDependOnControllersOrServices =
      noClasses()
          .that().resideInAPackage("..repository..")
          .should().dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..");

  @ArchTest
  @SuppressWarnings("unused")
  static final ArchRule modelsShouldNotDependOnApplicationLayers =
      noClasses()
          .that().resideInAPackage("..model..")
          .should().dependOnClassesThat()
          .resideInAnyPackage(
              "..controller..", "..service..", "..repository..", "..handler..", "..exception..");
}
