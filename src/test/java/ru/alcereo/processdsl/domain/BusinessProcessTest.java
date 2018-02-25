package ru.alcereo.processdsl.domain;

import lombok.val;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BusinessProcessTest {

    @Test
    public void getNextTaskTest(){

        val process = new BusinessProcess(UUID.randomUUID());

        val task1ID = UUID.randomUUID();
        val task1 = mock(Task.class);
        when(task1.getIdentifier()).thenReturn(task1ID);

        val task2ID = UUID.randomUUID();
        val task2 = mock(Task.class);
        when(task2.getIdentifier()).thenReturn(task2ID);

        val task3ID = UUID.randomUUID();
        val task3 = mock(Task.class);
        when(task3.getIdentifier()).thenReturn(task3ID);

        process.addLastTask(task1);
        process.addLastTask(task2);
        process.addLastTask(task3);

        process.getRaisedBusinessEvents().forEach(process::handleEvent);

        Optional<Task> nextTaskOpt = process.getNextTaskAfter(task1ID);
        assertTrue("Не удалось получить следующую таску",
                nextTaskOpt.isPresent()
        );
        assertEquals(
                "Полученная таска не совпадает со следующей ожидаемой",
                task2,
                nextTaskOpt.get()
        );


        nextTaskOpt = process.getNextTaskAfter(task2ID);
        assertTrue("Не удалось получить следующую таску",
                nextTaskOpt.isPresent()
        );
        assertEquals(
                "Полученная таска не совпадает со следующей ожидаемой",
                task3,
                nextTaskOpt.get()
        );

        nextTaskOpt = process.getNextTaskAfter(task3ID);
        assertTrue("Не удалось получить следующую таску",
                !nextTaskOpt.isPresent()
        );
    }

    @Test
    public void isLastTaskTest(){

        val process = new BusinessProcess(UUID.randomUUID());

        val task1ID = UUID.randomUUID();
        val task1 = mock(Task.class);
        when(task1.getIdentifier()).thenReturn(task1ID);

        val task2ID = UUID.randomUUID();
        val task2 = mock(Task.class);
        when(task2.getIdentifier()).thenReturn(task2ID);

        val task3ID = UUID.randomUUID();
        val task3 = mock(Task.class);
        when(task3.getIdentifier()).thenReturn(task3ID);

        process.addLastTask(task1);
        process.addLastTask(task2);
        process.addLastTask(task3);

        process.getRaisedBusinessEvents().forEach(process::handleEvent);

        assertFalse(process.isLastTask(task1ID));
        assertFalse(process.isLastTask(task2ID));
        assertTrue(process.isLastTask(task3ID));
    }

}