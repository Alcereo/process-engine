package ru.alcereo.processdsl.task

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith
/**
 * Created by alcereo on 12.01.18.
 */
@RunWith(Cucumber)
@CucumberOptions(
        features = "classpath:./"
)
class PersistCucumberTest{
}


