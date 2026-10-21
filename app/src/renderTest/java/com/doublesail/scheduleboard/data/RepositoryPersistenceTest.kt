package com.doublesail.scheduleboard.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.doublesail.scheduleboard.schedule.IcsScheduleRepository
import com.doublesail.scheduleboard.tasks.LocalTaskRepository
import java.io.ByteArrayInputStream
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class RepositoryPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun repeatedImportsDoNotDuplicateAndInvalidImportKeepsOldData() = runBlocking {
        val repository = IcsScheduleRepository(context)
        val text = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:stable\nDTSTART:20261021T080000Z\n" +
            "DTEND:20261021T090000Z\nSUMMARY:植物学\nEND:VEVENT\nEND:VCALENDAR\n"
        repeat(2) { repository.import(ByteArrayInputStream(text.toByteArray()), "fixture.ics", ZoneId.of("Asia/Shanghai")) }
        assertEquals(1, repository.snapshot.first().events.size)
        try {
            repository.import(ByteArrayInputStream("not a calendar".toByteArray()), "bad.ics", ZoneId.of("Asia/Shanghai"))
            fail("Invalid calendar was accepted")
        } catch (_: IllegalArgumentException) { }
        val reopened = IcsScheduleRepository(context).snapshot.first()
        assertEquals("fixture.ics", reopened.sourceName)
        assertEquals("植物学", reopened.events.single().title)
    }
    @Test fun tasksPersistCompletionAndClearOnlyCompleted() = runBlocking {
        val repository = LocalTaskRepository(context)
        repository.add("高数作业")
        repository.add("背单词")
        val id = repository.snapshot.first().tasks.first().id
        repository.setCompleted(id, true)
        val reopened = LocalTaskRepository(context)
        assertTrue(reopened.snapshot.first().tasks.first().completed)
        reopened.clearCompleted()
        assertEquals(listOf("背单词"), repository.snapshot.first().tasks.map { it.title })
    }
}
