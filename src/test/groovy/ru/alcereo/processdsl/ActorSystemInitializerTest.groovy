package ru.alcereo.processdsl

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before

import java.nio.file.Paths

abstract class ActorSystemInitializerTest extends GroovyTestCase{

    ActorSystem system

    @Before
    void setUp() throws Exception {
        def config = ConfigFactory.load("test-config")
        system = ActorSystem.create("test-config", config)

    }

    @After
    void tearDown() throws Exception {
        system.terminate()
        Paths.get("build","persistent").deleteDir() // Очистка хранилища

    }

}
